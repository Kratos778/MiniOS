package com.minios.elizierdias.window.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val density = LocalDensity.current
    val windowWidth = with(density) { window.size.width.toDp() }
    val windowHeight = with(density) { window.size.height.toDp() }

    val sizeHolder = remember(window.instanceId) { mutableStateOf(window.size) }
    sizeHolder.value = window.size
    val posHolder = remember(window.instanceId) { mutableStateOf(window.position) }
    posHolder.value = window.position

    /*
     * IMPORTANTE: minimizar NÃO pode usar size(0.dp).
     * AndroidView / WebView / MediaPlayer / Terminal perdem estado ou param
     * quando o layout fica 0x0. Mantemos o tamanho real e empurramos a
     * janela para fora do ecrã (ainda na composition).
     */
    val drawX = if (window.isMinimized) -100_000 else window.position.x.toInt()
    val drawY = if (window.isMinimized) -100_000 else window.position.y.toInt()
    val drawZ = if (window.isMinimized) -1f else window.zIndex.toFloat()

    Box(
        modifier = Modifier
            .offset { IntOffset(drawX, drawY) }
            .size(width = windowWidth, height = windowHeight)
            .zIndex(drawZ)
            .border(
                width = if (window.isFocused && !window.isMinimized) 1.5.dp else 1.dp,
                color = if (window.isFocused && !window.isMinimized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color(0xFF30363D)
                },
                shape = RoundedCornerShape(8.dp),
            )
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF161B22)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(Color(0xFF21262D))
                    .padding(horizontal = 6.dp)
                    .pointerInput(window.instanceId, window.isMinimized) {
                        if (window.isMinimized) return@pointerInput
                        detectDragGestures(
                            onDragStart = { onFocus() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onMove(
                                    Offset(
                                        posHolder.value.x + dragAmount.x,
                                        posHolder.value.y + dragAmount.y,
                                    ),
                                )
                            },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = window.app.title,
                    color = Color(0xFFE6EDF3),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                IconButton(onClick = onMinimize, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.Minimize,
                        contentDescription = "Minimize",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(14.dp),
                    )
                }
                IconButton(onClick = onToggleMaximize, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.CropSquare,
                        contentDescription = "Maximize",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(14.dp),
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFF85149),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                content()
            }
        }
        if (!window.isMinimized) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
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
                        .padding(3.dp)
                        .size(12.dp)
                        .background(
                            color = if (window.isFocused) Color(0xFF58A6FF) else Color(0xFF6E7681),
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}
