package com.minios.elizierdias.apps.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minios.elizierdias.core.MiniOSConfig
import com.minios.elizierdias.core.PowerMode
import com.minios.elizierdias.personalization.Wallpapers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsApp() {
    val context = LocalContext.current
    val config = remember(context) { MiniOSConfig(context) }
    val scope = rememberCoroutineScope()
    val wallpaper by config.wallpaperId.collectAsState(initial = "default_gradient")
    val wallpaperUri by config.wallpaperUri.collectAsState(initial = "")
    val power by config.powerMode.collectAsState(initial = PowerMode.BALANCED)
    var statusMsg by remember { mutableStateOf("") }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
            statusMsg = "Nenhuma foto escolhida"
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            statusMsg = "A guardar wallpaper..."
            val saved = withContext(Dispatchers.IO) {
                try {
                    val dest = File(context.filesDir, "wallpaper_custom.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) { }
                    dest.absolutePath
                } catch (e: Exception) {
                    null
                }
            }
            if (saved != null) {
                config.setWallpaperUri(saved)
                statusMsg = "Wallpaper de foto ativo"
            } else {
                statusMsg = "Erro ao guardar a foto"
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0D1117)).padding(16.dp)) {
        Text("Wallpaper", color = Color(0xFFC9D1D9), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row {
            Wallpapers.all.forEach { wp ->
                Box(
                    Modifier.padding(end = 8.dp).size(56.dp).clip(RoundedCornerShape(8.dp))
                        .background(wp.previewColor)
                        .clickable {
                            scope.launch {
                                config.setWallpaper(wp.id)
                                statusMsg = "Gradiente: ${wp.id}"
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (wp.id == wallpaper && wallpaperUri.isEmpty()) {
                        Text("OK", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            statusMsg = ""
            pickImage.launch("image/*")
        }) {
            Text("Escolher foto da galeria")
        }
        if (wallpaperUri.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Foto personalizada ativa", color = Color(0xFF3FB950), fontSize = 12.sp)
        }
        if (statusMsg.isNotEmpty()) {
            Text(statusMsg, color = Color(0xFF8B949E), fontSize = 11.sp)
        }

        Spacer(Modifier.height(24.dp))
        Text("Desempenho", color = Color(0xFFC9D1D9), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        PowerMode.entries.forEach { mode ->
            Row(
                Modifier.fillMaxWidth().clickable { scope.launch { config.setPowerMode(mode) } }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = power == mode, onClick = { scope.launch { config.setPowerMode(mode) } })
                Text(
                    when (mode) {
                        PowerMode.PERFORMANCE -> "Performance"
                        PowerMode.BALANCED -> "Balanced"
                        PowerMode.BATTERY_SAVER -> "Battery Saver"
                    },
                    color = Color(0xFFC9D1D9), fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Sobre", color = Color(0xFFC9D1D9), fontSize = 14.sp)
        Text("MiniOS 0.1.0 · com.minios.elizierdias.debug", color = Color(0xFF8B949E), fontSize = 12.sp)
        Text("Mouse: 1 clique = ESQ · 2 cliques = DIR (estilo Winlator)", color = Color(0xFF8B949E), fontSize = 11.sp)
    }
}
