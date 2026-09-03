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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.minios.elizierdias.core.MouseButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Mouse virtual estilo Winlator / trackpad.
 *
 * - Ponteiro NÃO salta para o dedo.
 * - Movimento por DELTA (relativo): dedo no meio move ponteiro onde ele estiver.
 * - Clique SEMPRE na posição do ponteiro.
 * - 1 clique = ESQUERDO | 2 cliques rápidos = DIREITO
 */
@Composable
fun VirtualMouseOverlay(
    enabled: Boolean = true,
    sensitivity: Float = 1.2f,
    onClick: (position: Offset, button: MouseButton) -> Unit,
    onMove: (position: Offset) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!enabled) return

    var pointerPos by remember { mutableStateOf(Offset(200f, 160f)) }
    var screenSize by remember { mutableStateOf(Offset(1280f, 720f)) }

    val scope = rememberCoroutineScope()
    var pendingLeftJob by remember { mutableStateOf<Job?>(null) }
    var waitingSecondTap by remember { mutableStateOf(false) }

    fun clamp(p: Offset): Offset = Offset(
        p.x.coerceIn(0f, (screenSize.x - 4f).coerceAtLeast(4f)),
        p.y.coerceIn(0f, (screenSize.y - 4f).coerceAtLeast(4f)),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Offset(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // NÃO mover ponteiro para o dedo
                    var lastFinger = down.position
                    var totalTravel = 0f

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            val finger = change.position
                            val delta = (finger - lastFinger) * sensitivity
                            totalTravel += delta.getDistance()
                            pointerPos = clamp(pointerPos + delta)
                            onMove(pointerPos)
                            lastFinger = finger
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })

                    // Clique só se quase não arrastou
                    if (totalTravel < 14f) {
                        if (waitingSecondTap) {
                            // 2º toque → DIREITO
                            pendingLeftJob?.cancel()
                            pendingLeftJob = null
                            waitingSecondTap = false
                            onClick(pointerPos, MouseButton.RIGHT)
                        } else {
                            // 1º toque → espera; se não houver 2º, ESQUERDO
                            waitingSecondTap = true
                            val posAtTap = pointerPos
                            pendingLeftJob = scope.launch {
                                delay(300)
                                waitingSecondTap = false
                                onClick(posAtTap, MouseButton.LEFT)
                                pendingLeftJob = null
                            }
                        }
                    }
                }
            },
    ) {
        Canvas(
            Modifier
                .offset { IntOffset(pointerPos.x.roundToInt(), pointerPos.y.roundToInt()) }
                .size(28.dp),
        ) {
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
