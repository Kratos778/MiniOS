package com.minios.elizierdias.personalization

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * Wallpaper de vídeo em loop (ContentScale.Crop via RESIZE_MODE_ZOOM).
 * Buffers pequenos — wallpapers curtos em loop não precisam de fila grande.
 */
@Composable
fun VideoWallpaper(
    source: String,
    modifier: Modifier = Modifier,
    soundEnabled: Boolean = false,
) {
    val context = LocalContext.current

    val mediaUri = remember(source) {
        when {
            source.startsWith("content://") -> Uri.parse(source)
            source.startsWith("/") -> Uri.fromFile(File(source))
            source.startsWith("file://") -> Uri.parse(source)
            else -> null
        }
    }

    if (mediaUri == null) return

    val player = remember(source) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1_000,
                /* maxBufferMs = */ 3_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000,
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(mediaUri))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (soundEnabled) 1f else 0f
                playWhenReady = true
                prepare()
            }
    }

    LaunchedEffect(soundEnabled) {
        try {
            player.volume = if (soundEnabled) 1f else 0f
        } catch (_: Exception) {
        }
    }

    DisposableEffect(source) {
        onDispose {
            try {
                player.stop()
            } catch (_: Exception) {
            }
            try {
                player.release()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
                setBackgroundColor(0xFF0D1117.toInt())
                setShutterBackgroundColor(0xFF0D1117.toInt())
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
            try {
                player.volume = if (soundEnabled) 1f else 0f
            } catch (_: Exception) {
            }
            if (!player.isPlaying && player.playbackState == Player.STATE_READY) {
                player.play()
            }
        },
    )
}
