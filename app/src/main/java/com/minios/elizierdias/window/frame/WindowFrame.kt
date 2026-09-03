package com.minios.elizierdias.window.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    if (window.isMinimized) return
    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(window.position.x.toInt(), window.position.y.toInt()) }
            .size(
                width = with(androidx.compose.ui.platform.LocalDensity.current) { window.size.width.toDp() },
                height = with(androidx.compose.ui.platform.LocalDensity.current) { window.size.height.toDp() },
            )
            .border(
                width = if (window.isFocused) 2.dp else 1.dp,
                color = if (window.isFocused) MaterialTheme.colorScheme.primary else Color(0xFF30363D),
                shape = RoundedCornerShape(10.dp),
            )
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF161B22)),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp)
                    .background(if (window.isFocused) Color(0xFF21262D) else Color(0xFF1C2128))
                    .pointerInput(window.instanceId) {
                        detectDragGestures(onDragStart = { onFocus() }) { change, dragAmount ->
                            change.consume()
                            onMove(window.position + dragAmount)
                        }
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(window.app.icon, null, tint = Color(0xFFC9D1D9), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(window.app.title, color = Color(0xFFC9D1D9), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                IconButton(onClick = onMinimize, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Minimize, null, tint = Color(0xFF8B949E), modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onToggleMaximize, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.CropSquare, null, tint = Color(0xFF8B949E), modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, null, tint = Color(0xFFF85149), modifier = Modifier.size(14.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(18.dp).pointerInput(window.instanceId) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onResize(Size(window.size.width + dragAmount.x, window.size.height + dragAmount.y))
                }
            },
        )
    }
}
