package com.minios.elizierdias.shell.mouse

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
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
 * - Duplo toque: duplo clique na posição do cursor (abrir apps, selecionar, etc.)
 * - Mouse OFF: este composable não desenha nem intercepta — touch normal
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

        // Liberta o overlay um instante para o evento chegar às views por baixo
        intercepting = false
        target.post {
            try {
                repeat(count) { i ->
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
                    target.dispatchTouchEvent(down)
                    target.dispatchTouchEvent(up)
                    down.recycle()
                    up.recycle()
                    if (i < count - 1) {
                        try {
                            Thread.sleep(80)
                        } catch (_: InterruptedException) {
                        }
                    }
                }
            } finally {
                target.post {
                    intercepting = true
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                originInWindow = coords.positionInWindow()
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
                                    // Clique onde está o cursor (não move o cursor para o dedo)
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
        if (!initialized) {
            // Centro do desktop na primeira utilização
            return@Canvas
        }

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
