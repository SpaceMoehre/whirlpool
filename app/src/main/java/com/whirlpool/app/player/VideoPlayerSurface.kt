package com.whirlpool.app.player

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun VideoPlayerSurface(
    streamUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    LaunchedEffect(streamUrl) {
        val url = streamUrl ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(Unit) {
        onDispose {
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
