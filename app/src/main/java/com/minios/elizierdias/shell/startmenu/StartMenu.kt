package com.minios.elizierdias.shell.startmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.minios.elizierdias.core.MiniApp

@Composable
fun StartMenu(apps: List<MiniApp>, onAppClick: (MiniApp) -> Unit, onDismiss: () -> Unit) {
    Popup(
        alignment = Alignment.BottomStart,
        offset = androidx.compose.ui.unit.IntOffset(8, -52),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(Modifier.width(240.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF161B22)).padding(8.dp)) {
            Text("Aplicativos", color = Color(0xFF8B949E), fontSize = 11.sp, modifier = Modifier.padding(8.dp, 6.dp))
            apps.forEach { app ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .clickable { onAppClick(app); onDismiss() }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(app.icon, null, tint = Color(0xFF58A6FF), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(app.title, color = Color(0xFFC9D1D9), fontSize = 13.sp)
                }
            }
        }
    }
}
