package com.minios.elizierdias.shell.mouse

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/**
 * Rato virtual do MiniOS.
 *
 * - Arrastar: move o cursor (mecânica original)
 * - Toque simples: clique na **posição do cursor**
 * - Duplo toque: duplo clique na posição do cursor
 * - Mouse OFF: não intercepta — touch normal nos dedos
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
    var intercepting by remember { mutableStateOf(true) }

    fun injectClick(at: Offset, count: Int = 1) {
        val target = hostView.rootView ?: hostView
        val x = originInWindow.x + at.x
        val y = originInWindow.y + at.y

        intercepting = false

        fun fireOne(then: (() -> Unit)? = null) {
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                x,
                y,
                0,
            )
            val up = MotionEvent.obtain(
                downTime,
                downTime + 40,
                MotionEvent.ACTION_UP,
                x,
                y,
                0,
            )
            try {
                target.dispatchTouchEvent(down)
                target.dispatchTouchEvent(up)
            } finally {
                down.recycle()
                up.recycle()
            }
            then?.invoke()
        }

        target.post {
            if (count <= 1) {
                fireOne {
                    target.post { intercepting = true }
                }
            } else {
                fireOne {
                    target.postDelayed({
                        fireOne {
                            target.post { intercepting = true }
                        }
                    }, 90L)
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                originInWindow = coords.positionInWindow()
                if (!initialized && coords.size.width > 0 && coords.size.height > 0) {
                    cursor = Offset(
                        coords.size.width / 2f,
                        coords.size.height / 2f,
                    )
                    initialized = true
                }
            }
            .then(
                if (intercepting) {
                    Modifier
                        .pointerInput(sensitivity) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val next = cursor + dragAmount * sensitivity
                                cursor = Offset(
                                    next.x.coerceIn(0f, size.width.toFloat()),
                                    next.y.coerceIn(0f, size.height.toFloat()),
                                )
                                initialized = true
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (!initialized) {
                                        cursor = Offset(size.width / 2f, size.height / 2f)
                                        initialized = true
                                    }
                                    injectClick(cursor, count = 1)
                                },
                                onDoubleTap = {
                                    if (!initialized) {
                                        cursor = Offset(size.width / 2f, size.height / 2f)
                                        initialized = true
                                    }
                                    injectClick(cursor, count = 2)
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

        val line = 2.dp.toPx()
        val length = 13.dp.toPx()

        drawLine(Color.White, Offset(x, y), Offset(x, y + length), line)
        drawLine(
            Color.White,
            Offset(x, y),
            Offset(x + length * 0.75f, y + length * 0.75f),
            line,
        )
        drawLine(
            Color.Black,
            Offset(x + 1.dp.toPx(), y + 2.dp.toPx()),
            Offset(x + 1.dp.toPx(), y + length + 1.dp.toPx()),
            line / 2,
        )
    }
}
