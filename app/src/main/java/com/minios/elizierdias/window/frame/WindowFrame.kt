package com.minios.elizierdias.window.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.minios.elizierdias.core.MiniWindow
import com.minios.elizierdias.window.manager.WindowManager

@Composable
fun WindowFrame(
    window: MiniWindow,
    onFocus: () -> Unit,
    onMove: (Offset) -> Unit,
    onResize: (Size) -> Unit,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    content: @Composable () -> Unit,
) {
    // Minimized windows are not drawn (Desktop filters them). Safety guard:
    if (window.isMinimized) return

    val density = LocalDensity.current
    val windowWidth = with(density) { window.size.width.toDp() }
    val windowHeight = with(density) { window.size.height.toDp() }

    // Live size holder so drag gestures do not restart when size changes
    val sizeHolder = remember(window.instanceId) { mutableStateOf(window.size) }
    sizeHolder.value = window.size
    val posHolder = remember(window.instanceId) { mutableStateOf(window.position) }
    posHolder.value = window.position

    var dragPos by remember(window.instanceId) { mutableStateOf(window.position) }

    Box(
        modifier = Modifier
            .offset { IntOffset(window.position.x.toInt(), window.position.y.toInt()) }
            .size(width = windowWidth, height = windowHeight)
            .zIndex(window.zIndex.toFloat())
            .border(
                width = if (window.isFocused) 2.dp else 1.dp,
                color = if (window.isFocused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color(0xFF30363D)
                },
                shape = RoundedCornerShape(10.dp),
            )
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF161B22))
            .pointerInput(window.instanceId) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) {
                            onFocus()
                        }
                    }
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(
                        if (window.isFocused) Color(0xFF21262D) else Color(0xFF1C2128),
                    )
                    .pointerInput(window.instanceId, window.isMaximized) {
                        detectDragGestures(
                            onDragStart = {
                                onFocus()
                                dragPos = posHolder.value
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (window.isMaximized) return@detectDragGestures
                                dragPos += dragAmount
                                onMove(dragPos)
                            },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = window.app.title,
                    color = Color(0xFFE6EDF3),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onMinimize, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Minimize,
                        contentDescription = "Minimize",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(14.dp),
                    )
                }
                IconButton(onClick = onToggleMaximize, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.CropSquare,
                        contentDescription = if (window.isMaximized) "Restore" else "Maximize",
                        tint = if (window.isMaximized) Color(0xFF58A6FF) else Color(0xFF8B949E),
                        modifier = Modifier.size(14.dp),
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFF85149),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            // Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                content()
            }
        }

        // Resize handles (only when not maximized)
        if (!window.isMaximized) {
            // Right edge
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(10.dp)
                    .pointerInput(window.instanceId) {
                        var w = sizeHolder.value.width
                        var h = sizeHolder.value.height
                        detectDragGestures(
                            onDragStart = {
                                onFocus()
                                w = sizeHolder.value.width
                                h = sizeHolder.value.height
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                w = (w + dragAmount.x).coerceAtLeast(WindowManager.MIN_WIDTH)
                                onResize(Size(w, h))
                            },
                        )
                    },
            )
            // Bottom edge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(10.dp)
                    .pointerInput(window.instanceId) {
                        var w = sizeHolder.value.width
                        var h = sizeHolder.value.height
                        detectDragGestures(
                            onDragStart = {
                                onFocus()
                                w = sizeHolder.value.width
                                h = sizeHolder.value.height
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                h = (h + dragAmount.y).coerceAtLeast(WindowManager.MIN_HEIGHT)
                                onResize(Size(w, h))
                            },
                        )
                    },
            )
            // Blue corner block — larger hit area, smooth drag
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .pointerInput(window.instanceId) {
                        var w = sizeHolder.value.width
                        var h = sizeHolder.value.height
                        detectDragGestures(
                            onDragStart = {
                                onFocus()
                                w = sizeHolder.value.width
                                h = sizeHolder.value.height
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                w = (w + dragAmount.x).coerceAtLeast(WindowManager.MIN_WIDTH)
                                h = (h + dragAmount.y).coerceAtLeast(WindowManager.MIN_HEIGHT)
                                onResize(Size(w, h))
                            },
                        )
                    },
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(16.dp)
                        .background(
                            color = if (window.isFocused) Color(0xFF58A6FF) else Color(0xFF6E7681),
                            shape = RoundedCornerShape(3.dp),
                        ),
                )
            }
        }
    }
}
