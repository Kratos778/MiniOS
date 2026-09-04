package com.minios.elizierdias.personalization

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * Wallpaper de vídeo em loop com a **mesma mecânica** de GIF / JPG / PNG:
 * ContentScale.Crop — preenche o ecrã, centrado, corta o excesso, sem barras.
 *
 * @param soundEnabled se true, reproduz áudio; se false, volume 0.
 */
@Composable
fun VideoWallpaper(
    source: String,
    modifier: Modifier = Modifier,
    soundEnabled: Boolean = false,
) {
    val context = LocalContext.current

    val mediaPlayer = remember(source) { MediaPlayer() }

    DisposableEffect(soundEnabled) {
        try {
            if (soundEnabled) {
                mediaPlayer.setVolume(1f, 1f)
            } else {
                mediaPlayer.setVolume(0f, 0f)
            }
        } catch (_: Exception) {
        }
        onDispose { }
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
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                clipChildren = true
                clipToPadding = true
                setBackgroundColor(0xFF0D1117.toInt())

                val textureView = TextureView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    )
                }
                addView(textureView)

                fun applyCover(videoW: Int, videoH: Int) {
                    if (videoW <= 0 || videoH <= 0) return
                    val viewW = width
                    val viewH = height
                    if (viewW <= 0 || viewH <= 0) return

                    val scale = maxOf(
                        viewW.toFloat() / videoW,
                        viewH.toFloat() / videoH,
                    )
                    val drawW = (videoW * scale).toInt().coerceAtLeast(1)
                    val drawH = (videoH * scale).toInt().coerceAtLeast(1)

                    textureView.layoutParams = FrameLayout.LayoutParams(drawW, drawH, Gravity.CENTER)
                    textureView.requestLayout()
                }

                fun applyVolume() {
                    try {
                        if (soundEnabled) {
                            mediaPlayer.setVolume(1f, 1f)
                        } else {
                            mediaPlayer.setVolume(0f, 0f)
                        }
                    } catch (_: Exception) {
                    }
                }

                fun bindDataSource() {
                    when {
                        source.startsWith("content://") -> {
                            mediaPlayer.setDataSource(ctx, Uri.parse(source))
                        }
                        source.startsWith("/") -> {
                            val file = File(source)
                            if (!file.exists() || file.length() == 0L) {
                                throw IllegalStateException("Ficheiro de vídeo em falta")
                            }
                            mediaPlayer.setDataSource(file.absolutePath)
                        }
                        else -> throw IllegalArgumentException("Fonte inválida")
                    }
                }

                textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        try {
                            mediaPlayer.reset()
                            bindDataSource()
                            mediaPlayer.setSurface(Surface(surface))
                            mediaPlayer.isLooping = true
                            applyVolume()
                            mediaPlayer.setOnVideoSizeChangedListener { _, vw, vh ->
                                post { applyCover(vw, vh) }
                            }
                            mediaPlayer.setOnPreparedListener { mp ->
                                post { applyCover(mp.videoWidth, mp.videoHeight) }
                                applyVolume()
                                try {
                                    mp.start()
                                } catch (_: Exception) {
                                }
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
                            post {
                                applyCover(mediaPlayer.videoWidth, mediaPlayer.videoHeight)
                            }
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

                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    try {
                        if (mediaPlayer.videoWidth > 0 && mediaPlayer.videoHeight > 0) {
                            applyCover(mediaPlayer.videoWidth, mediaPlayer.videoHeight)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        },
        update = { view -> view },
    )
}
