package com.minios.elizierdias.personalization

import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * Wallpaper de vídeo em loop (estilo live wallpaper simples).
 * Aceita caminho de ficheiro local ou content:// URI.
 */
@Composable
fun VideoWallpaper(
    source: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val uri = remember(source) {
        when {
            source.startsWith("content://") -> Uri.parse(source)
            source.startsWith("/") -> Uri.fromFile(File(source))
            else -> null
        }
    }

    if (uri == null) return

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            VideoView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    // Silencioso por defeito no wallpaper
                    try {
                        mp.setVolume(0f, 0f)
                    } catch (_: Exception) {
                    }
                    start()
                }
                setOnErrorListener { _, _, _ ->
                    true
                }
            }
        },
        update = { videoView ->
            if (videoView.tag != source) {
                videoView.tag = source
                videoView.setVideoURI(uri)
                videoView.start()
            }
        },
    )

    DisposableEffect(source) {
        onDispose { }
    }
}
