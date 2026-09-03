package com.minios.elizierdias.apps.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.core.content.ContextCompat
import com.minios.elizierdias.core.MiniOSConfig
import com.minios.elizierdias.core.PowerMode
import com.minios.elizierdias.personalization.Wallpapers
import kotlinx.coroutines.launch

@Composable
fun SettingsApp() {
    val context = LocalContext.current
    val config = remember(context) { MiniOSConfig(context) }
    val scope = rememberCoroutineScope()
    val wallpaper by config.wallpaperId.collectAsState(initial = "default_gradient")
    val wallpaperUri by config.wallpaperUri.collectAsState(initial = "")
    val power by config.powerMode.collectAsState(initial = PowerMode.BALANCED)

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
            scope.launch { config.setWallpaperUri(uri.toString()) }
        }
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickImage.launch(arrayOf("image/*"))
    }

    fun openPhotoPicker() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            pickImage.launch(arrayOf("image/*"))
        } else {
            requestPermission.launch(permission)
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
                        .clickable { scope.launch { config.setWallpaper(wp.id) } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (wp.id == wallpaper && wallpaperUri.isEmpty()) Text("OK", color = Color.White, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { openPhotoPicker() }) { Text("Escolher foto da galeria") }
        if (wallpaperUri.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Foto personalizada ativa", color = Color(0xFF3FB950), fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text("Desempenho", color = Color(0xFFC9D1D9), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        PowerMode.entries.forEach { mode ->
            Row(
                Modifier.fillMaxWidth().clickable { scope.launch { config.setPowerMode(mode) } }.padding(vertical = 4.dp),
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
        Text("Mouse: toque curto = ESQ · longo = DIR", color = Color(0xFF8B949E), fontSize = 11.sp)
    }
}
