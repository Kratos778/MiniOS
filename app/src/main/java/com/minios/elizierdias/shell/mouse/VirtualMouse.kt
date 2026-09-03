package com.minios.elizierdias.shell.mouse

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
import androidx.compose.ui.unit.dp

/**
 * Lightweight virtual mouse for the MiniOS desktop.
 *
 * The cursor is drawn only inside the MiniOS desktop.
 * It does not control other Android applications.
 */
@Composable
fun VirtualMouse(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    sensitivity: Float = 1.15f,
    onLeftClick: ((Offset) -> Unit)? = null,
    onRightClick: ((Offset) -> Unit)? = null,
) {
    var cursor by remember { mutableStateOf(Offset.Zero) }
    var initialized by remember { mutableStateOf(false) }

    if (!enabled) return

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(sensitivity) {
                detectDragGestures { change, dragAmount ->
                    change.consume()

                    val next = cursor + dragAmount * sensitivity

                    cursor = Offset(
                        next.x.coerceIn(0f, size.width.toFloat()),
                        next.y.coerceIn(0f, size.height.toFloat())
                    )

                    initialized = true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        cursor = it
                        initialized = true
                        onLeftClick?.invoke(cursor)
                    },
                    onLongPress = {
                        cursor = it
                        initialized = true
                        onRightClick?.invoke(cursor)
                    }
                )
            }
    ) {
        if (!initialized) return@Canvas

        val x = cursor.x
        val y = cursor.y

        val line = 2.dp.toPx()
        val length = 13.dp.toPx()

        drawLine(
            Color.White,
            Offset(x, y),
            Offset(x, y + length),
            line
        )

        drawLine(
            Color.White,
            Offset(x, y),
            Offset(x + length * 0.75f, y + length * 0.75f),
            line
        )

        drawLine(
            Color.Black,
            Offset(x + 1.dp.toPx(), y + 2.dp.toPx()),
            Offset(
                x + 1.dp.toPx(),
                y + length + 1.dp.toPx()
            ),
            line / 2
        )
    }
}
