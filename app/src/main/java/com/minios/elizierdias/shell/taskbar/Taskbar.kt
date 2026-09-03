package com.minios.elizierdias.shell.taskbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minios.elizierdias.core.MiniWindow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun Taskbar(
    openWindows: List<MiniWindow>,
    onStartClick: () -> Unit,
    onWindowClick: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(Color(0xFF161B22)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.fillMaxHeight().clickable { onStartClick() }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Apps, "Start", tint = Color(0xFF58A6FF), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("MiniOS", color = Color(0xFFC9D1D9), fontSize = 13.sp)
        }

        Row(Modifier.weight(1f).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            openWindows.forEach { w ->
                Row(
                    Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (w.isFocused && !w.isMinimized) Color(0xFF21262D) else Color.Transparent)
                        .clickable { onWindowClick(w.instanceId) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(w.app.icon, null, tint = Color(0xFF8B949E), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(w.app.title, color = Color(0xFFC9D1D9), fontSize = 12.sp)
                }
            }
        }

        var now by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
        LaunchedEffect(Unit) {
            while (true) {
                now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                delay(15_000)
            }
        }
        Text(now, color = Color(0xFFC9D1D9), fontSize = 12.sp)
        Spacer(Modifier.width(14.dp))
    }
}
