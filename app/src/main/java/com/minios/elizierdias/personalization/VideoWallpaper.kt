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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * Wallpaper de vídeo em loop.
 *
 * Modo **ZOOM (cover)** — o que o utilizador pediu sem barras pretas:
 * - preenche 100% do ecrã (sem faixas)
 * - não estica / não distorce
 * - corta só o excesso nas bordas (inevitável se o ratio do vídeo ≠ ecrã)
 *
 * Ecrã tipico NoskOS: 1640x720 (~2.28:1). A maioria dos vídeos é 16:9;
 * por isso ZOOM corta um pouco em cima/baixo. É o trade-off correcto.
 *
 * Desempenho (4GB RAM): buffer moderado, max 1640x720 / 4 Mbps.
 */
@Composable
fun VideoWallpaper(
    source: String,
    modifier: Modifier = Modifier,
    soundEnabled: Boolean = false,
    contentKey: Long = 0L,
) {
    val context = LocalContext.current

    val mediaUri = remember(source, contentKey) {
        when {
            source.startsWith("content://") -> Uri.parse(source)
            source.startsWith("/") -> Uri.fromFile(File(source))
            source.startsWith("file://") -> Uri.parse(source)
            else -> null
        }
    }

    if (mediaUri == null) return

    val player = remember(source, contentKey) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(400, 1_200, 200, 400)
            .setTargetBufferBytes(2 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(1640, 720)
                    .setMaxVideoBitrate(4_000_000)
                    .build()

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

    DisposableEffect(source, contentKey) {
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
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false
                // ZOOM = cover: ecrã cheio, sem barras, sem esticar
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
                val bg = 0xFF0D1117.toInt()
                setBackgroundColor(bg)
                setShutterBackgroundColor(bg)

                player.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            requestLayout()
                        }
                    }
                })
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
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
