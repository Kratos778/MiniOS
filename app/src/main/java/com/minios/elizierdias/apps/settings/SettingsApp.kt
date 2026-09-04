package com.minios.elizierdias.apps.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    val scrollState = rememberScrollState()

    val wallpaper by config.wallpaperId.collectAsState(initial = "default_gradient")
    val wallpaperUri by config.wallpaperUri.collectAsState(initial = "")
    val wallpaperVideoSound by config.wallpaperVideoSound.collectAsState(initial = false)
    val power by config.powerMode.collectAsState(initial = PowerMode.BALANCED)

    var statusMsg by remember { mutableStateOf("") }

    val isVideoWallpaper =
        wallpaperUri.endsWith(".mp4", true) ||
            wallpaperUri.endsWith(".webm", true) ||
            wallpaperUri.endsWith(".mkv", true) ||
            wallpaperUri.endsWith(".3gp", true) ||
            wallpaperUri.endsWith(".mov", true)

    fun saveAndApply(uri: Uri) {
        scope.launch {
            statusMsg = "A guardar wallpaper..."
            val savedPath = withContext(Dispatchers.IO) {
                try {
                    saveWallpaper(context, uri)
                } catch (_: Exception) {
                    null
                }
            }
            if (savedPath != null) {
                config.setWallpaperUri(savedPath)
                statusMsg = "Wallpaper personalizado ativo"
            } else {
                statusMsg = "Erro ao guardar o ficheiro"
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) {
            statusMsg = "Nenhuma imagem escolhida"
            return@rememberLauncherForActivityResult
        }
        saveAndApply(uri)
    }

    val pickVideo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) {
            statusMsg = "Nenhum vídeo escolhido"
            return@rememberLauncherForActivityResult
        }
        saveAndApply(uri)
    }

    val pickAny = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) {
            statusMsg = "Nenhum ficheiro escolhido"
            return@rememberLauncherForActivityResult
        }
        saveAndApply(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {

        Text(
            text = "Wallpaper",
            color = Color(0xFFC9D1D9),
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Wallpapers.all.forEach { wp ->
                androidx.compose.foundation.layout.Box(
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
                        Text(text = "OK", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                statusMsg = ""
                pickImage.launch("image/*")
            },
        ) {
            Text("Escolher foto / GIF da galeria")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                statusMsg = ""
                pickVideo.launch("video/*")
            },
        ) {
            Text("Escolher vídeo como wallpaper")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                statusMsg = ""
                pickAny.launch("*/*")
            },
        ) {
            Text("Escolher ficheiro (imagem ou vídeo)")
        }

        if (wallpaperUri.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            val kind = when {
                wallpaperUri.endsWith(".gif", true) -> "GIF animado"
                isVideoWallpaper -> "Vídeo em loop"
                else -> "Imagem"
            }
            Text(
                text = "Personalizado ativo ($kind)",
                color = Color(0xFF3FB950),
                fontSize = 12.sp,
            )
        }

        // Som do vídeo wallpaper — só relevante com vídeo ativo
        if (isVideoWallpaper) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            config.setWallpaperVideoSound(!wallpaperVideoSound)
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Som no vídeo wallpaper",
                        color = Color(0xFFC9D1D9),
                        fontSize = 13.sp,
                    )
                    Text(
                        text = if (wallpaperVideoSound) "Som ligado" else "Mudo",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = wallpaperVideoSound,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            config.setWallpaperVideoSound(enabled)
                        }
                    },
                )
            }
        }

        if (statusMsg.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(text = statusMsg, color = Color(0xFF8B949E), fontSize = 11.sp)
        }

        Spacer(Modifier.height(24.dp))

        Text(text = "Desempenho", color = Color(0xFFC9D1D9), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        PowerMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch { config.setPowerMode(mode) }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = power == mode,
                    onClick = {
                        scope.launch { config.setPowerMode(mode) }
                    },
                )
                Text(
                    text = when (mode) {
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

        Text(text = "Sobre", color = Color(0xFFC9D1D9), fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(text = "MiniOS 0.3.0", color = Color(0xFF8B949E), fontSize = 12.sp)
        Text(
            text = "Wallpaper: foto · GIF · vídeo · Desktop landscape",
            color = Color(0xFF8B949E),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(24.dp))
        Text(text = "MiniOS v0.3", color = Color(0xFFC9D1D9), fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "MediaPlayerOS com pesquisa · Wallpaper de vídeo",
            color = Color(0xFF8B949E),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(40.dp))
    }
}

private fun saveWallpaper(context: Context, uri: Uri): String {
    val directory = File(context.filesDir, "wallpapers")
    if (!directory.exists()) directory.mkdirs()

    val mime = context.contentResolver.getType(uri)

    val extension = when {
        mime == "image/jpeg" -> ".jpg"
        mime == "image/png" -> ".png"
        mime == "image/webp" -> ".webp"
        mime == "image/gif" -> ".gif"
        mime == "video/mp4" -> ".mp4"
        mime == "video/webm" -> ".webm"
        mime == "video/3gpp" -> ".3gp"
        mime?.startsWith("video/") == true -> ".mp4"
        mime?.startsWith("image/") == true -> ".img"
        else -> {
            val path = uri.lastPathSegment ?: ""
            when {
                path.endsWith(".gif", true) -> ".gif"
                path.endsWith(".mp4", true) -> ".mp4"
                path.endsWith(".webm", true) -> ".webm"
                path.endsWith(".png", true) -> ".png"
                path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> ".jpg"
                else -> ".bin"
            }
        }
    }

    val filename = "wallpaper_${System.currentTimeMillis()}$extension"
    val destination = File(directory, filename)

    context.contentResolver.openInputStream(uri)?.use { input ->
        destination.outputStream().use { output ->
            input.copyTo(output)
        }
    } ?: throw IllegalStateException("Não foi possível ler o ficheiro")

    return destination.absolutePath
}
