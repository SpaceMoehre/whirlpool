package com.whirlpool.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whirlpool.app.data.EngineRepository
import com.whirlpool.app.data.SourceServerConfig
import com.whirlpool.engine.StatusChannel
import com.whirlpool.engine.StatusChoice
import com.whirlpool.engine.StatusFilterOption
import com.whirlpool.engine.StatusSummary
import com.whirlpool.engine.VideoItem
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppSettings(
    val dominantHand: String = "Righty",
    val enableHaptics: Boolean = false,
    val detailedAlerts: Boolean = false,
    val showExtractionToast: Boolean = false,
    val autoPreview: Boolean = false,
    val statsAndAchievements: Boolean = true,
    val crashRecovery: Boolean = true,
    val categoriesSection: Boolean = true,
    val favoritesSection: Boolean = true,
    val viewingHistory: Boolean = false,
    val searchHistory: Boolean = true,
    val videoRowDetails: Boolean = true,
    val theme: String = "Dark",
    val palette: String = "Blue",
    val preferredResolution: String = "2160p",
    val preferredFormat: String = "HEVC",
    val loopPlayback: Boolean = false,
    val pictureInPicture: Boolean = true,
    val useSystemPlayer: Boolean = false,
    val skipDuration: String = "Automatic",
    val audioOutputNotification: Boolean = false,
    val blockAudioOutputChanges: Boolean = true,
    val audioNormalization: Boolean = true,
    val showLockScreen: Boolean = true,
    val unlockWithFaceId: Boolean = false,
    val blurOnScreenCapture: Boolean = true,
    val enableAnalytics: Boolean = true,
    val enableCrashReporting: Boolean = true,
    val followedTags: List<String> = emptyList(),
    val followedUploaders: List<String> = emptyList(),
)

object SettingKeys {
    const val DOMINANT_HAND = "settings.general.dominant_hand"
    const val ENABLE_HAPTICS = "settings.general.enable_haptics"
    const val DETAILED_ALERTS = "settings.general.detailed_alerts"
    const val SHOW_EXTRACTION_TOAST = "settings.general.show_extraction_toast"
    const val AUTO_PREVIEW = "settings.general.auto_preview"
    const val STATS_AND_ACHIEVEMENTS = "settings.general.stats_achievements"
    const val CRASH_RECOVERY = "settings.general.crash_recovery"
    const val CATEGORIES_SECTION = "settings.general.categories_section"
    const val FAVORITES_SECTION = "settings.general.favorites_section"
    const val VIEWING_HISTORY = "settings.general.viewing_history"
    const val SEARCH_HISTORY = "settings.general.search_history"
    const val VIDEO_ROW_DETAILS = "settings.general.video_row_details"
    const val THEME = "settings.general.theme"
    const val PALETTE = "settings.general.palette"

    const val PLAYBACK_PREFERRED_RESOLUTION = "settings.playback.preferred_resolution"
    const val PLAYBACK_PREFERRED_FORMAT = "settings.playback.preferred_format"
    const val PLAYBACK_LOOP = "settings.playback.loop"
    const val PLAYBACK_PIP = "settings.playback.pip"
    const val PLAYBACK_SYSTEM_PLAYER = "settings.playback.system_player"
    const val PLAYBACK_SKIP_DURATION = "settings.playback.skip_duration"
    const val PLAYBACK_AUDIO_OUTPUT_NOTIFICATION = "settings.playback.audio_output_notification"
    const val PLAYBACK_BLOCK_AUDIO_OUTPUT_CHANGES = "settings.playback.block_audio_output_changes"
    const val PLAYBACK_AUDIO_NORMALIZATION = "settings.playback.audio_normalization"

    const val PRIVACY_SHOW_LOCK_SCREEN = "settings.privacy.show_lock_screen"
    const val PRIVACY_UNLOCK_FACE_ID = "settings.privacy.unlock_face_id"
    const val PRIVACY_BLUR_SCREEN_CAPTURE = "settings.privacy.blur_screen_capture"
    const val PRIVACY_ENABLE_ANALYTICS = "settings.privacy.enable_analytics"
    const val PRIVACY_ENABLE_CRASH_REPORTING = "settings.privacy.enable_crash_reporting"

    const val FOLLOWING_TAGS = "settings.following.tags"
    const val FOLLOWING_UPLOADERS = "settings.following.uploaders"
}

data class WhirlpoolUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val videos: List<VideoItem> = emptyList(),
    val favorites: List<VideoItem> = emptyList(),
    val categories: List<String> = emptyList(),
    val channelDetails: List<StatusChannel> = emptyList(),
    val availableChannels: List<ChannelMenuItem> = emptyList(),
    val activeChannel: String = "catflix",
    val selectedFilters: Map<String, Set<String>> = emptyMap(),
    val currentPage: UInt = 1u,
    val hasMorePages: Boolean = true,
    val isLoadingNextPage: Boolean = false,
    val selectedVideo: VideoItem? = null,
    val streamUrl: String? = null,
    val streamHeaders: Map<String, String> = emptyMap(),
    val statusText: String = "Whirlpool",
    val actionText: String? = null,
    val errorText: String? = null,
    val logs: List<String> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val sourceServers: List<SourceServerConfig> = emptyList(),
    val activeServerBaseUrl: String = "",
)

data class ChannelMenuItem(
    val serverBaseUrl: String,
    val serverTitle: String,
    val serverHost: String,
    val channelId: String,
    val channelTitle: String,
    val channelDescription: String?,
    val channelFaviconUrl: String?,
)

class WhirlpoolViewModel(
    private val repository: EngineRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WhirlpoolUiState())
    val uiState: StateFlow<WhirlpoolUiState> = mutableState.asStateFlow()
    private var querySearchDebounceJob: Job? = null

    init {
        log("App initialized.")
        loadSettingsAndSources()
        loadRecentCrashReports()
        refreshAll()
    }

    fun onQueryChange(query: String) {
        mutableState.value = mutableState.value.copy(query = query)
        scheduleDebouncedSearch(query)
    }

    fun refreshAll(showLoading: Boolean = true) {
        val current = mutableState.value
        mutableState.value = if (showLoading) {
            current.copy(isLoading = true, errorText = null)
        } else {
            current.copy(errorText = null)
        }
        log(
            if (showLoading) {
                "Refreshing feed from server."
            } else {
                "Refreshing videos in background."
            },
        )

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val status = repository.status()
                    val channels = normalizeChannels(status)
                    val selectedChannel = channels.firstOrNull { channel ->
                        channel.id == current.activeChannel
                    } ?: channels.firstOrNull()
                    val nextChannelId = selectedChannel?.id ?: current.activeChannel
                    val normalizedFilters = normalizeFilterSelection(
                        selectedChannel,
                        current.selectedFilters,
                    )

                    val discovered = repository.discover(
                        query = current.query,
                        page = 1u,
                        limit = FEED_PAGE_LIMIT,
                        channelId = nextChannelId,
                        filters = normalizedFilters,
                    )
                    val videos = discovered
                    val favoriteVideos = repository.favoriteVideos()
                    val activeBaseUrl = repository.activeApiBaseUrl()
                    val sources = repository.listSourceServers()
                    val availableChannels = buildChannelMenuItems(
                        sources = sources,
                        activeStatus = status,
                        activeBaseUrl = activeBaseUrl,
                    )

                    current.copy(
                        isLoading = false,
                        statusText = if (status.name.isBlank()) "Whirlpool" else status.name,
                        videos = videos,
                        favorites = favoriteVideos,
                        categories = status.sources,
                        channelDetails = channels,
                        availableChannels = availableChannels,
                        activeChannel = nextChannelId,
                        selectedFilters = normalizedFilters,
                        currentPage = 1u,
                        hasMorePages = videos.size >= FEED_PAGE_LIMIT.toInt(),
                        isLoadingNextPage = false,
                        sourceServers = sources,
                        activeServerBaseUrl = activeBaseUrl,
                        actionText = null,
                        errorText = null,
                    )
                }
            }.onSuccess { state ->
                mutableState.value = state
                log("Feed loaded: ${state.videos.size} videos, ${state.categories.size} categories.")
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    isLoadingNextPage = false,
                    errorText = throwable.message ?: "Unable to load live feed from server.",
                )
                log("Feed load failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun loadNextPage() {
        val current = mutableState.value
        if (current.isLoading || current.isLoadingNextPage || !current.hasMorePages) {
            return
        }

        val nextPage = current.currentPage + 1u
        mutableState.value = current.copy(isLoadingNextPage = true)

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.discover(
                        query = current.query,
                        page = nextPage,
                        limit = FEED_PAGE_LIMIT,
                        channelId = current.activeChannel,
                        filters = current.selectedFilters,
                    )
                }
            }.onSuccess { discovered ->
                val latest = mutableState.value
                if (!latest.isLoadingNextPage) {
                    return@onSuccess
                }

                val existing = latest.videos
                val appended = (existing + discovered).distinctBy { video -> video.id }
                val hasMore = discovered.size >= FEED_PAGE_LIMIT.toInt()

                mutableState.value = latest.copy(
                    videos = appended,
                    currentPage = if (discovered.isNotEmpty()) nextPage else latest.currentPage,
                    hasMorePages = hasMore && discovered.isNotEmpty(),
                    isLoadingNextPage = false,
                )
                if (discovered.isNotEmpty()) {
                    log("Loaded page $nextPage (+${discovered.size} videos).")
                }
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    isLoadingNextPage = false,
                )
                log("Load next page failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun search() {
        cancelDebouncedSearch()
        refreshAll(showLoading = true)
    }

    fun onChannelSelected(channelId: String) {
        val current = mutableState.value
        onChannelSelected(current.activeServerBaseUrl, channelId)
    }

    fun onChannelSelected(serverBaseUrl: String, channelId: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val currentActive = repository.activeApiBaseUrl()
                    if (serverBaseUrl.isNotBlank() && serverBaseUrl != currentActive) {
                        val changed = repository.setActiveSource(serverBaseUrl)
                        if (!changed) {
                            throw IOException("Unable to switch source.")
                        }
                    }
                    repository.listSourceServers()
                }
            }.onSuccess { sources ->
                val current = mutableState.value
                mutableState.value = current.copy(
                    activeChannel = channelId,
                    sourceServers = sources,
                    activeServerBaseUrl = repository.activeApiBaseUrl(),
                    errorText = null,
                )
                refreshAll(showLoading = true)
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = throwable.message ?: "Unable to switch channel.",
                )
                log("Channel switch failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun onFilterSelected(optionId: String, choiceId: String) {
        val current = mutableState.value
        val selectedChannel = current.channelDetails.firstOrNull { channel ->
            channel.id == current.activeChannel
        } ?: return
        val selectedOption = selectedChannel
            .options
            .firstOrNull { option -> option.id == optionId }
            ?: return
        val validChoice = selectedOption
            .choices
            .any { choice -> choice.id == choiceId }
        if (!validChoice || selectedOption.choices.isEmpty()) {
            return
        }

        val currentSelection = current.selectedFilters[optionId].orEmpty()
        val updatedSelection = if (selectedOption.multiSelect) {
            if (choiceId in currentSelection) {
                currentSelection - choiceId
            } else {
                currentSelection + choiceId
            }
        } else {
            setOf(choiceId)
        }

        mutableState.value = current.copy(
            selectedFilters = current.selectedFilters + (optionId to updatedSelection),
        )
        refreshVideosInBackground()
    }

    fun onFilterToggleAll(optionId: String, selectAll: Boolean) {
        val current = mutableState.value
        val selectedChannel = current.channelDetails.firstOrNull { channel ->
            channel.id == current.activeChannel
        } ?: return
        val selectedOption = selectedChannel
            .options
            .firstOrNull { option -> option.id == optionId && option.multiSelect }
            ?: return

        val updatedSelection = if (selectAll) {
            selectedOption.choices.map { choice -> choice.id }.toSet()
        } else {
            emptySet()
        }

        mutableState.value = current.copy(
            selectedFilters = current.selectedFilters + (optionId to updatedSelection),
        )
        refreshVideosInBackground()
    }

    fun playVideo(video: VideoItem) {
        log("Playback requested for: ${video.title}")
        log("yt-dlp state: ${repository.ytDlpState()}")
        val selectedChannel = mutableState.value.channelDetails.firstOrNull { channel ->
            channel.id == mutableState.value.activeChannel
        }
        val channelYtdlpCommand = selectedChannel
            ?.ytdlpCommand
            ?.takeIf { command -> command.isNotBlank() }
        channelYtdlpCommand?.let { command ->
            log("Applying channel yt-dlp args: $command")
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolved = repository.resolve(
                        pageUrl = video.pageUrl,
                        ytdlpCommand = channelYtdlpCommand,
                    )
                    Pair(
                        mutableState.value.copy(
                            selectedVideo = video,
                            streamUrl = resolved.streamUrl,
                            streamHeaders = resolved.requestHeaders,
                            errorText = null,
                            actionText = null,
                        ),
                        resolved,
                    )
                }
            }.onSuccess { (state, resolved) ->
                mutableState.value = state
                val host = state.streamUrl
                    ?.substringAfter("://")
                    ?.substringBefore("/")
                    ?.ifBlank { "unknown" }
                    ?: "unknown"
                log(
                    "Playback stream resolved successfully " +
                        "(headers=${state.streamHeaders.size}, host=$host).",
                )
                resolved.ytDlpVersion?.let { log("yt-dlp version: $it") }
                resolved.diagnostics
                    .takeLast(3)
                    .forEach { log("yt-dlp diagnostic: $it") }
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    selectedVideo = null,
                    streamUrl = null,
                    streamHeaders = emptyMap(),
                    errorText = throwable.message ?: "Unable to play this video.",
                    actionText = null,
                )
                log("Playback failed: ${throwable.message ?: "unknown error"}")
                log("yt-dlp state after failure: ${repository.ytDlpState()}")
            }
        }
    }

    fun dismissPlayer() {
        mutableState.value = mutableState.value.copy(
            selectedVideo = null,
            streamUrl = null,
            streamHeaders = emptyMap(),
        )
    }

    fun onPlayerError(message: String) {
        mutableState.value = mutableState.value.copy(
            selectedVideo = null,
            streamUrl = null,
            streamHeaders = emptyMap(),
            errorText = message,
            actionText = null,
        )
        log("Player error: $message")
    }

    fun onPlayerEvent(message: String) {
        log("Player: $message")
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val favoriteIds = repository.favorites().map { it.videoId }.toSet()
                    if (video.id in favoriteIds) {
                        repository.removeFavorite(video.id)
                    } else {
                        repository.addFavorite(video)
                    }
                    repository.favoriteVideos()
                }
            }.onSuccess { favorites ->
                mutableState.value = mutableState.value.copy(favorites = favorites)
                log("Favorites updated. Total favorites: ${favorites.size}.")
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = "Could not update favorites right now.",
                )
                log("Favorite update failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun exportDatabase() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val path = repository.transferPath()
                    repository.exportDatabase(path)
                    path
                }
            }.onSuccess { path ->
                mutableState.value = mutableState.value.copy(
                    actionText = "Exported DB to $path",
                    errorText = null,
                )
                log("Database exported to $path")
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = "Export failed. Verify app storage permissions and try again.",
                )
                log("Database export failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun importDatabase() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val path = repository.transferPath()
                    repository.importDatabase(path)
                    path
                }
            }.onSuccess {
                refreshAll()
                mutableState.value = mutableState.value.copy(
                    actionText = "Imported DB from $it",
                    errorText = null,
                )
                log("Database imported from $it")
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = "Import failed. No valid export file was found.",
                )
                log("Database import failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun clearCacheData() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.clearCacheData() }
            }.onSuccess { cleared ->
                refreshAll(showLoading = false)
                mutableState.value = mutableState.value.copy(
                    actionText = "Cleared cache entries: $cleared",
                    errorText = null,
                )
                log("Cache cleared: $cleared rows")
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = throwable.message ?: "Failed to clear cache.",
                )
                log("Cache clear failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.clearWatchHistory() }
            }.onSuccess { cleared ->
                mutableState.value = mutableState.value.copy(
                    actionText = "Cleared watch history: $cleared",
                    errorText = null,
                )
                log("Watch history cleared: $cleared rows")
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = throwable.message ?: "Failed to clear watch history.",
                )
                log("Watch history clear failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun clearAllFavorites() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.clearAllFavorites() }
            }.onSuccess { cleared ->
                refreshAll(showLoading = false)
                mutableState.value = mutableState.value.copy(
                    actionText = "Cleared favorites: $cleared",
                    errorText = null,
                )
                log("Favorites cleared: $cleared rows")
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = throwable.message ?: "Failed to clear favorites.",
                )
                log("Favorites clear failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun clearAchievements() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.clearAchievements() }
            }.onSuccess { cleared ->
                mutableState.value = mutableState.value.copy(
                    actionText = "Deleted achievements: $cleared",
                    errorText = null,
                )
                log("Achievements cleared: $cleared rows")
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = throwable.message ?: "Failed to clear achievements.",
                )
                log("Achievements clear failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.resetAllData()
                    repository.listSourceServers()
                }
            }.onSuccess { sources ->
                mutableState.value = mutableState.value.copy(
                    actionText = "All app data reset.",
                    errorText = null,
                    sourceServers = sources,
                    activeServerBaseUrl = repository.activeApiBaseUrl(),
                )
                loadSettingsAndSources()
                refreshAll(showLoading = false)
                log("All app data reset")
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = throwable.message ?: "Failed to reset app data.",
                )
                log("Reset all data failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun addSource(input: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val server = repository.addSource(input)
                    val sources = repository.listSourceServers()
                    Pair(server, sources)
                }
            }.onSuccess { (server, sources) ->
                mutableState.value = mutableState.value.copy(
                    sourceServers = sources,
                    activeServerBaseUrl = server.baseUrl,
                    actionText = "Added source: ${server.title}",
                    errorText = null,
                )
                log("Added source ${server.baseUrl}")
                refreshAll(showLoading = true)
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = throwable.message ?: "Unable to add source.",
                )
                log("Add source failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun removeSource(baseUrl: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val removed = repository.removeSource(baseUrl)
                    val sources = repository.listSourceServers()
                    Pair(removed, sources)
                }
            }.onSuccess { (removed, sources) ->
                if (removed) {
                    mutableState.value = mutableState.value.copy(
                        sourceServers = sources,
                        activeServerBaseUrl = repository.activeApiBaseUrl(),
                        actionText = "Removed source.",
                        errorText = null,
                    )
                    log("Removed source $baseUrl")
                    refreshAll(showLoading = true)
                } else {
                    mutableState.value = mutableState.value.copy(
                        errorText = "Source could not be removed.",
                    )
                }
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = throwable.message ?: "Unable to remove source.",
                )
                log("Remove source failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun activateSource(baseUrl: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val changed = repository.setActiveSource(baseUrl)
                    val sources = repository.listSourceServers()
                    Pair(changed, sources)
                }
            }.onSuccess { (changed, sources) ->
                if (changed) {
                    mutableState.value = mutableState.value.copy(
                        sourceServers = sources,
                        activeServerBaseUrl = repository.activeApiBaseUrl(),
                        actionText = "Source updated.",
                        errorText = null,
                    )
                    log("Active source changed to ${repository.activeApiBaseUrl()}")
                    refreshAll(showLoading = true)
                }
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = throwable.message ?: "Unable to switch source.",
                )
                log("Switch source failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun updateBooleanSetting(key: String, value: Boolean) {
        updateSetting(key, value.toString())
    }

    fun updateTextSetting(key: String, value: String) {
        updateSetting(key, value)
    }

    fun addFollowingTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        val current = mutableState.value.settings.followedTags
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return
        val updated = current + trimmed
        updateSetting(SettingKeys.FOLLOWING_TAGS, encodeStringList(updated))
    }

    fun removeFollowingTag(tag: String) {
        val updated = mutableState.value.settings.followedTags.filterNot { it == tag }
        updateSetting(SettingKeys.FOLLOWING_TAGS, encodeStringList(updated))
    }

    fun addFollowingUploader(uploader: String) {
        val trimmed = uploader.trim()
        if (trimmed.isEmpty()) return
        val current = mutableState.value.settings.followedUploaders
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return
        val updated = current + trimmed
        updateSetting(SettingKeys.FOLLOWING_UPLOADERS, encodeStringList(updated))
    }

    fun removeFollowingUploader(uploader: String) {
        val updated = mutableState.value.settings.followedUploaders.filterNot { it == uploader }
        updateSetting(SettingKeys.FOLLOWING_UPLOADERS, encodeStringList(updated))
    }

    fun clearLogs() {
        mutableState.value = mutableState.value.copy(logs = emptyList())
    }

    private fun updateSetting(key: String, value: String) {
        val current = mutableState.value
        val updatedSettings = applySetting(current.settings, key, value)
        mutableState.value = current.copy(settings = updatedSettings)

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val saved = repository.setSetting(key, value)
                    if (!saved) {
                        throw IOException("Unable to save setting: $key")
                    }
                }
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    errorText = throwable.message ?: "Unable to save setting.",
                )
                log("Persist setting failed for $key: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    private fun loadSettingsAndSources() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val persisted = repository.loadSettings()
                    val servers = repository.listSourceServers()
                    val activeBase = repository.activeApiBaseUrl()
                    Triple(persisted, servers, activeBase)
                }
            }.onSuccess { (persisted, servers, activeBase) ->
                var settings = AppSettings()
                persisted.forEach { (key, value) ->
                    settings = applySetting(settings, key, value)
                }
                mutableState.value = mutableState.value.copy(
                    settings = settings,
                    sourceServers = servers,
                    activeServerBaseUrl = activeBase,
                )
            }.onFailure { throwable ->
                log("Settings load failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    private fun loadRecentCrashReports() {
        viewModelScope.launch {
            val reports = withContext(Dispatchers.IO) {
                repository.recentCrashSummaries(limit = 5)
            }
            if (reports.isEmpty()) {
                return@launch
            }
            log("Recent crash reports:")
            reports.forEach { report ->
                log("Crash: $report")
            }
        }
    }

    private fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val entry = "[$time] $message"
        val existing = mutableState.value.logs
        val updated = (existing + entry).takeLast(200)
        mutableState.value = mutableState.value.copy(logs = updated)
    }

    private fun scheduleDebouncedSearch(expectedQuery: String) {
        cancelDebouncedSearch()
        querySearchDebounceJob = viewModelScope.launch {
            delay(1_000)
            val currentQuery = mutableState.value.query
            if (currentQuery == expectedQuery) {
                refreshAll(showLoading = true)
            }
        }
    }

    private fun cancelDebouncedSearch() {
        querySearchDebounceJob?.cancel()
        querySearchDebounceJob = null
    }

    private fun refreshVideosInBackground() {
        cancelDebouncedSearch()
        refreshAll(showLoading = false)
    }

    private fun normalizeChannels(status: com.whirlpool.engine.StatusSummary): List<StatusChannel> {
        if (status.channelDetails.isNotEmpty()) {
            return status.channelDetails
        }
        return status.channels.map { channelId ->
            StatusChannel(
                id = channelId,
                title = channelId,
                description = null,
                faviconUrl = null,
                ytdlpCommand = null,
                options = emptyList(),
            )
        }
    }

    private fun buildChannelMenuItems(
        sources: List<SourceServerConfig>,
        activeStatus: StatusSummary,
        activeBaseUrl: String,
    ): List<ChannelMenuItem> {
        val out = mutableListOf<ChannelMenuItem>()

        sources.forEach { source ->
            val status = if (source.baseUrl == activeBaseUrl) {
                activeStatus
            } else {
                runCatching { repository.statusForSource(source.baseUrl) }.getOrNull()
            } ?: return@forEach

            val channels = normalizeChannels(status)
            channels.forEach { channel ->
                out += ChannelMenuItem(
                    serverBaseUrl = source.baseUrl,
                    serverTitle = source.title,
                    serverHost = sourceHost(source.baseUrl),
                    channelId = channel.id,
                    channelTitle = channel.title.ifBlank { channel.id },
                    channelDescription = channel.description,
                    channelFaviconUrl = channel.faviconUrl,
                )
            }
        }

        return out.sortedWith(
            compareBy(
                String.CASE_INSENSITIVE_ORDER,
                ChannelMenuItem::channelTitle,
            ).thenBy(
                String.CASE_INSENSITIVE_ORDER,
                ChannelMenuItem::serverTitle,
            ),
        )
    }

    private fun sourceHost(baseUrl: String): String {
        return baseUrl
            .substringAfter("://")
            .substringBefore("/")
            .ifBlank { baseUrl }
    }

    private fun normalizeFilterSelection(
        channel: StatusChannel?,
        currentSelection: Map<String, Set<String>>,
    ): Map<String, Set<String>> {
        val channelOptions = channel?.options.orEmpty()
        if (channelOptions.isEmpty()) {
            return emptyMap()
        }

        val out = linkedMapOf<String, Set<String>>()
        channelOptions.forEach { option ->
            val hasStoredSelection = option.id in currentSelection
            val chosen = pickChoices(
                option = option,
                selectedChoiceIds = currentSelection[option.id].orEmpty(),
                keepEmptySelection = hasStoredSelection,
            )
            if (chosen.isNotEmpty() || (option.multiSelect && hasStoredSelection)) {
                out[option.id] = chosen
            }
        }
        return out
    }

    private fun pickChoices(
        option: StatusFilterOption,
        selectedChoiceIds: Set<String>,
        keepEmptySelection: Boolean,
    ): Set<String> {
        if (option.choices.isEmpty()) {
            return emptySet()
        }
        val validChoiceIds = option.choices.map { choice -> choice.id }.toSet()
        if (option.multiSelect) {
            val selected = selectedChoiceIds.filter { choiceId -> choiceId in validChoiceIds }.toSet()
            return if (selected.isNotEmpty() || keepEmptySelection) {
                selected
            } else {
                setOf(option.choices.first().id)
            }
        }
        val selected = selectedChoiceIds.firstOrNull { choiceId -> choiceId in validChoiceIds }
            ?: option.choices.first().id
        return setOf(selected)
    }

    companion object {
        private val FEED_PAGE_LIMIT: UInt = 10u

        fun factory(context: Context): ViewModelProvider.Factory {
            val repository = EngineRepository(context)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WhirlpoolViewModel(repository) as T
                }
            }
        }
    }

    override fun onCleared() {
        cancelDebouncedSearch()
        super.onCleared()
    }
}

private fun normalizePreferenceToken(value: String): String {
    return value.trim().trim('"')
}

private fun parsePreferenceBoolean(value: String): Boolean? {
    return normalizePreferenceToken(value).toBooleanStrictOrNull()
}

private fun normalizeDominantHand(value: String): String {
    val normalized = normalizePreferenceToken(value).lowercase()
    return when (normalized) {
        "left", "lefty" -> "Lefty"
        else -> "Righty"
    }
}

private fun normalizeThemeSetting(value: String): String {
    return when (normalizePreferenceToken(value).lowercase()) {
        "light" -> "Light"
        else -> "Dark"
    }
}

private fun normalizeSkipDurationSetting(value: String): String {
    return when (normalizePreferenceToken(value).lowercase()) {
        "automatic" -> "Automatic"
        "5s" -> "5s"
        "10s" -> "10s"
        "15s" -> "15s"
        "30s" -> "30s"
        else -> "Automatic"
    }
}

internal fun applySetting(settings: AppSettings, key: String, value: String): AppSettings {
    val normalized = normalizePreferenceToken(value)
    return when (key) {
        SettingKeys.DOMINANT_HAND -> settings.copy(dominantHand = normalizeDominantHand(value))
        SettingKeys.ENABLE_HAPTICS -> settings.copy(enableHaptics = parsePreferenceBoolean(value) ?: settings.enableHaptics)
        SettingKeys.DETAILED_ALERTS -> settings.copy(detailedAlerts = parsePreferenceBoolean(value) ?: settings.detailedAlerts)
        SettingKeys.SHOW_EXTRACTION_TOAST -> settings.copy(showExtractionToast = parsePreferenceBoolean(value) ?: settings.showExtractionToast)
        SettingKeys.AUTO_PREVIEW -> settings.copy(autoPreview = parsePreferenceBoolean(value) ?: settings.autoPreview)
        SettingKeys.STATS_AND_ACHIEVEMENTS -> settings.copy(statsAndAchievements = parsePreferenceBoolean(value) ?: settings.statsAndAchievements)
        SettingKeys.CRASH_RECOVERY -> settings.copy(crashRecovery = parsePreferenceBoolean(value) ?: settings.crashRecovery)
        SettingKeys.CATEGORIES_SECTION -> settings.copy(categoriesSection = parsePreferenceBoolean(value) ?: settings.categoriesSection)
        SettingKeys.FAVORITES_SECTION -> settings.copy(favoritesSection = parsePreferenceBoolean(value) ?: settings.favoritesSection)
        SettingKeys.VIEWING_HISTORY -> settings.copy(viewingHistory = parsePreferenceBoolean(value) ?: settings.viewingHistory)
        SettingKeys.SEARCH_HISTORY -> settings.copy(searchHistory = parsePreferenceBoolean(value) ?: settings.searchHistory)
        SettingKeys.VIDEO_ROW_DETAILS -> settings.copy(videoRowDetails = parsePreferenceBoolean(value) ?: settings.videoRowDetails)
        SettingKeys.THEME -> settings.copy(theme = normalizeThemeSetting(value))
        SettingKeys.PALETTE -> settings.copy(palette = normalized)

        SettingKeys.PLAYBACK_PREFERRED_RESOLUTION -> settings.copy(preferredResolution = normalized)
        SettingKeys.PLAYBACK_PREFERRED_FORMAT -> settings.copy(preferredFormat = normalized)
        SettingKeys.PLAYBACK_LOOP -> settings.copy(loopPlayback = parsePreferenceBoolean(value) ?: settings.loopPlayback)
        SettingKeys.PLAYBACK_PIP -> settings.copy(pictureInPicture = parsePreferenceBoolean(value) ?: settings.pictureInPicture)
        SettingKeys.PLAYBACK_SYSTEM_PLAYER -> settings.copy(useSystemPlayer = parsePreferenceBoolean(value) ?: settings.useSystemPlayer)
        SettingKeys.PLAYBACK_SKIP_DURATION -> settings.copy(skipDuration = normalizeSkipDurationSetting(value))
        SettingKeys.PLAYBACK_AUDIO_OUTPUT_NOTIFICATION -> settings.copy(audioOutputNotification = parsePreferenceBoolean(value) ?: settings.audioOutputNotification)
        SettingKeys.PLAYBACK_BLOCK_AUDIO_OUTPUT_CHANGES -> settings.copy(blockAudioOutputChanges = parsePreferenceBoolean(value) ?: settings.blockAudioOutputChanges)
        SettingKeys.PLAYBACK_AUDIO_NORMALIZATION -> settings.copy(audioNormalization = parsePreferenceBoolean(value) ?: settings.audioNormalization)

        SettingKeys.PRIVACY_SHOW_LOCK_SCREEN -> settings.copy(showLockScreen = parsePreferenceBoolean(value) ?: settings.showLockScreen)
        SettingKeys.PRIVACY_UNLOCK_FACE_ID -> settings.copy(unlockWithFaceId = parsePreferenceBoolean(value) ?: settings.unlockWithFaceId)
        SettingKeys.PRIVACY_BLUR_SCREEN_CAPTURE -> settings.copy(blurOnScreenCapture = parsePreferenceBoolean(value) ?: settings.blurOnScreenCapture)
        SettingKeys.PRIVACY_ENABLE_ANALYTICS -> settings.copy(enableAnalytics = parsePreferenceBoolean(value) ?: settings.enableAnalytics)
        SettingKeys.PRIVACY_ENABLE_CRASH_REPORTING -> settings.copy(enableCrashReporting = parsePreferenceBoolean(value) ?: settings.enableCrashReporting)

        SettingKeys.FOLLOWING_TAGS -> settings.copy(followedTags = decodeStringList(value))
        SettingKeys.FOLLOWING_UPLOADERS -> settings.copy(followedUploaders = decodeStringList(value))
        else -> settings
    }
}

internal fun encodeStringList(values: List<String>): String {
    return values
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(separator = "\n")
}

internal fun decodeStringList(value: String): List<String> {
    return value
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
