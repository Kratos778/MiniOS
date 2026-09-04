package com.minios.elizierdias.personalization

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * Wallpaper de vídeo em loop, com escala **cover** (como ContentScale.Crop):
 * preenche o ecrã todo, mantém proporção e corta o excesso.
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

    val mediaPlayer = remember(source) {
        MediaPlayer()
    }

    DisposableEffect(source) {
        onDispose {
            try {
                mediaPlayer.stop()
            } catch (_: Exception) {
            }
            try {
                mediaPlayer.reset()
            } catch (_: Exception) {
            }
            try {
                mediaPlayer.release()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            TextureView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )

                surfaceTextureListener = object : TextureView.SurfaceTextureListener {

                    private fun applyCenterCrop(view: TextureView, videoW: Int, videoH: Int) {
                        if (videoW <= 0 || videoH <= 0) return
                        val viewW = view.width.toFloat()
                        val viewH = view.height.toFloat()
                        if (viewW <= 0f || viewH <= 0f) return

                        val scale = maxOf(viewW / videoW, viewH / videoH)
                        val scaledW = videoW * scale
                        val scaledH = videoH * scale
                        val dx = (viewW - scaledW) / 2f
                        val dy = (viewH - scaledH) / 2f

                        val matrix = Matrix()
                        matrix.setScale(scale, scale)
                        matrix.postTranslate(dx, dy)
                        view.setTransform(matrix)
                    }

                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        try {
                            mediaPlayer.reset()
                            mediaPlayer.setDataSource(ctx, uri)
                            mediaPlayer.setSurface(Surface(surface))
                            mediaPlayer.isLooping = true
                            mediaPlayer.setVolume(0f, 0f)
                            mediaPlayer.setOnVideoSizeChangedListener { _, vw, vh ->
                                applyCenterCrop(this@apply, vw, vh)
                            }
                            mediaPlayer.setOnPreparedListener { mp ->
                                applyCenterCrop(
                                    this@apply,
                                    mp.videoWidth,
                                    mp.videoHeight,
                                )
                                mp.start()
                            }
                            mediaPlayer.setOnErrorListener { _, _, _ -> true }
                            mediaPlayer.prepareAsync()
                        } catch (_: Exception) {
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        try {
                            applyCenterCrop(
                                this@apply,
                                mediaPlayer.videoWidth,
                                mediaPlayer.videoHeight,
                            )
                        } catch (_: Exception) {
                        }
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        try {
                            mediaPlayer.setSurface(null)
                        } catch (_: Exception) {
                        }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        },
        update = { /* URI fixo via remember(source) */ },
    )
}
