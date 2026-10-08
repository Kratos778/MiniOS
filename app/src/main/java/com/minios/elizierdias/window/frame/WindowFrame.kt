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
    // Minimizada: nao desenhar o frame, MAS manter o content na composition
    // para o estado da app (Terminal/Linux/Browser) nao fazer reset.
    if (window.isMinimized) {
        Box(modifier = Modifier.size(0.dp)) {
            content()
        }
        return
    }

    val density = LocalDensity.current
    val windowWidth = with(density) { window.size.width.toDp() }
    val windowHeight = with(density) { window.size.height.toDp() }

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
                detectDragGestures(
                    onDragStart = { onFocus() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onMove(
                            Offset(
                                window.position.x + dragAmount.x,
                                window.position.y + dragAmount.y,
                            ),
                        )
                    },
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Color(0xFF21262D))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = window.app.title,
                    color = Color(0xFFE6EDF3),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onMinimize, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Minimize,
                        contentDescription = "Minimize",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onToggleMaximize, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.CropSquare,
                        contentDescription = "Maximize",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFF85149),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                content()
            }
            // Resize handle (bottom-right)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .pointerInput(window.instanceId) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onResize(
                                Size(
                                    (window.size.width + dragAmount.x).coerceAtLeast(200f),
                                    (window.size.height + dragAmount.y).coerceAtLeast(150f),
                                ),
                            )
                        }
                    },
            )
        }
    }
}
