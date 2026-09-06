package com.minios.elizierdias.shell.mouse

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/**
 * Rato virtual — camada full-screen por cima de janelas e taskbar.
 *
 * - Arrastar dedo: move o cursor
 * - Duplo toque: clique (DOWN+UP) na posição do cursor
 * - Toque longo + arrastar: botão pressionado (mover janela pela barra de título)
 * - Mouse OFF: não intercepta
 */
@Composable
fun VirtualMouse(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    sensitivity: Float = 1.2f,
) {
    if (!enabled) return

    val hostView = LocalView.current

    var cursor by remember { mutableStateOf(Offset.Zero) }
    var initialized by remember { mutableStateOf(false) }
    var originInWindow by remember { mutableStateOf(Offset.Zero) }

    // false = deixa passar eventos injetados
    var captureTouches by remember { mutableStateOf(true) }
    var pendingClick by remember { mutableStateOf(false) }

    fun absX(): Float = originInWindow.x + cursor.x
    fun absY(): Float = originInWindow.y + cursor.y

    fun obtain(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long): MotionEvent {
        return MotionEvent.obtain(downTime, eventTime, action, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
    }

    fun dispatch(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long = downTime) {
        val target = hostView.rootView ?: hostView
        val ev = obtain(action, x, y, downTime, eventTime)
        try {
            target.dispatchTouchEvent(ev)
        } finally {
            ev.recycle()
        }
    }

    fun clickAtCursor() {
        val x = absX()
        val y = absY()
        val t = SystemClock.uptimeMillis()
        dispatch(MotionEvent.ACTION_DOWN, x, y, t)
        dispatch(MotionEvent.ACTION_UP, x, y, t, t + 40)
    }

    LaunchedEffect(pendingClick, captureTouches) {
        if (pendingClick && !captureTouches) {
            delay(16)
            clickAtCursor()
            delay(50)
            pendingClick = false
            captureTouches = true
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100_000f)
            .onGloballyPositioned { coords ->
                originInWindow = coords.positionInWindow()
                if (!initialized && coords.size.width > 0 && coords.size.height > 0) {
                    cursor = Offset(coords.size.width / 2f, coords.size.height / 2f)
                    initialized = true
                }
            }
            .then(
                if (captureTouches) {
                    Modifier.pointerInput(sensitivity) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()

                            if (!initialized) {
                                cursor = Offset(size.width / 2f, size.height / 2f)
                                initialized = true
                            }

                            val startTime = SystemClock.uptimeMillis()
                            var moved = false
                            var longPress = false
                            var buttonDownTime = 0L
                            var holding = false

                            // Espera: long-press (~350ms sem mover muito) ou arrasto
                            while (true) {
                                val event = withTimeoutOrNull(350L) {
                                    awaitPointerEvent()
                                }
                                if (event == null) {
                                    // timeout → long press = botão do rato pressionado
                                    if (!moved) {
                                        longPress = true
                                        holding = true
                                        buttonDownTime = SystemClock.uptimeMillis()
                                        captureTouches = false
                                        dispatch(
                                            MotionEvent.ACTION_DOWN,
                                            absX(),
                                            absY(),
                                            buttonDownTime,
                                        )
                                        captureTouches = true
                                    }
                                    break
                                }
                                val change = event.changes.firstOrNull() ?: continue
                                if (!change.pressed) {
                                    // soltou rápido → possível clique / double-tap tratado abaixo
                                    break
                                }
                                val delta = change.positionChange()
                                if (delta.getDistance() > 6f) {
                                    moved = true
                                    change.consume()
                                    val next = cursor + delta * sensitivity
                                    cursor = Offset(
                                        next.x.coerceIn(0f, size.width.toFloat()),
                                        next.y.coerceIn(0f, size.height.toFloat()),
                                    )
                                    break
                                }
                            }

                            // Continuar arrasto (mover cursor; se holding, injetar MOVE)
                            val pointerId = down.id
                            drag(pointerId) { change ->
                                change.consume()
                                val delta = change.positionChange()
                                val next = cursor + delta * sensitivity
                                cursor = Offset(
                                    next.x.coerceIn(0f, size.width.toFloat()),
                                    next.y.coerceIn(0f, size.height.toFloat()),
                                )
                                if (holding) {
                                    captureTouches = false
                                    dispatch(
                                        MotionEvent.ACTION_MOVE,
                                        absX(),
                                        absY(),
                                        buttonDownTime,
                                        SystemClock.uptimeMillis(),
                                    )
                                    captureTouches = true
                                }
                            }

                            if (holding) {
                                captureTouches = false
                                dispatch(
                                    MotionEvent.ACTION_UP,
                                    absX(),
                                    absY(),
                                    buttonDownTime,
                                    SystemClock.uptimeMillis(),
                                )
                                captureTouches = true
                            } else if (!moved && !longPress) {
                                // Toque curto sem arrasto → clique simples no cursor
                                // (duplo toque = dois cliques rápidos = double-click nativo)
                                val elapsed = SystemClock.uptimeMillis() - startTime
                                if (elapsed < 350L) {
                                    pendingClick = true
                                    captureTouches = false
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        if (!initialized) return@Canvas

        val x = cursor.x
        val y = cursor.y

        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y + 20.dp.toPx())
            lineTo(x + 5.5.dp.toPx(), y + 15.dp.toPx())
            lineTo(x + 11.dp.toPx(), y + 24.dp.toPx())
            lineTo(x + 14.dp.toPx(), y + 22.dp.toPx())
            lineTo(x + 8.dp.toPx(), y + 13.dp.toPx())
            lineTo(x + 16.dp.toPx(), y + 13.dp.toPx())
            close()
        }
        drawPath(path, Color.Black, style = Stroke(width = 3.5.dp.toPx()))
        drawPath(path, Color.White)
    }
}

private suspend fun <T> withTimeoutOrNull(
    timeMillis: Long,
    block: suspend () -> T,
): T? {
    return try {
        kotlinx.coroutines.withTimeout(timeMillis) { block() }
    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
        null
    }
}
