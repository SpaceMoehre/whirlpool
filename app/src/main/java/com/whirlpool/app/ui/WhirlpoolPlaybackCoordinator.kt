package com.whirlpool.app.ui

import com.whirlpool.app.data.EngineRepository
import com.whirlpool.app.data.PlaybackResolution
import com.whirlpool.app.data.VideoDownloadResult
import com.whirlpool.engine.StatusChannel
import com.whirlpool.engine.VideoItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class WhirlpoolPlaybackCoordinator(
    private val repository: EngineRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun selectedChannelYtdlpCommand(
        channelDetails: List<StatusChannel>,
        activeChannel: String,
    ): String? {
        return channelDetails.firstOrNull { channel ->
            channel.id == activeChannel
        }
            ?.ytdlpCommand
            ?.takeIf { command -> command.isNotBlank() }
    }

    suspend fun resolvePlayback(
        video: VideoItem,
        activeChannel: String,
        ytdlpCommand: String?,
    ): PlaybackResolveResult = withContext(ioDispatcher) {
        val channelId = DownloadedVideoIndex.resolveChannelId(
            video = video,
            fallbackChannelId = activeChannel,
        )
        val downloadedPath = repository.downloadedVideoPath(
            channelId = channelId,
            videoId = video.id,
        )
        val launch = if (downloadedPath != null) {
            PlaybackLaunchResult.Local(downloadedPath)
        } else {
            PlaybackLaunchResult.Remote(
                repository.resolve(
                    pageUrl = video.pageUrl,
                    ytdlpCommand = ytdlpCommand,
                ),
            )
        }
        PlaybackResolveResult(
            channelId = channelId,
            launch = launch,
        )
    }

    suspend fun download(
        video: VideoItem,
        activeChannel: String,
        ytdlpCommand: String?,
    ): DownloadExecutionResult = withContext(ioDispatcher) {
        val channelId = DownloadedVideoIndex.resolveChannelId(
            video = video,
            fallbackChannelId = activeChannel,
        )
        val downloaded = repository.downloadVideo(
            pageUrl = video.pageUrl,
            title = video.title,
            channelId = channelId,
            videoId = video.id,
            ytdlpCommand = ytdlpCommand,
        )
        DownloadExecutionResult(
            channelId = channelId,
            downloaded = downloaded,
        )
    }
}

internal data class PlaybackResolveResult(
    val channelId: String,
    val launch: PlaybackLaunchResult,
)

internal sealed interface PlaybackLaunchResult {
    data class Local(val path: String) : PlaybackLaunchResult
    data class Remote(val resolution: PlaybackResolution) : PlaybackLaunchResult
}

internal data class DownloadExecutionResult(
    val channelId: String,
    val downloaded: VideoDownloadResult,
)
