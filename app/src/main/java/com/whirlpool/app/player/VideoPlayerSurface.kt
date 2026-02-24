package com.whirlpool.app.player

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView

private const val DefaultUserAgent =
    "Mozilla/5.0 (Linux; Android 14; Whirlpool) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

@Composable
fun VideoPlayerSurface(
    streamUrl: String?,
    requestHeaders: Map<String, String>,
    onPlaybackError: (String) -> Unit,
    onPlaybackEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDisposing = remember { mutableStateOf(false) }
    val player = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    LaunchedEffect(streamUrl, requestHeaders) {
        val url = streamUrl ?: return@LaunchedEffect
        isDisposing.value = false
        val userAgent = requestHeaders.entries
            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value
            ?.ifBlank { null }
            ?: DefaultUserAgent

        val defaultHeaders = requestHeaders
            .filterKeys { !it.equals("User-Agent", ignoreCase = true) }
            .filterValues { it.isNotBlank() }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
        if (defaultHeaders.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(defaultHeaders)
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

        player.stop()
        player.clearMediaItems()
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
        onPlaybackEvent("Preparing stream (${defaultHeaders.size} headers).")
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> onPlaybackEvent("Buffering...")
                    Player.STATE_READY -> onPlaybackEvent("Playback started.")
                    Player.STATE_ENDED -> onPlaybackEvent("Playback ended.")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (isDisposing.value) {
                    return
                }
                val detail = buildString {
                    append(error.errorCodeName)
                    append(": ")
                    append(error.message ?: "unknown error")
                    val causeMessage = error.cause?.message
                    if (!causeMessage.isNullOrBlank()) {
                        append(" | cause=")
                        append(causeMessage)
                    }
                    if (error.cause is HttpDataSource.InvalidResponseCodeException) {
                        val code = (error.cause as HttpDataSource.InvalidResponseCodeException).responseCode
                        append(" | http=")
                        append(code)
                    }
                }
                onPlaybackError("Playback failed: $detail")
            }
        }
        player.addListener(listener)
        onDispose {
            isDisposing.value = true
            player.removeListener(listener)
            player.playWhenReady = false
            player.stop()
            player.clearMediaItems()
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                controllerAutoShow = false
                this.player = player
            }
        },
        update = { playerView ->
            playerView.player = player
        },
    )
}
