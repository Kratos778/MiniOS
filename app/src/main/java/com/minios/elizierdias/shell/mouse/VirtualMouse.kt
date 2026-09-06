package com.minios.elizierdias.shell.mouse

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/**
 * Rato virtual otimizado.
 *
 * Posição do cursor lida só na fase de *draw* (graphicsLayer),
 * para não recompor o Desktop a cada pixel de movimento.
 */
@Composable
fun VirtualMouse(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    sensitivity: Float = 2.6f,
) {
    if (!enabled) return

    val hostView = LocalView.current

    var cursorX by remember { mutableFloatStateOf(0f) }
    var cursorY by remember { mutableFloatStateOf(0f) }
    var maxX by remember { mutableFloatStateOf(1f) }
    var maxY by remember { mutableFloatStateOf(1f) }
    var originX by remember { mutableFloatStateOf(0f) }
    var originY by remember { mutableFloatStateOf(0f) }
    var initialized by remember { mutableStateOf(false) }

    var captureTouches by remember { mutableStateOf(true) }
    var pendingClick by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var holdDownTime by remember { mutableStateOf(0L) }

    fun absX() = originX + cursorX
    fun absY() = originY + cursorY

    fun dispatch(action: Int, downTime: Long, eventTime: Long = downTime) {
        val target = hostView.rootView ?: hostView
        val ev = MotionEvent.obtain(
            downTime, eventTime, action, absX(), absY(), 0,
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try {
            target.dispatchTouchEvent(ev)
        } finally {
            ev.recycle()
        }
    }

    fun clickAtCursor() {
        val t = SystemClock.uptimeMillis()
        dispatch(MotionEvent.ACTION_DOWN, t)
        dispatch(MotionEvent.ACTION_UP, t, t + 30)
    }

    LaunchedEffect(pendingClick, captureTouches) {
        if (pendingClick && !captureTouches) {
            delay(8)
            clickAtCursor()
            delay(32)
            pendingClick = false
            captureTouches = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100_000f)
            .onGloballyPositioned { coords ->
                originX = coords.positionInWindow().x
                originY = coords.positionInWindow().y
                maxX = coords.size.width.toFloat().coerceAtLeast(1f)
                maxY = coords.size.height.toFloat().coerceAtLeast(1f)
                if (!initialized && coords.size.width > 0) {
                    cursorX = maxX * 0.5f
                    cursorY = maxY * 0.5f
                    initialized = true
                }
            }
            .then(
                if (captureTouches) {
                    Modifier
                        .pointerInput(sensitivity) {
                            detectDragGestures(
                                onDragStart = {
                                    if (!initialized) {
                                        cursorX = size.width / 2f
                                        cursorY = size.height / 2f
                                        initialized = true
                                    }
                                    if (holding) {
                                        captureTouches = false
                                        holdDownTime = SystemClock.uptimeMillis()
                                        dispatch(MotionEvent.ACTION_DOWN, holdDownTime)
                                        captureTouches = true
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    cursorX = (cursorX + dragAmount.x * sensitivity)
                                        .coerceIn(0f, maxX)
                                    cursorY = (cursorY + dragAmount.y * sensitivity)
                                        .coerceIn(0f, maxY)
                                    if (holding) {
                                        captureTouches = false
                                        dispatch(
                                            MotionEvent.ACTION_MOVE,
                                            holdDownTime,
                                            SystemClock.uptimeMillis(),
                                        )
                                        captureTouches = true
                                    }
                                },
                                onDragEnd = {
                                    if (holding) {
                                        captureTouches = false
                                        dispatch(
                                            MotionEvent.ACTION_UP,
                                            holdDownTime,
                                            SystemClock.uptimeMillis(),
                                        )
                                        captureTouches = true
                                        holding = false
                                    }
                                },
                                onDragCancel = {
                                    if (holding) {
                                        captureTouches = false
                                        dispatch(
                                            MotionEvent.ACTION_CANCEL,
                                            holdDownTime,
                                            SystemClock.uptimeMillis(),
                                        )
                                        captureTouches = true
                                        holding = false
                                    }
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    pendingClick = true
                                    captureTouches = false
                                },
                                onDoubleTap = {
                                    pendingClick = true
                                    captureTouches = false
                                },
                                onLongPress = {
                                    holding = true
                                    holdDownTime = SystemClock.uptimeMillis()
                                    captureTouches = false
                                    dispatch(MotionEvent.ACTION_DOWN, holdDownTime)
                                    captureTouches = true
                                },
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        if (!initialized) return@Box

        // Cursor isolado: translation lida na fase de draw → sem recompor Desktop
        Canvas(
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    translationX = cursorX
                    translationY = cursorY
                    // camada GPU própria para o ponteiro
                    clip = false
                },
        ) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, 18.dp.toPx())
                lineTo(5.dp.toPx(), 14.dp.toPx())
                lineTo(10.dp.toPx(), 22.dp.toPx())
                lineTo(12.dp.toPx(), 20.dp.toPx())
                lineTo(7.dp.toPx(), 12.dp.toPx())
                lineTo(14.dp.toPx(), 12.dp.toPx())
                close()
            }
            drawPath(path, Color.Black, style = Stroke(width = 3.dp.toPx()))
            drawPath(path, Color.White)
            if (holding) {
                drawCircle(
                    color = Color(0x8858A6FF),
                    radius = 9.dp.toPx(),
                    center = Offset(4.dp.toPx(), 4.dp.toPx()),
                )
            }
        }
    }
}
