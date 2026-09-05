package com.minios.elizierdias.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val TrackColor = Color(0xFF2A2A2E)
private val ThumbColor = Color(0xFF7A7A82)
private val ThumbActive = Color(0xFF9A9AA2)

/**
 * Scrollbar vertical estilo PC:
 * - arrastar o thumb com botão esquerdo
 * - clicar na track (acima/abaixo do thumb) = saltar uma página (como no Windows)
 */
@Composable
fun PcLazyVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    width: Dp = 10.dp,
) {
    val scope = rememberCoroutineScope()
    val info = state.layoutInfo
    val total = info.totalItemsCount
    if (total <= 0) return
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return

    val viewportH = info.viewportSize.height.toFloat().coerceAtLeast(1f)
    val avg = visible.map { it.size }.average().toFloat().coerceAtLeast(1f)
    val contentH = avg * total
    if (contentH <= viewportH + 1f) return

    val thumbFraction = (viewportH / contentH).coerceIn(0.12f, 1f)
    val first = visible.first()
    val maxIndex = (total - visible.size).coerceAtLeast(1)
    val scrollFraction = ((first.index + (-first.offset / avg)) / maxIndex).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .clip(RoundedCornerShape(4.dp))
            .background(TrackColor),
    ) {
        val trackH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val thumbH = (trackH * thumbFraction).coerceAtLeast(24f)
        val thumbTravel = (trackH - thumbH).coerceAtLeast(0f)
        val thumbY = thumbTravel * scrollFraction

        // Track: clique = page up / page down
        Box(
            Modifier
                .fillMaxHeight()
                .width(width)
                .pointerInput(total, viewportH) {
                    detectTapGestures { offset ->
                        val page = (visible.size).coerceAtLeast(1)
                        val target = if (offset.y < thumbY) {
                            // acima do thumb → página para cima
                            (state.firstVisibleItemIndex - page).coerceAtLeast(0)
                        } else if (offset.y > thumbY + thumbH) {
                            // abaixo → página para baixo
                            (state.firstVisibleItemIndex + page).coerceAtMost(total - 1)
                        } else {
                            return@detectTapGestures
                        }
                        scope.launch { state.scrollToItem(target) }
                    }
                },
        )

        // Thumb: arrastar
        Box(
            Modifier
                .padding(top = with(LocalDensity.current) { thumbY.toDp() })
                .width(width)
                .height(with(LocalDensity.current) { thumbH.toDp() })
                .clip(RoundedCornerShape(4.dp))
                .background(ThumbColor)
                .pointerInput(total, trackH) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaFrac = dragAmount / trackH
                        val indexDelta = (deltaFrac * total).toInt()
                        if (indexDelta != 0) {
                            val target = (state.firstVisibleItemIndex + indexDelta)
                                .coerceIn(0, (total - 1).coerceAtLeast(0))
                            scope.launch { state.scrollToItem(target) }
                        }
                    }
                },
        )
    }
}

/** Scrollbar para Column + verticalScroll(ScrollState). */
@Composable
fun PcScrollVerticalScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
    width: Dp = 10.dp,
) {
    val scope = rememberCoroutineScope()
    val max = state.maxValue
    if (max <= 0) return

    val value = state.value
    val viewportApprox = 1f // fraction via value/max
    val thumbFraction = (0.2f).coerceIn(0.12f, 0.5f) // visual approx
    val scrollFraction = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .clip(RoundedCornerShape(4.dp))
            .background(TrackColor),
    ) {
        val trackH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val thumbH = (trackH * thumbFraction).coerceAtLeast(24f)
        val thumbTravel = (trackH - thumbH).coerceAtLeast(0f)
        val thumbY = thumbTravel * scrollFraction

        Box(
            Modifier
                .fillMaxHeight()
                .width(width)
                .pointerInput(max) {
                    detectTapGestures { offset ->
                        val page = (max * 0.2f).toInt().coerceAtLeast(40)
                        val target = when {
                            offset.y < thumbY -> (state.value - page).coerceAtLeast(0)
                            offset.y > thumbY + thumbH -> (state.value + page).coerceAtMost(max)
                            else -> return@detectTapGestures
                        }
                        scope.launch { state.scrollTo(target) }
                    }
                },
        )

        Box(
            Modifier
                .padding(top = with(LocalDensity.current) { thumbY.toDp() })
                .width(width)
                .height(with(LocalDensity.current) { thumbH.toDp() })
                .clip(RoundedCornerShape(4.dp))
                .background(ThumbActive)
                .pointerInput(max, trackH) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = ((dragAmount / trackH) * max).toInt()
                        if (delta != 0) {
                            scope.launch {
                                state.scrollTo((state.value + delta).coerceIn(0, max))
                            }
                        }
                    }
                },
        )
    }
}
