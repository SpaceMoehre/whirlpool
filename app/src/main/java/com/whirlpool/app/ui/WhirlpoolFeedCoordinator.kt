package com.whirlpool.app.ui

import com.whirlpool.app.data.EngineRepository
import com.whirlpool.app.data.SourceServerConfig
import com.whirlpool.engine.StatusChannel
import com.whirlpool.engine.StatusFilterOption
import com.whirlpool.engine.StatusSummary
import com.whirlpool.engine.VideoItem
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class WhirlpoolFeedCoordinator(
    private val repository: EngineRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun refresh(current: WhirlpoolUiState, pageLimit: UInt): WhirlpoolUiState = withContext(ioDispatcher) {
        val status = repository.status()
        val channels = normalizeChannels(status)
        val selectedChannel = channels.firstOrNull { channel ->
            channel.id == current.activeChannel
        } ?: channels.firstOrNull()
        val nextChannelId = selectedChannel?.id ?: current.activeChannel
        val normalizedFilters = normalizeFilterSelection(
            channel = selectedChannel,
            currentSelection = current.selectedFilters,
        )
        val videos = repository.discover(
            query = current.query,
            page = 1u,
            limit = pageLimit,
            channelId = nextChannelId,
            filters = normalizedFilters,
        )
        val favorites = repository.favoriteVideos()
        val downloadedVideoKeys = collectDownloadedVideoKeys(
            videos = videos + favorites,
            fallbackChannelId = nextChannelId,
        )
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
            favorites = favorites,
            categories = status.sources,
            channelDetails = channels,
            availableChannels = availableChannels,
            activeChannel = nextChannelId,
            selectedFilters = normalizedFilters,
            currentPage = 1u,
            hasMorePages = videos.size >= pageLimit.toInt(),
            isLoadingNextPage = false,
            sourceServers = sources,
            activeServerBaseUrl = activeBaseUrl,
            downloadedVideoKeys = downloadedVideoKeys,
            actionText = null,
            errorText = null,
        )
    }

    suspend fun loadNextPage(
        current: WhirlpoolUiState,
        nextPage: UInt,
        pageLimit: UInt,
    ): FeedPageResult = withContext(ioDispatcher) {
        val discovered = repository.discover(
            query = current.query,
            page = nextPage,
            limit = pageLimit,
            channelId = current.activeChannel,
            filters = current.selectedFilters,
        )
        val downloadedVideoKeys = collectDownloadedVideoKeys(
            videos = discovered,
            fallbackChannelId = current.activeChannel,
        )
        FeedPageResult(
            discovered = discovered,
            downloadedVideoKeys = downloadedVideoKeys,
        )
    }

    suspend fun switchSource(serverBaseUrl: String): SourceSwitchResult = withContext(ioDispatcher) {
        val currentActive = repository.activeApiBaseUrl()
        if (serverBaseUrl.isNotBlank() && serverBaseUrl != currentActive) {
            val changed = repository.setActiveSource(serverBaseUrl)
            if (!changed) {
                throw IOException("Unable to switch source.")
            }
        }
        SourceSwitchResult(
            sources = repository.listSourceServers(),
            activeBaseUrl = repository.activeApiBaseUrl(),
        )
    }

    suspend fun toggleFavorite(
        video: VideoItem,
        currentVideos: List<VideoItem>,
        fallbackChannelId: String,
    ): FavoritesUpdateResult = withContext(ioDispatcher) {
        val favoriteIds = repository.favorites().map { it.videoId }.toSet()
        if (video.id in favoriteIds) {
            repository.removeFavorite(video.id)
        } else {
            repository.addFavorite(video)
        }
        val favorites = repository.favoriteVideos()
        val downloadedVideoKeys = collectDownloadedVideoKeys(
            videos = currentVideos + favorites,
            fallbackChannelId = fallbackChannelId,
        )
        FavoritesUpdateResult(
            favorites = favorites,
            downloadedVideoKeys = downloadedVideoKeys,
        )
    }

    private fun collectDownloadedVideoKeys(
        videos: List<VideoItem>,
        fallbackChannelId: String,
    ): Set<String> {
        return DownloadedVideoIndex.collectDownloadedKeys(
            videos = videos,
            fallbackChannelId = fallbackChannelId,
        ) { channelId, videoId ->
            repository.downloadedVideoPath(
                channelId = channelId,
                videoId = videoId,
            ) != null
        }
    }

    private fun normalizeChannels(status: StatusSummary): List<StatusChannel> {
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
}

internal data class FeedPageResult(
    val discovered: List<VideoItem>,
    val downloadedVideoKeys: Set<String>,
)

internal data class FavoritesUpdateResult(
    val favorites: List<VideoItem>,
    val downloadedVideoKeys: Set<String>,
)

internal data class SourceSwitchResult(
    val sources: List<SourceServerConfig>,
    val activeBaseUrl: String,
)
