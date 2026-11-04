package com.minios.elizierdias.apps.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minios.elizierdias.core.MiniOSConfig
import com.minios.elizierdias.core.PowerMode
import com.minios.elizierdias.core.purgeWallpaperFiles
import com.minios.elizierdias.core.wallpaperStorageDir
import com.minios.elizierdias.personalization.Wallpapers
import com.minios.elizierdias.ui.components.PcScrollVerticalScrollbar
import com.minios.elizierdias.windows.WindowsConfig
import com.minios.elizierdias.windows.WindowsManager
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsApp() {
    val context = LocalContext.current
    val config = remember(context) { MiniOSConfig(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val windowsManager = remember(context) {
        WindowsManager(context).also { it.initialize() }
    }
    val windowsInstalled by windowsManager.isInstalled.collectAsState()
    val windowsStatus by windowsManager.statusMessage.collectAsState()
    val windowsProgress by windowsManager.installProgress.collectAsState()
    var windowsBusy by remember { mutableStateOf(false) }

    val wallpaper by config.wallpaperId.collectAsState(initial = "default_gradient")
    val wallpaperUri by config.wallpaperUri.collectAsState(initial = "")
    val wallpaperVideoSound by config.wallpaperVideoSound.collectAsState(initial = false)
    val power by config.powerMode.collectAsState(initial = PowerMode.BALANCED)
    var statusMsg by remember { mutableStateOf("") }

    val isVideoWallpaper = wallpaperUri.lowercase().let { p ->
        listOf(".mp4", ".webm", ".mkv", ".3gp", ".mov").any { p.endsWith(it) }
    }
    val isGifWallpaper = wallpaperUri.endsWith(".gif", true)

    fun saveSimple(uri: Uri) {
        scope.launch {
            statusMsg = "A guardar wallpaper..."
            config.detachWallpaperForReplace()
            delay(120)
            val path = withContext(Dispatchers.IO) {
                try {
                    val dir = wallpaperStorageDir(context)
                    dir.mkdirs()
                    purgeWallpaperFiles(context)
                    dir.mkdirs()
                    val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
                    val name = (uri.lastPathSegment ?: "").lowercase()
                    val ext = when {
                        mime.contains("gif") || name.endsWith(".gif") -> ".gif"
                        mime.startsWith("video") || name.endsWith(".mp4") -> ".mp4"
                        mime.contains("png") || name.endsWith(".png") -> ".png"
                        mime.contains("webp") || name.endsWith(".webp") -> ".webp"
                        else -> ".jpg"
                    }
                    val dest = File(dir, "current$ext")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    }
                    if (dest.exists() && dest.length() > 0) dest.absolutePath else null
                } catch (_: Exception) {
                    null
                }
            }
            if (path != null) {
                config.setWallpaperUri(path)
                statusMsg = "Wallpaper ativo"
            } else {
                statusMsg = "Erro ao guardar wallpaper"
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) statusMsg = "Nenhuma imagem" else saveSimple(uri)
    }
    val pickVideoPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) statusMsg = "Nenhum vídeo" else saveSimple(uri)
    }
    val pickVideoClassic = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) statusMsg = "Nenhum vídeo" else saveSimple(uri)
    }
    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            statusMsg = "Nenhum ficheiro"
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
        saveSimple(uri)
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(16.dp),
        ) {
            Text("Wallpaper", color = Color(0xFFC9D1D9), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Wallpapers.all.forEach { wp ->
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(wp.previewColor)
                            .clickable {
                                scope.launch {
                                    config.setWallpaper(wp.id)
                                    statusMsg = "Wallpaper: ${wp.id}"
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
            Button(onClick = { statusMsg = ""; pickImage.launch("image/*") }) {
                Text("Escolher foto / GIF da galeria")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                statusMsg = ""
                pickVideoPhoto.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                )
            }) { Text("Escolher vídeo da galeria") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { statusMsg = ""; pickVideoClassic.launch("video/*") }) {
                Text("Galeria (modo clássico)")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                statusMsg = ""
                pickDoc.launch(arrayOf("video/*", "image/*", "application/octet-stream"))
            }) { Text("Escolher ficheiro (Downloads)") }

            if (wallpaperUri.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                val kind = when {
                    isGifWallpaper -> "GIF"
                    isVideoWallpaper -> "Vídeo"
                    else -> "Imagem"
                }
                Text("Personalizado ativo ($kind)", color = Color(0xFF3FB950), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        config.clearCustomWallpaper()
                        statusMsg = "Wallpaper custom removido"
                    }
                }) { Text("Remover wallpaper custom") }
            }
            if (isVideoWallpaper) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { config.setWallpaperVideoSound(!wallpaperVideoSound) }
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Som no vídeo wallpaper", color = Color(0xFFC9D1D9), fontSize = 13.sp)
                        Text(
                            if (wallpaperVideoSound) "Som ligado" else "Mudo",
                            color = Color(0xFF8B949E),
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = wallpaperVideoSound,
                        onCheckedChange = { scope.launch { config.setWallpaperVideoSound(it) } },
                    )
                }
            }
            if (statusMsg.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(statusMsg, color = Color(0xFF8B949E), fontSize = 11.sp)
            }

            Spacer(Modifier.height(24.dp))
            Text("Desempenho", color = Color(0xFFC9D1D9), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            PowerMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { config.setPowerMode(mode) } }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = power == mode,
                        onClick = { scope.launch { config.setPowerMode(mode) } },
                    )
                    Text(
                        when (mode) {
                            PowerMode.PERFORMANCE -> "Performance"
                            PowerMode.BALANCED -> "Balanced"
                            PowerMode.BATTERY_SAVER -> "Battery Saver"
                        },
                        color = Color(0xFFC9D1D9),
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Windows Compatibility", color = Color(0xFFC9D1D9), fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Um só botão: prepara o Linux automaticamente (se precisar) e instala Wine. " +
                    "Precisas de internet e permissão de armazenamento.",
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
            val statusColor = if (windowsInstalled) Color(0xFF3FB950) else Color(0xFFD29922)
            Text(
                if (windowsInstalled) "Estado: Instalada" else "Estado: Não instalada",
                color = statusColor,
                fontSize = 13.sp,
            )
            Text(windowsStatus, color = Color(0xFF8B949E), fontSize = 11.sp)
            windowsProgress?.let { prog ->
                Spacer(Modifier.height(4.dp))
                Text(prog, color = Color(0xFF58A6FF), fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            if (!windowsInstalled) {
                Button(
                    onClick = {
                        if (windowsBusy) return@Button
                        windowsBusy = true
                        scope.launch {
                            windowsManager.installRuntime()
                            windowsBusy = false
                        }
                    },
                    enabled = !windowsBusy,
                ) {
                    Text(if (windowsBusy) "A instalar..." else "Instalar camada Windows")
                }
            } else {
                Text(
                    "Prefixo: ${WindowsConfig.defaultPrefixDir().absolutePath}",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "No Files, toca num .exe e confirma. Ficheiros em /sdcard funcionam melhor.",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("Sobre", color = Color(0xFFC9D1D9), fontSize = 14.sp)
            Text("NoskOS · UI + Windows one-click", color = Color(0xFF8B949E), fontSize = 12.sp)
            Spacer(Modifier.height(40.dp))
        }
        PcScrollVerticalScrollbar(
            state = scrollState,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
        )
    }
}
