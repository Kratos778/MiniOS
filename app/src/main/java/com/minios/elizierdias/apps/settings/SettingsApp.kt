package com.minios.elizierdias.apps.settings

import android.content.Context
import android.content.Intent
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

    val isVideoWallpaper = isVideoPath(wallpaperUri)
    val isGifWallpaper = wallpaperUri.endsWith(".gif", true)

    fun saveAndApply(uri: Uri) {
        scope.launch {
            statusMsg = "A guardar wallpaper..."
            val result = withContext(Dispatchers.IO) {
                try {
                    saveWallpaper(context, uri)
                } catch (e: Exception) {
                    null to (e.message ?: "erro")
                }
            }
            val savedPath = result.first
            if (savedPath != null) {
                config.setWallpaperUri(savedPath)
                val kind = when {
                    isVideoPath(savedPath) -> "vídeo"
                    savedPath.endsWith(".gif", true) -> "GIF"
                    else -> "imagem"
                }
                statusMsg = "Wallpaper $kind ativo"
            } else {
                statusMsg = "Erro ao guardar: ${result.second}"
            }
        }
    }

    fun takePersist(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
            // GetContent nem sempre permite persistable — ok
        }
    }

    // Galeria de fotos / GIF (MediaStore)
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) {
            statusMsg = "Nenhuma imagem escolhida"
            return@rememberLauncherForActivityResult
        }
        saveAndApply(uri)
    }

    // Vídeos indexados na galeria
    val pickVideoGallery = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) {
            statusMsg = "Nenhum vídeo escolhido"
            return@rememberLauncherForActivityResult
        }
        saveAndApply(uri)
    }

    // Qualquer ficheiro no armazenamento (inclui vídeos que a galeria não mostra)
    val pickAnyDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            statusMsg = "Nenhum ficheiro escolhido"
            return@rememberLauncherForActivityResult
        }
        takePersist(uri)
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
                pickVideoGallery.launch("video/*")
            },
        ) {
            Text("Escolher vídeo da galeria")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                statusMsg = ""
                // OpenDocument vê ficheiros reais (Downloads, etc.), não só a galeria
                pickAnyDocument.launch(
                    arrayOf(
                        "video/*",
                        "image/*",
                        "application/octet-stream",
                    ),
                )
            },
        ) {
            Text("Escolher ficheiro (Downloads / ficheiros)")
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Se o vídeo não aparece na galeria, usa «Escolher ficheiro».",
            color = Color(0xFF8B949E),
            fontSize = 11.sp,
        )

        if (wallpaperUri.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            val kind = when {
                isGifWallpaper -> "GIF animado"
                isVideoWallpaper -> "Vídeo em loop"
                else -> "Imagem"
            }
            Text(
                text = "Personalizado ativo ($kind)",
                color = Color(0xFF3FB950),
                fontSize = 12.sp,
            )
        }

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
        Text(text = "MiniOS 0.3.1", color = Color(0xFF8B949E), fontSize = 12.sp)
        Text(
            text = "Wallpaper: foto · GIF · vídeo (ficheiros + galeria)",
            color = Color(0xFF8B949E),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(40.dp))
    }
}

/** Extensões / caminhos que contam como vídeo no Desktop. */
internal fun isVideoPath(path: String): Boolean {
    if (path.isBlank()) return false
    val p = path.lowercase()
    return p.endsWith(".mp4") ||
        p.endsWith(".webm") ||
        p.endsWith(".mkv") ||
        p.endsWith(".3gp") ||
        p.endsWith(".mov") ||
        p.endsWith(".m4v") ||
        p.endsWith(".avi") ||
        p.endsWith(".ts") ||
        p.endsWith(".m2ts") ||
        p.endsWith(".flv") ||
        p.endsWith(".mpeg") ||
        p.endsWith(".mpg")
}

/**
 * Copia o URI para storage interno e devolve path + mensagem de erro.
 * Detecta o tipo real por MIME, nome e magic bytes (evita .bin → ecrã preto).
 */
private fun saveWallpaper(context: Context, uri: Uri): Pair<String?, String> {
    val directory = File(context.filesDir, "wallpapers")
    if (!directory.exists()) directory.mkdirs()

    val mime = context.contentResolver.getType(uri)?.lowercase()
    val nameHint = (
        uri.lastPathSegment
            ?: uri.path
            ?: ""
        ).lowercase()

    // 1) Lê bytes
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return null to "Não foi possível ler o ficheiro"

    if (bytes.isEmpty()) return null to "Ficheiro vazio"

    // 2) Extensão por MIME / nome / magic bytes
    val extension = resolveExtension(mime, nameHint, bytes)

    val filename = "wallpaper_${System.currentTimeMillis()}$extension"
    val destination = File(directory, filename)
    destination.writeBytes(bytes)

    if (!destination.exists() || destination.length() == 0L) {
        return null to "Falha ao gravar"
    }

    return destination.absolutePath to ""
}

private fun resolveExtension(mime: String?, nameHint: String, bytes: ByteArray): String {
    // MIME
    when {
        mime == "image/jpeg" || mime == "image/jpg" -> return ".jpg"
        mime == "image/png" -> return ".png"
        mime == "image/webp" -> return ".webp"
        mime == "image/gif" -> return ".gif"
        mime == "video/mp4" -> return ".mp4"
        mime == "video/webm" -> return ".webm"
        mime == "video/3gpp" || mime == "video/3gpp2" -> return ".3gp"
        mime == "video/quicktime" -> return ".mov"
        mime == "video/x-matroska" -> return ".mkv"
        mime == "video/x-m4v" -> return ".m4v"
        mime?.startsWith("video/") == true -> return ".mp4"
        mime?.startsWith("image/") == true -> return ".img"
    }

    // Nome do ficheiro
    listOf(
        ".gif", ".mp4", ".webm", ".mkv", ".3gp", ".mov", ".m4v",
        ".avi", ".ts", ".m2ts", ".flv", ".mpeg", ".mpg",
        ".png", ".jpg", ".jpeg", ".webp",
    ).forEach { ext ->
        if (nameHint.endsWith(ext)) {
            return if (ext == ".jpeg") ".jpg" else ext
        }
    }

    // Magic bytes
    if (bytes.size >= 12) {
        // GIF
        if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()) {
            return ".gif"
        }
        // PNG
        if (bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte()) {
            return ".png"
        }
        // JPEG
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return ".jpg"
        }
        // WebM / Matroska (EBML)
        if (bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
        ) {
            return ".webm"
        }
        // MP4 / MOV / M4V — "ftyp" at offset 4
        if (bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte()
        ) {
            val brand = String(bytes, 8, minOf(4, bytes.size - 8))
            return when {
                brand.startsWith("qt") -> ".mov"
                else -> ".mp4"
            }
        }
    }

    // Se o MIME era desconhecido mas o utilizador veio do picker de vídeo, assume mp4
    if (mime == null || mime == "application/octet-stream") {
        return ".mp4"
    }

    return ".mp4"
}
