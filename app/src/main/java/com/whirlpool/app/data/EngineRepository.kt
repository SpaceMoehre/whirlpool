package com.whirlpool.app.data

import android.content.Context
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.whirlpool.engine.Engine
import com.whirlpool.engine.EngineConfig
import com.whirlpool.engine.FavoriteItem
import com.whirlpool.engine.FilterSelection
import com.whirlpool.engine.StatusSummary
import com.whirlpool.engine.VideoItem
import java.io.File
import java.io.IOException
import org.json.JSONObject

class EngineRepository(private val context: Context) {
    private val appContext = context.applicationContext

    private val databaseFile: File by lazy {
        File(appContext.filesDir, "shared/whirlpool.db")
    }

    private val curlCffiBridge: File by lazy {
        File(appContext.filesDir, "curl_cffi_fetch.py")
    }

    private val ytDlpResolver: YtDlpResolver by lazy {
        YtDlpResolver(appContext)
    }

    private val engine: Engine by lazy {
        ensureBridgeScriptInstalled()
        val config = EngineConfig(
            apiBaseUrl = "https://getfigleaf.com",
            dbPath = databaseFile.absolutePath,
            ytDlpPath = "chaquopy:yt_dlp",
            pythonExecutable = "python3",
            curlCffiScriptPath = curlCffiBridge.takeIf { it.exists() }?.absolutePath,
            ytDlpRepoApi = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest",
        )
        Engine(config)
    }

    fun status(): StatusSummary = engine.syncStatus()

    fun discover(
        query: String,
        page: UInt = 1u,
        limit: UInt = 10u,
        channelId: String = "",
        filters: Map<String, String> = emptyMap(),
    ): List<VideoItem> {
        val selections = filters.entries.map { entry ->
            FilterSelection(optionId = entry.key, choiceId = entry.value)
        }
        return engine.discoverVideosWithFilters(
            query = query,
            page = page,
            limit = limit,
            channelId = channelId,
            filters = selections,
        )
    }

    fun resolve(pageUrl: String): PlaybackResolution = ytDlpResolver.resolve(pageUrl)

    fun favorites(): List<FavoriteItem> = engine.listFavorites()

    fun addFavorite(video: VideoItem): FavoriteItem = engine.addFavorite(video)

    fun removeFavorite(videoId: String): Boolean = engine.removeFavorite(videoId)

    fun exportDatabase(path: String): Boolean = engine.exportDatabase(path)

    fun importDatabase(path: String): Boolean = engine.importDatabase(path)

    fun transferPath(): String {
        return File(appContext.filesDir, "exports/whirlpool-export.db").absolutePath
    }

    fun checkYtDlpUpdates() = engine.checkYtDlpUpdate()

    fun ytDlpState(): String = ytDlpResolver.state()

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

private class YtDlpResolver(private val context: Context) {
    @Volatile
    private var lastState: String = "yt-dlp resolver not initialized"

    fun state(): String = lastState

    fun resolve(pageUrl: String): PlaybackResolution {
        ensurePythonStarted()

        return try {
            val module = Python.getInstance().getModule("ytdlp_bridge")
            val payload = module.callAttr("extract", pageUrl).toString()
            val resolved = parseResolutionPayload(payload, pageUrl)

            lastState = buildString {
                append("yt-dlp resolve ok")
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

    val headers = buildMap {
        val objectHeaders = json.optJSONObject("requestHeaders")
        if (objectHeaders != null) {
            val keys = objectHeaders.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = objectHeaders.optString(key).trim()
                if (key.isNotBlank() && value.isNotBlank()) {
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
