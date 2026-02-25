package com.whirlpool.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whirlpool.app.data.EngineRepository
import com.whirlpool.engine.StatusChannel
import com.whirlpool.engine.StatusChoice
import com.whirlpool.engine.StatusFilterOption
import com.whirlpool.engine.VideoItem
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

data class WhirlpoolUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val videos: List<VideoItem> = emptyList(),
    val favorites: List<VideoItem> = emptyList(),
    val categories: List<String> = emptyList(),
    val channelDetails: List<StatusChannel> = emptyList(),
    val activeChannel: String = "catflix",
    val selectedFilters: Map<String, String> = emptyMap(),
    val selectedVideo: VideoItem? = null,
    val streamUrl: String? = null,
    val streamHeaders: Map<String, String> = emptyMap(),
    val statusText: String = "Whirlpool",
    val actionText: String? = null,
    val errorText: String? = null,
    val logs: List<String> = emptyList(),
)

class WhirlpoolViewModel(
    private val repository: EngineRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WhirlpoolUiState())
    val uiState: StateFlow<WhirlpoolUiState> = mutableState.asStateFlow()
    private var querySearchDebounceJob: Job? = null

    init {
        log("App initialized.")
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
                        limit = 10u,
                        channelId = nextChannelId,
                        filters = normalizedFilters,
                    )
                    val videos = discovered
                    val favoriteIds = repository.favorites().map { it.videoId }.toSet()
                    val favoriteVideos = videos.filter { it.id in favoriteIds }

                    current.copy(
                        isLoading = false,
                        statusText = if (status.name.isBlank()) "Whirlpool" else status.name,
                        videos = videos,
                        favorites = favoriteVideos,
                        categories = status.sources,
                        channelDetails = channels,
                        activeChannel = nextChannelId,
                        selectedFilters = normalizedFilters,
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
                    errorText = throwable.message ?: "Unable to load live feed from server.",
                )
                log("Feed load failed: ${throwable.message ?: "unknown error"}")
            }
        }
    }

    fun search() {
        cancelDebouncedSearch()
        refreshAll(showLoading = true)
    }

    fun onChannelSelected(channelId: String) {
        val current = mutableState.value
        val selectedChannel = current.channelDetails.firstOrNull { channel -> channel.id == channelId }
        val normalizedFilters = normalizeFilterSelection(selectedChannel, current.selectedFilters)
        mutableState.value = current.copy(
            activeChannel = channelId,
            selectedFilters = normalizedFilters,
        )
        refreshVideosInBackground()
    }

    fun onFilterSelected(optionId: String, choiceId: String) {
        val current = mutableState.value
        val selectedChannel = current.channelDetails.firstOrNull { channel ->
            channel.id == current.activeChannel
        } ?: return
        val validChoice = selectedChannel
            .options
            .firstOrNull { option -> option.id == optionId }
            ?.choices
            ?.any { choice -> choice.id == choiceId }
            ?: false
        if (!validChoice) {
            return
        }

        mutableState.value = current.copy(
            selectedFilters = current.selectedFilters + (optionId to choiceId),
        )
        refreshVideosInBackground()
    }

    fun playVideo(video: VideoItem) {
        log("Playback requested for: ${video.title}")
        log("yt-dlp state: ${repository.ytDlpState()}")
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolved = repository.resolve(video.pageUrl)
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
                    repository.favorites().map { favorite ->
                        mutableState.value.videos.firstOrNull { it.id == favorite.videoId }
                    }.filterNotNull()
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

    fun clearLogs() {
        mutableState.value = mutableState.value.copy(logs = emptyList())
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
                options = emptyList(),
            )
        }
    }

    private fun normalizeFilterSelection(
        channel: StatusChannel?,
        currentSelection: Map<String, String>,
    ): Map<String, String> {
        val channelOptions = channel?.options.orEmpty()
        if (channelOptions.isEmpty()) {
            return emptyMap()
        }

        val out = linkedMapOf<String, String>()
        channelOptions.forEach { option ->
            val chosen = pickChoice(option, currentSelection[option.id])
            if (chosen != null) {
                out[option.id] = chosen.id
            }
        }
        return out
    }

    private fun pickChoice(
        option: StatusFilterOption,
        selectedChoiceId: String?,
    ): StatusChoice? {
        if (option.choices.isEmpty()) {
            return null
        }
        return option.choices.firstOrNull { choice -> choice.id == selectedChoiceId }
            ?: option.choices.first()
    }

    companion object {
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
