package com.whirlpool.app.data

import android.content.Context
import android.os.Environment
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.whirlpool.app.CrashReporter
import com.whirlpool.engine.Engine
import com.whirlpool.engine.EngineConfig
import com.whirlpool.engine.FavoriteItem
import com.whirlpool.engine.FilterSelection
import com.whirlpool.engine.SourceServer
import com.whirlpool.engine.StatusSummary
import com.whirlpool.engine.VideoItem
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONObject

private const val STARTING_SOURCE_BASE_URL = "https://getfigleaf.com"
private const val ACTIVE_SOURCE_KEY = "settings.sources.active"
private const val SOURCES_BOOTSTRAPPED_KEY = "settings.sources.bootstrapped"
private const val SETTINGS_PREFIX = "settings."
private const val DOWNLOADED_VIDEO_KEY_PREFIX = "downloads.video."
private val URL_SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
private val ALLOWED_SOURCE_SCHEMES = setOf("http", "https")
private val ALLOWED_STREAM_SCHEMES = setOf("http", "https")

data class SourceServerConfig(
    val baseUrl: String,
    val title: String,
    val color: String?,
    val iconUrl: String?,
    val isActive: Boolean,
)

class EngineRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val engineLock = Any()
    private val engines = linkedMapOf<String, Engine>()
    @Volatile
    private var activeBaseUrlCache: String? = null
    @Volatile
    private var pythonExecutableCache: String? = null

    private val databaseFile: File by lazy {
        File(appContext.filesDir, "shared/whirlpool.db")
    }

    private val curlCffiBridge: File by lazy {
        File(appContext.filesDir, "curl_cffi_fetch.py")
    }

    private val ytDlpResolver: YtDlpResolver by lazy {
        YtDlpResolver(appContext)
    }

    fun status(): StatusSummary = activeSourceEngine().syncStatus()

    fun statusForSource(baseUrl: String): StatusSummary {
        val normalized = normalizeConfiguredBaseUrl(baseUrl)
        require(normalized.isNotBlank()) { "baseUrl cannot be blank" }
        return settingsEngine().probeStatus(normalized)
    }

    fun discover(
        query: String,
        page: UInt = 1u,
        limit: UInt = 10u,
        channelId: String = "",
        filters: Map<String, Set<String>> = emptyMap(),
    ): List<VideoItem> {
        val selections = filters.entries.flatMap { entry ->
            val selectedIds = entry.value
                .map { choiceId -> choiceId.trim() }
                .filter { choiceId -> choiceId.isNotEmpty() }
                .distinct()
            if (selectedIds.isEmpty()) {
                listOf(FilterSelection(optionId = entry.key, choiceId = ""))
            } else {
                selectedIds.map { choiceId ->
                    FilterSelection(optionId = entry.key, choiceId = choiceId)
                }
            }
        }
        return activeSourceEngine().discoverVideosWithFilters(
            query = query,
            page = page,
            limit = limit,
            channelId = channelId,
            filters = selections,
        )
    }

    fun resolve(pageUrl: String, ytdlpCommand: String? = null): PlaybackResolution =
        ytDlpResolver.resolve(pageUrl, ytdlpCommand)

    fun downloadVideo(
        pageUrl: String,
        title: String?,
        channelId: String,
        videoId: String,
        ytdlpCommand: String? = null,
    ): VideoDownloadResult {
        val downloadsDir = appContext
            .getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?.let { moviesDir -> File(moviesDir, "Whirlpool") }
            ?: File(appContext.filesDir, "downloads")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val downloaded = ytDlpResolver.download(
            pageUrl = pageUrl,
            outputDir = downloadsDir,
            filenameHint = title,
            ytdlpCommand = ytdlpCommand,
        )
        val tracked = trackDownloadedVideo(
            channelId = channelId,
            videoId = videoId,
            savedPath = downloaded.savedPath,
        )
        if (!tracked) {
            throw IOException("Downloaded file saved but failed to index it for offline playback.")
        }
        return downloaded
    }

    fun downloadedVideoPath(channelId: String, videoId: String): String? {
        val key = downloadedVideoPreferenceKey(channelId, videoId) ?: return null
        val storedPath = settingsEngine()
            .getUserPreference(key)
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
            ?: return null
        val file = File(storedPath)
        if (file.exists() && file.isFile) {
            return file.absolutePath
        }

        // Best-effort cleanup of stale mappings.
        settingsEngine().setUserPreference(key, "")
        return null
    }

    fun favorites(): List<FavoriteItem> = activeEngine().listFavorites()

    fun favoriteVideos(): List<VideoItem> = activeEngine().listFavoriteVideos()

    fun addFavorite(video: VideoItem): FavoriteItem = activeEngine().addFavorite(video)

    fun removeFavorite(videoId: String): Boolean = activeEngine().removeFavorite(videoId)

    fun exportDatabase(path: String): Boolean = activeEngine().exportDatabase(path)

    fun importDatabase(path: String): Boolean = activeEngine().importDatabase(path)

    fun transferPath(): String {
        return File(appContext.filesDir, "exports/whirlpool-export.db").absolutePath
    }

    fun checkYtDlpUpdates() = activeEngine().checkYtDlpUpdate()

    fun ytDlpState(): String = ytDlpResolver.state()

    fun recentCrashSummaries(limit: Int = 5): List<String> {
        return CrashReporter.recentSummaries(appContext, limit)
            .map { summary ->
                "${summary.timestamp} | ${summary.exception} | screen=${summary.screen} | file=${summary.fileName}"
            }
    }

    fun activeApiBaseUrl(): String = resolveActiveApiBaseUrl().orEmpty()

    fun listSourceServers(): List<SourceServerConfig> {
        val settingsEngine = settingsEngine()
        ensureInitialSourceSeeded(settingsEngine)
        val active = resolveActiveApiBaseUrl()
        val servers = settingsEngine
            .listSourceServers()
            .mapNotNull { server ->
                val baseUrl = normalizeConfiguredBaseUrl(server.baseUrl)
                if (baseUrl.isBlank()) return@mapNotNull null
                SourceServerConfig(
                    baseUrl = baseUrl,
                    title = server.title.ifBlank { sourceTitleFromBaseUrl(baseUrl) },
                    color = server.color,
                    iconUrl = server.iconUrl,
                    isActive = baseUrl == active,
                )
            }
            .sortedWith(
                compareByDescending<SourceServerConfig> { it.isActive }
                    .thenBy { it.title.lowercase() },
            )
        return servers
    }

    fun addSource(userInput: String): SourceServerConfig {
        val candidates = sourceUrlCandidates(userInput)
        if (candidates.isEmpty()) {
            throw IOException("Please enter a server URL.")
        }

        val probeEngine = settingsEngine()
        var lastError: Throwable? = null
        for (candidate in candidates) {
            val status = try {
                probeEngine.probeStatus(candidate)
            } catch (err: Throwable) {
                lastError = err
                continue
            }
            val title = status.name.ifBlank { sourceTitleFromBaseUrl(candidate) }
            probeEngine.upsertSourceServer(
                SourceServer(
                    baseUrl = candidate,
                    title = title,
                    color = status.primaryColor,
                    iconUrl = status.iconUrl,
                ),
            )
            probeEngine.setUserPreference(SOURCES_BOOTSTRAPPED_KEY, true.toString())
            setActiveSource(candidate)
            return listSourceServers().first { it.baseUrl == candidate }
        }

        throw IOException(
            "Unable to connect to /api/status. ${lastError?.message ?: "Check URL and try again."}",
            lastError,
        )
    }

    fun removeSource(baseUrl: String): Boolean {
        val normalized = normalizeConfiguredBaseUrl(baseUrl)
        if (normalized.isBlank()) {
            return false
        }

        val settingsEngine = settingsEngine()
        val removed = settingsEngine.removeSourceServer(normalized)
        if (!removed) {
            return false
        }

        if (resolveActiveApiBaseUrl() == normalized) {
            val nextActive = settingsEngine
                .listSourceServers()
                .map { server -> normalizeConfiguredBaseUrl(server.baseUrl) }
                .firstOrNull { candidate -> candidate.isNotBlank() }
            if (nextActive != null) {
                setActiveSource(nextActive)
            } else {
                clearActiveSource()
            }
        }
        return true
    }

    fun setActiveSource(baseUrl: String): Boolean {
        val normalized = normalizeConfiguredBaseUrl(baseUrl)
        if (normalized.isBlank()) {
            return false
        }
        val available = settingsEngine()
            .listSourceServers()
            .map { server ->
                normalizeConfiguredBaseUrl(server.baseUrl)
            }
            .filter { candidate -> candidate.isNotBlank() }
            .toSet()
        if (normalized !in available) {
            return false
        }
        settingsEngine().setUserPreference(ACTIVE_SOURCE_KEY, normalized)
        activeBaseUrlCache = normalized
        return true
    }

    fun loadSettings(prefix: String = SETTINGS_PREFIX): Map<String, String> {
        return settingsEngine()
            .listUserPreferences(prefix)
            .associate { pref -> pref.id to pref.preferenceValue }
    }

    fun setSetting(key: String, value: String): Boolean {
        return settingsEngine().setUserPreference(key, value)
    }

    fun getSetting(key: String): String? = settingsEngine().getUserPreference(key)

    fun clearCacheData(): Long = activeEngine().clearCacheData().toLong()

    fun clearWatchHistory(): Long = activeEngine().clearWatchHistory().toLong()

    fun clearAllFavorites(): Long = activeEngine().clearAllFavorites().toLong()

    fun clearAchievements(): Long = activeEngine().clearAchievements().toLong()

    fun resetAllData(): Boolean {
        val ok = activeEngine().resetAllData()
        val settingsEngine = settingsEngine()
        activeBaseUrlCache = null
        ensureInitialSourceSeeded(settingsEngine)
        return ok
    }

    private fun trackDownloadedVideo(channelId: String, videoId: String, savedPath: String): Boolean {
        val key = downloadedVideoPreferenceKey(channelId, videoId) ?: return false
        val file = File(savedPath)
        if (!file.exists() || !file.isFile) {
            return false
        }
        return settingsEngine().setUserPreference(key, file.absolutePath)
    }

    private fun ensureBridgeScriptInstalled() {
        if (curlCffiBridge.exists()) return

        runCatching {
            appContext.assets.open("curl_cffi_fetch.py").use { input ->
                curlCffiBridge.parentFile?.mkdirs()
                curlCffiBridge.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            curlCffiBridge.setExecutable(true)
        }
    }

    private fun activeSourceEngine(): Engine = engineFor(requireActiveSourceBaseUrl())

    private fun activeEngine(): Engine = engineFor(resolveOperationalApiBaseUrl())

    private fun settingsEngine(): Engine = engineFor(STARTING_SOURCE_BASE_URL)

    private fun engineFor(baseUrl: String): Engine {
        val normalized = normalizeConfiguredBaseUrl(baseUrl)
        require(normalized.isNotBlank()) { "baseUrl cannot be blank" }
        synchronized(engineLock) {
            return engines.getOrPut(normalized) {
                ensureBridgeScriptInstalled()
                val pythonExecutable = resolvePythonExecutable()
                val canUseCurlCffiBridge = pythonExecutable.startsWith("/") && File(pythonExecutable).exists()
                val config = EngineConfig(
                    apiBaseUrl = normalized,
                    dbPath = databaseFile.absolutePath,
                    ytDlpPath = "chaquopy:yt_dlp",
                    pythonExecutable = pythonExecutable,
                    curlCffiScriptPath = if (canUseCurlCffiBridge) {
                        curlCffiBridge.takeIf { it.exists() }?.absolutePath
                    } else {
                        null
                    },
                    ytDlpRepoApi = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest",
                )
                Engine(config)
            }
        }
    }

    private fun resolvePythonExecutable(): String {
        pythonExecutableCache?.let { cached -> return cached }

        val resolved = runCatching {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(appContext))
            }
            val executable = Python.getInstance()
                .getModule("sys")
                .get("executable")
                .toString()
                .trim()
            executable.takeIf { it.isNotBlank() } ?: "python3"
        }.getOrDefault("python3")

        pythonExecutableCache = resolved
        return resolved
    }

    private fun resolveOperationalApiBaseUrl(): String {
        return resolveActiveApiBaseUrl() ?: STARTING_SOURCE_BASE_URL
    }

    private fun requireActiveSourceBaseUrl(): String {
        return resolveActiveApiBaseUrl()
            ?: throw IOException("No source configured. Add a source in Settings > Sources.")
    }

    private fun clearActiveSource() {
        settingsEngine().setUserPreference(ACTIVE_SOURCE_KEY, "")
        activeBaseUrlCache = null
    }

    private fun resolveActiveApiBaseUrl(): String? {
        activeBaseUrlCache?.let { cached ->
            return cached
        }

        val settingsEngine = settingsEngine()
        ensureInitialSourceSeeded(settingsEngine)
        val preferred = settingsEngine.getUserPreference(ACTIVE_SOURCE_KEY)
            ?.let(::normalizeConfiguredBaseUrl)
            ?.takeIf { it.isNotBlank() }
        val available = settingsEngine
            .listSourceServers()
            .map { server -> normalizeConfiguredBaseUrl(server.baseUrl) }
            .filter { baseUrl -> baseUrl.isNotBlank() }

        val active = when {
            preferred != null && preferred in available -> preferred
            available.isNotEmpty() -> available.first()
            else -> null
        }
        if (preferred != active) {
            settingsEngine.setUserPreference(ACTIVE_SOURCE_KEY, active.orEmpty())
        }
        activeBaseUrlCache = active
        return active
    }

    private fun ensureInitialSourceSeeded(settingsEngine: Engine) {
        val existing = settingsEngine
            .listSourceServers()
            .map { server -> normalizeConfiguredBaseUrl(server.baseUrl) }
            .any { baseUrl -> baseUrl.isNotBlank() }
        if (existing) return

        val alreadyBootstrapped = settingsEngine
            .getUserPreference(SOURCES_BOOTSTRAPPED_KEY)
            ?.toBooleanStrictOrNull()
            ?: false
        if (alreadyBootstrapped) return

        val status = runCatching { settingsEngine.probeStatus(STARTING_SOURCE_BASE_URL) }.getOrNull()
        settingsEngine.upsertSourceServer(
            SourceServer(
                baseUrl = STARTING_SOURCE_BASE_URL,
                title = status?.name?.ifBlank { sourceTitleFromBaseUrl(STARTING_SOURCE_BASE_URL) }
                    ?: sourceTitleFromBaseUrl(STARTING_SOURCE_BASE_URL),
                color = status?.primaryColor,
                iconUrl = status?.iconUrl,
            ),
        )
        settingsEngine.setUserPreference(SOURCES_BOOTSTRAPPED_KEY, true.toString())
        settingsEngine.setUserPreference(ACTIVE_SOURCE_KEY, STARTING_SOURCE_BASE_URL)
        activeBaseUrlCache = STARTING_SOURCE_BASE_URL
    }
}

internal fun sourceUrlCandidates(userInput: String): List<String> {
    val trimmed = userInput.trim().trimEnd('/')
    if (trimmed.isBlank()) {
        return emptyList()
    }
    if (URL_SCHEME_REGEX.containsMatchIn(trimmed)) {
        return normalizeConfiguredBaseUrl(trimmed)
            .takeIf { candidate -> candidate.isNotBlank() }
            ?.let(::listOf)
            ?: emptyList()
    }
    return listOf(
        normalizeConfiguredBaseUrl("https://$trimmed"),
        normalizeConfiguredBaseUrl("http://$trimmed"),
    )
        .filter { candidate -> candidate.isNotBlank() }
        .distinct()
}

internal fun normalizeConfiguredBaseUrl(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isBlank()) {
        return ""
    }
    if (trimmed.any { ch -> ch.isWhitespace() || ch == '\u0000' }) {
        return ""
    }

    val candidate = if (URL_SCHEME_REGEX.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return ""
    val scheme = uri.scheme?.lowercase(Locale.US) ?: return ""
    if (scheme !in ALLOWED_SOURCE_SCHEMES) {
        return ""
    }
    if (uri.userInfo != null) {
        return ""
    }
    val host = uri.host?.takeIf { hostValue -> hostValue.isNotBlank() } ?: return ""
    val port = if (uri.port in 1..65535) ":${uri.port}" else ""
    val normalizedHost = if (':' in host && !host.startsWith("[") && !host.endsWith("]")) {
        "[$host]"
    } else {
        host
    }
    val path = uri.rawPath.orEmpty()
    val query = uri.rawQuery?.let { rawQuery -> "?$rawQuery" }.orEmpty()
    return "$scheme://$normalizedHost$port$path$query".trimEnd('/')
}

private fun sourceTitleFromBaseUrl(baseUrl: String): String {
    return baseUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .ifBlank { "Source" }
}

data class PlaybackResolution(
    val id: String,
    val title: String,
    val pageUrl: String,
    val streamUrl: String,
    val requestHeaders: Map<String, String>,
    val thumbnailUrl: String?,
    val authorName: String?,
    val extractor: String?,
    val formatId: String?,
    val ext: String?,
    val protocol: String?,
    val durationSeconds: UInt?,
    val ytDlpVersion: String?,
    val diagnostics: List<String>,
)

data class VideoDownloadResult(
    val id: String,
    val title: String,
    val pageUrl: String,
    val savedPath: String,
    val savedName: String,
    val ytDlpVersion: String?,
    val diagnostics: List<String>,
)

private class YtDlpResolver(private val context: Context) {
    @Volatile
    private var lastState: String = "yt-dlp resolver not initialized"

    fun state(): String = lastState

    fun resolve(pageUrl: String, ytdlpCommand: String? = null): PlaybackResolution {
        ensurePythonStarted()

        val normalizedCommand = ytdlpCommand
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return try {
            val module = Python.getInstance().getModule("ytdlp_bridge")
            val payload = module.callAttr("extract", pageUrl, normalizedCommand).toString()
            val resolved = parseResolutionPayload(payload, pageUrl)

            lastState = buildString {
                append("yt-dlp resolve ok")
                normalizedCommand?.let { append(" custom_args=true") }
                resolved.extractor?.let { append(" extractor=$it") }
                resolved.formatId?.let { append(" format=$it") }
                resolved.ext?.let { append(" ext=$it") }
            }
            resolved
        } catch (err: PyException) {
            lastState = "yt-dlp python error: ${err.message ?: "unknown"}"
            throw IOException("yt-dlp python error: ${err.message ?: "unknown"}", err)
        } catch (err: Throwable) {
            lastState = "yt-dlp resolver error: ${err.message ?: "unknown"}"
            throw IOException("yt-dlp resolver error: ${err.message ?: "unknown"}", err)
        }
    }

    fun download(
        pageUrl: String,
        outputDir: File,
        filenameHint: String?,
        ytdlpCommand: String? = null,
    ): VideoDownloadResult {
        ensurePythonStarted()

        val normalizedCommand = ytdlpCommand
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return try {
            val module = Python.getInstance().getModule("ytdlp_bridge")
            val payload = module.callAttr(
                "download",
                pageUrl,
                outputDir.absolutePath,
                filenameHint.orEmpty(),
                normalizedCommand,
            ).toString()
            val result = parseDownloadPayload(payload, pageUrl)

            lastState = buildString {
                append("yt-dlp download ok")
                normalizedCommand?.let { append(" custom_args=true") }
                append(" file=")
                append(result.savedName)
            }
            result
        } catch (err: PyException) {
            lastState = "yt-dlp python error: ${err.message ?: "unknown"}"
            throw IOException("yt-dlp python error: ${err.message ?: "unknown"}", err)
        } catch (err: Throwable) {
            lastState = "yt-dlp resolver error: ${err.message ?: "unknown"}"
            throw IOException("yt-dlp resolver error: ${err.message ?: "unknown"}", err)
        }
    }

    @Synchronized
    private fun ensurePythonStarted() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
        lastState = runCatching {
            val version = Python.getInstance().getModule("ytdlp_bridge").callAttr("version").toString()
            "python runtime ready (yt-dlp $version)"
        }.getOrDefault("python runtime ready")
    }
}

internal fun parseResolutionPayload(payload: String, pageUrl: String): PlaybackResolution {
    val json = JSONObject(payload)
    val streamUrl = json.optString("streamUrl").trim()
    if (streamUrl.isEmpty()) {
        throw IOException("yt-dlp did not return a stream url")
    }
    if (!isAllowedSchemeUrl(streamUrl, ALLOWED_STREAM_SCHEMES)) {
        throw IOException("yt-dlp returned unsupported stream url scheme")
    }

    val headers = buildMap {
        val objectHeaders = json.optJSONObject("requestHeaders")
        if (objectHeaders != null) {
            val keys = objectHeaders.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next()
                val key = sanitizeHeaderComponent(rawKey)
                val value = sanitizeHeaderComponent(objectHeaders.optString(rawKey))
                if (key != null && value != null) {
                    put(key, value)
                }
            }
        }
    }

    return PlaybackResolution(
        id = json.optString("id", pageUrl),
        title = json.optString("title", "Untitled"),
        pageUrl = json.optString("pageUrl", pageUrl),
        streamUrl = streamUrl,
        requestHeaders = headers,
        thumbnailUrl = json.optString("thumbnailUrl").takeIf { it.isNotBlank() },
        authorName = json.optString("authorName").takeIf { it.isNotBlank() },
        extractor = json.optString("extractor").takeIf { it.isNotBlank() },
        formatId = json.optString("formatId").takeIf { it.isNotBlank() },
        ext = json.optString("ext").takeIf { it.isNotBlank() },
        protocol = json.optString("protocol").takeIf { it.isNotBlank() },
        durationSeconds = json.optLong("durationSeconds", -1)
            .takeIf { it >= 0 }
            ?.toUInt(),
        ytDlpVersion = json.optString("ytDlpVersion").takeIf { it.isNotBlank() },
        diagnostics = buildList {
            val values = json.optJSONArray("diagnostics") ?: return@buildList
            for (index in 0 until values.length()) {
                val entry = values.optString(index).trim()
                if (entry.isNotEmpty()) {
                    add(entry)
                }
            }
        },
    )
}

internal fun parseDownloadPayload(payload: String, pageUrl: String): VideoDownloadResult {
    val json = JSONObject(payload)
    val savedPath = json.optString("savedPath").trim()
    if (savedPath.isEmpty()) {
        throw IOException("yt-dlp download did not return a saved file path")
    }

    return VideoDownloadResult(
        id = json.optString("id", pageUrl),
        title = json.optString("title", "Untitled"),
        pageUrl = json.optString("pageUrl", pageUrl),
        savedPath = savedPath,
        savedName = json.optString("savedName")
            .trim()
            .ifEmpty { File(savedPath).name.ifEmpty { "video.mp4" } },
        ytDlpVersion = json.optString("ytDlpVersion").takeIf { it.isNotBlank() },
        diagnostics = buildList {
            val values = json.optJSONArray("diagnostics") ?: return@buildList
            for (index in 0 until values.length()) {
                val entry = values.optString(index).trim()
                if (entry.isNotEmpty()) {
                    add(entry)
                }
            }
        },
    )
}

private fun sanitizeHeaderComponent(input: String?): String? {
    val normalized = input
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
        ?: return null
    if (
        normalized.contains('\r') ||
        normalized.contains('\n') ||
        normalized.contains('\u0000')
    ) {
        return null
    }
    return normalized
}

private fun isAllowedSchemeUrl(url: String, allowedSchemes: Set<String>): Boolean {
    val parsed = runCatching { URI(url) }.getOrNull() ?: return false
    val scheme = parsed.scheme?.lowercase(Locale.US) ?: return false
    return scheme in allowedSchemes
}

internal fun downloadedVideoPreferenceKey(channelId: String, videoId: String): String? {
    val normalizedChannel = channelId.trim()
    val normalizedVideoId = videoId.trim()
    if (normalizedChannel.isEmpty() || normalizedVideoId.isEmpty()) {
        return null
    }
    return buildString {
        append(DOWNLOADED_VIDEO_KEY_PREFIX)
        append(escapeDownloadedVideoKeyToken(normalizedChannel))
        append("::")
        append(escapeDownloadedVideoKeyToken(normalizedVideoId))
    }
}

private fun escapeDownloadedVideoKeyToken(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
