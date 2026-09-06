package com.minios.elizierdias.shell.mouse

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/**
 * Rato virtual — camada full-screen por cima de janelas e taskbar.
 *
 * - Arrastar: move o cursor
 * - Duplo toque: clique na posição do **cursor** (não do dedo)
 * - Mouse OFF: não desenha nem captura
 */
@Composable
fun VirtualMouse(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    sensitivity: Float = 1.15f,
) {
    if (!enabled) return

    val hostView = LocalView.current

    var cursor by remember { mutableStateOf(Offset.Zero) }
    var initialized by remember { mutableStateOf(false) }
    var originInWindow by remember { mutableStateOf(Offset.Zero) }
    var areaSize by remember { mutableStateOf(Offset.Zero) }

    var captureTouches by remember { mutableStateOf(true) }
    var pendingClick by remember { mutableStateOf(false) }

    fun dispatchClickAtCursor() {
        val target = hostView.rootView ?: hostView
        val x = originInWindow.x + cursor.x
        val y = originInWindow.y + cursor.y

        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0,
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(
            downTime,
            downTime + 40,
            MotionEvent.ACTION_UP,
            x,
            y,
            0,
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }

        try {
            target.dispatchTouchEvent(down)
            target.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    LaunchedEffect(pendingClick, captureTouches) {
        if (pendingClick && !captureTouches) {
            delay(16)
            dispatchClickAtCursor()
            delay(60)
            pendingClick = false
            captureTouches = true
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .zIndex(10_000f)
            .onGloballyPositioned { coords ->
                originInWindow = coords.positionInWindow()
                areaSize = Offset(coords.size.width.toFloat(), coords.size.height.toFloat())
                if (!initialized && coords.size.width > 0 && coords.size.height > 0) {
                    cursor = Offset(
                        coords.size.width / 2f,
                        coords.size.height / 2f,
                    )
                    initialized = true
                }
            }
            .then(
                if (captureTouches) {
                    Modifier
                        .pointerInput(sensitivity) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val next = cursor + dragAmount * sensitivity
                                cursor = Offset(
                                    next.x.coerceIn(0f, size.width.toFloat().coerceAtLeast(1f)),
                                    next.y.coerceIn(0f, size.height.toFloat().coerceAtLeast(1f)),
                                )
                                initialized = true
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (!initialized) {
                                        cursor = Offset(
                                            size.width / 2f,
                                            size.height / 2f,
                                        )
                                        initialized = true
                                    }
                                    pendingClick = true
                                    captureTouches = false
                                },
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        if (!initialized) return@Canvas

        val x = cursor.x
        val y = cursor.y

        // Cursor seta mais visível (por cima de tudo)
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y + 18.dp.toPx())
            lineTo(x + 5.dp.toPx(), y + 14.dp.toPx())
            lineTo(x + 10.dp.toPx(), y + 22.dp.toPx())
            lineTo(x + 13.dp.toPx(), y + 20.dp.toPx())
            lineTo(x + 7.dp.toPx(), y + 12.dp.toPx())
            lineTo(x + 14.dp.toPx(), y + 12.dp.toPx())
            close()
        }
        drawPath(path, Color.Black, style = Stroke(width = 3.dp.toPx()))
        drawPath(path, Color.White)
    }
}
