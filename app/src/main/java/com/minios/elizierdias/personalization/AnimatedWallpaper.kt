package com.minios.elizierdias.personalization

import android.graphics.Movie
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun AnimatedWallpaper(
    source: String,
    modifier: Modifier = Modifier,
    contentKey: Long = 0L,
) {
    val context = LocalContext.current

    var movie by remember(source, contentKey) {
        mutableStateOf<Movie?>(null)
    }

    var animationTime by remember(source, contentKey) {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(source, contentKey) {
        movie = null

        try {
            val bytes = when {
                source.startsWith("/") -> {
                    File(source).readBytes()
                }

                source.startsWith("content://") -> {
                    context.contentResolver
                        .openInputStream(android.net.Uri.parse(source))
                        ?.use { it.readBytes() }
                }

                else -> null
            }

            if (bytes != null) {
                movie = Movie.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            movie = null
        }
    }

    LaunchedEffect(movie) {
        if (movie == null) return@LaunchedEffect

        while (true) {
            animationTime = System.currentTimeMillis()
            delay(16L)
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize(),
    ) {
        val currentMovie = movie ?: return@Canvas

        val duration = currentMovie.duration().takeIf { it > 0 } ?: 1000
        val elapsed = animationTime % duration
        currentMovie.setTime(elapsed.toInt())

        val sourceWidth = currentMovie.width().toFloat()
        val sourceHeight = currentMovie.height().toFloat()

        if (sourceWidth <= 0f || sourceHeight <= 0f || size.width <= 0f || size.height <= 0f) {
            return@Canvas
        }

        val scale = maxOf(size.width / sourceWidth, size.height / sourceHeight)
        val drawWidth = sourceWidth * scale
        val drawHeight = sourceHeight * scale
        val offsetX = (size.width - drawWidth) / 2f
        val offsetY = (size.height - drawHeight) / 2f

        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.clipRect(0f, 0f, size.width, size.height)
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        currentMovie.draw(canvas, 0f, 0f)
        canvas.restore()
    }
}
