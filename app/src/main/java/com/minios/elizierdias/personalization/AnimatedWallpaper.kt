package com.minios.elizierdias.personalization

import android.graphics.Movie
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun AnimatedWallpaper(
    source: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var movie by remember(source) {
        mutableStateOf<Movie?>(null)
    }

    var startTime by remember(source) {
        mutableLongStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(source) {
        movie = null
        startTime = System.currentTimeMillis()

        try {
            val bytes = when {
                source.startsWith("/") -> {
                    File(source).readBytes()
                }

                source.startsWith("content://") -> {
                    context.contentResolver
                        .openInputStream(
                            android.net.Uri.parse(source)
                        )
                        ?.use { it.readBytes() }
                }

                source.startsWith("http://") ||
                source.startsWith("https://") -> {
                    val connection =
                        URL(source)
                            .openConnection() as HttpURLConnection

                    connection.connectTimeout = 10_000
                    connection.readTimeout = 15_000
                    connection.connect()

                    connection.inputStream.use {
                        it.readBytes()
                    }
                }

                else -> null
            }

            if (bytes != null) {
                movie = Movie.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                )
            }
        } catch (_: Exception) {
            movie = null
        }
    }

    if (movie == null) {
        return
    }

    Canvas(
        modifier = modifier.fillMaxSize(),
    ) {
        val currentMovie = movie ?: return@Canvas

        val duration =
            currentMovie.duration()
                .takeIf { it > 0 }
                ?: 1000

        val elapsed =
            (
                System.currentTimeMillis() -
                    startTime
            ).toInt()

        val frameTime =
            elapsed % duration

        currentMovie.setTime(frameTime)

        val movieWidth =
            currentMovie.width().toFloat()

        val movieHeight =
            currentMovie.height().toFloat()

        if (
            movieWidth <= 0f ||
            movieHeight <= 0f
        ) {
            return@Canvas
        }

        val scale =
            maxOf(
                size.width / movieWidth,
                size.height / movieHeight,
            )

        val scaledWidth =
            movieWidth * scale

        val scaledHeight =
            movieHeight * scale

        val left =
            (size.width - scaledWidth) / 2f

        val top =
            (size.height - scaledHeight) / 2f

        withTransform(
            transformBlock = {
                translate(
                    left,
                    top,
                )
                scale(
                    scale,
                    scale,
                )
            },
        ) {
            currentMovie.draw(
                drawContext.canvas.nativeCanvas,
                0f,
                0f,
            )
        }

        // Força a próxima atualização do frame.
        invalidateAnimation()
    }
}

private suspend fun invalidateAnimation() {
    delay(16L)
}
