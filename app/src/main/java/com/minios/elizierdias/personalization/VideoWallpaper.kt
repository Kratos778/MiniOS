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
 * Proporção **FIT (contain)**:
 * - vídeo inteiro visível (não corta)
 * - dentro das bordas (não sai do ecrã)
 * - aspect ratio preservado (não estica)
 * - pode haver faixas laterais/superior se o ratio for diferente do ecrã
 *
 * Desempenho: buffer curto + limite 1280x720 / bitrate para telemóveis fracos.
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
            .setBufferDurationsMs(
                /* min */ 300,
                /* max */ 1_000,
                /* playback */ 150,
                /* rebuffer */ 300,
            )
            .setTargetBufferBytes(1 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                // Limitar resolução/bitrate — menos GPU/RAM em 2GB
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(1280, 720)
                    .setMaxVideoBitrate(2_500_000)
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
                // FIT = contain: cabe todo, sem cortar, sem esticar
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
                val bg = 0xFF0D1117.toInt()
                setBackgroundColor(bg)
                setShutterBackgroundColor(bg)

                player.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            requestLayout()
                        }
                    }
                })
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
