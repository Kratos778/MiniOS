package com.minios.elizierdias.input

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.minios.elizierdias.core.MouseButton
import kotlin.math.roundToInt

@Composable
fun VirtualMouseOverlay(
    enabled: Boolean = true,
    pointerOffset: Offset = Offset(-24f, -56f),
    onClick: (position: Offset, button: MouseButton) -> Unit,
    onMove: (position: Offset) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    var pointerPos by remember { mutableStateOf(Offset(200f, 160f)) }
    var visible by remember { mutableStateOf(true) }

    Box(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startTime = System.currentTimeMillis()
                var moved = false
                pointerPos = down.position + pointerOffset
                onMove(pointerPos)
                visible = true
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (change.pressed) {
                        if ((change.position - down.position).getDistance() > 8f) moved = true
                        pointerPos = change.position + pointerOffset
                        onMove(pointerPos)
                        change.consume()
                    }
                } while (event.changes.any { it.pressed })
                val duration = System.currentTimeMillis() - startTime
                if (!moved || duration < 200) {
                    val button = if (duration >= 400L) MouseButton.RIGHT else MouseButton.LEFT
                    onClick(pointerPos, button)
                }
            }
        },
    ) {
        if (visible) {
            Canvas(Modifier.offset {
                IntOffset(pointerPos.x.roundToInt(), pointerPos.y.roundToInt())
            }.size(28.dp)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(0f, h * 0.85f)
                    lineTo(w * 0.28f, h * 0.68f)
                    lineTo(w * 0.48f, h)
                    lineTo(w * 0.62f, h * 0.92f)
                    lineTo(w * 0.40f, h * 0.58f)
                    lineTo(w * 0.72f, h * 0.58f)
                    close()
                }
                drawPath(path, Color.White, style = Fill)
                drawPath(path, Color.Black, style = Stroke(width = 1.5f))
            }
        }
    }
}
