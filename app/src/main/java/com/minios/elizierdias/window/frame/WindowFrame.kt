package com.minios.elizierdias.window.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    // Minimizada: não desenhar nada (Desktop também filtra — defesa extra)
    if (window.isMinimized) return

    val density = LocalDensity.current
    val windowWidth = with(density) { window.size.width.toDp() }
    val windowHeight = with(density) { window.size.height.toDp() }

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
            // Toque na janela → foca (antes do conteúdo)
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
            // Barra de título — arrastar para mover
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(
                        if (window.isFocused) Color(0xFF21262D) else Color(0xFF1C2128),
                    )
                    .pointerInput(window.instanceId, window.isMaximized) {
                        if (window.isMaximized) return@pointerInput
                        detectDragGestures(
                            onDragStart = {
                                onFocus()
                                dragPos = window.position
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragPos += dragAmount
                                onMove(dragPos)
                            },
                        )
                    }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = window.app.icon,
                    contentDescription = null,
                    tint = Color(0xFFC9D1D9),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = window.app.title,
                    color = Color(0xFFC9D1D9),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                IconButton(onClick = onMinimize, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Minimize,
                        "Minimize",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(14.dp),
                    )
                }
                IconButton(onClick = onToggleMaximize, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.CropSquare,
                        "Maximize",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(14.dp),
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        "Close",
                        tint = Color(0xFFF85149),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content()
            }
        }

        // Handle de resize canto inferior direito
        if (!window.isMaximized) {
            var resizeSize by remember(window.instanceId) { mutableStateOf(window.size) }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .pointerInput(window.instanceId) {
                        detectDragGestures(
                            onDragStart = {
                                onFocus()
                                resizeSize = window.size
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                resizeSize = Size(
                                    width = (resizeSize.width + dragAmount.x).coerceAtLeast(280f),
                                    height = (resizeSize.height + dragAmount.y).coerceAtLeast(200f),
                                )
                                onResize(resizeSize)
                            },
                        )
                    },
            )
        }
    }
}
