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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Rato virtual do MiniOS.
 *
 * - Arrastar: move o cursor (mecânica original, inalterada)
 * - Duplo toque em qualquer sítio do ecrã: clique **na posição do cursor**
 *   (não debaixo dos dedos)
 * - Mouse OFF: não intercepta — touch normal
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

    // true = captura drag/tap; false = deixa passar o clique injetado para baixo
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
            downTime + 50,
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

    // Depois de desligar a captura, espera 1 frame e injeta o clique no cursor
    LaunchedEffect(pendingClick, captureTouches) {
        if (pendingClick && !captureTouches) {
            delay(32)
            dispatchClickAtCursor()
            delay(80)
            pendingClick = false
            captureTouches = true
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
                if (captureTouches) {
                    Modifier
                        // Movimento — igual ao que já tinhas
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
                        // Só duplo toque → clique na posição do cursor
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
                                    // Liberta a captura para o evento injetado chegar às apps
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
