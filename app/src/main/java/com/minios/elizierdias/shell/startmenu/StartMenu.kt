package com.minios.elizierdias.shell.startmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.minios.elizierdias.core.MiniApp

@Composable
fun StartMenu(
    apps: List<MiniApp>,
    onAppClick: (MiniApp) -> Unit,
    onDismiss: () -> Unit,
    onExitMiniOS: () -> Unit = {},
) {
    Popup(
        alignment = Alignment.BottomStart,
        offset = IntOffset(8, -52),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(240.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF161B22))
                .padding(8.dp),
        ) {
            Text(
                "Aplicativos",
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
                modifier = Modifier.padding(8.dp, 6.dp),
            )
            apps.forEach { app ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            onAppClick(app)
                            onDismiss()
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        app.icon,
                        null,
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(app.title, color = Color(0xFFC9D1D9), fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF30363D)),
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        onDismiss()
                        onExitMiniOS()
                    }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Sair",
                    tint = Color(0xFFF85149),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Sair do MiniOS", color = Color(0xFFF85149), fontSize = 13.sp)
            }
        }
    }
}
