package com.minios.elizierdias.apps.settings

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.minios.elizierdias.core.MiniOSConfig
import com.minios.elizierdias.core.PowerMode
import com.minios.elizierdias.personalization.Wallpapers
import com.minios.elizierdias.ui.components.PcScrollVerticalScrollbar
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File

/** Altura máxima do wallpaper vídeo (melhor desempenho no telemóvel). */
private const val MAX_WALLPAPER_HEIGHT = 540

/** Se o lado maior for maior que isto, faz downscale. */
private const val MAX_SIDE_BEFORE_SCALE = 960

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

    // Photo Picker do sistema — mostra vídeos da galeria (incl. 4K indexados)
    val pickVideoPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) {
            statusMsg = "Nenhum vídeo escolhido"
            return@rememberLauncherForActivityResult
        }
        statusMsg = "A processar vídeo da galeria..."
        saveAndApply(uri)
    }

    // Fallback GetContent (alguns OEMs ainda usam isto)
    val pickVideoGetContent = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) {
            statusMsg = "Nenhum vídeo escolhido"
            return@rememberLauncherForActivityResult
        }
        statusMsg = "A processar vídeo..."
        saveAndApply(uri)
    }

    val pickAnyDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            statusMsg = "Nenhum ficheiro escolhido"
            return@rememberLauncherForActivityResult
        }
        takePersist(uri)
        statusMsg = "A processar ficheiro..."
        saveAndApply(uri)
    }

    Row(Modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(16.dp),
        ) {

            Text(text = "Wallpaper", color = Color(0xFFC9D1D9), fontSize = 14.sp)
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
                            Text(text = "OK", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(onClick = {
                statusMsg = ""
                pickImage.launch("image/*")
            }) {
                Text("Escolher foto / GIF da galeria")
            }

            Spacer(Modifier.height(8.dp))

            Button(onClick = {
                statusMsg = ""
                // Photo Picker (galeria moderna — 4K incluídos se estiverem na MediaStore)
                pickVideoPhotoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                )
            }) {
                Text("Escolher vídeo da galeria")
            }

            Spacer(Modifier.height(6.dp))

            OutlinedButton(onClick = {
                statusMsg = ""
                pickVideoGetContent.launch("video/*")
            }) {
                Text("Galeria (modo clássico)")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(onClick = {
                statusMsg = ""
                pickAnyDocument.launch(
                    arrayOf("video/*", "image/*", "application/octet-stream"),
                )
            }) {
                Text("Escolher ficheiro (Downloads / ficheiros)")
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "4K e vídeos grandes são reduzidos a ~540p só no cache do MiniOS (original intacto).",
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
            )
            Text(
                text = "Se a galeria não listar um vídeo, usa «ficheiro» (ex.: Downloads).",
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
                Text("Personalizado ativo ($kind)", color = Color(0xFF3FB950), fontSize = 12.sp)
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
                        onCheckedChange = { enabled ->
                            scope.launch { config.setWallpaperVideoSound(enabled) }
                        },
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
            Text("Sobre", color = Color(0xFFC9D1D9), fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text("MiniOS 0.3.4", color = Color(0xFF8B949E), fontSize = 12.sp)
            Text(
                "Wallpaper vídeo: cache ~540p · original preservado",
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(40.dp))
        }

        PcScrollVerticalScrollbar(
            state = scrollState,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
        )
    }
}

internal fun isVideoPath(path: String): Boolean {
    if (path.isBlank()) return false
    val p = path.lowercase()
    return p.endsWith(".mp4") || p.endsWith(".webm") || p.endsWith(".mkv") ||
        p.endsWith(".3gp") || p.endsWith(".mov") || p.endsWith(".m4v") ||
        p.endsWith(".avi") || p.endsWith(".ts") || p.endsWith(".m2ts") ||
        p.endsWith(".flv") || p.endsWith(".mpeg") || p.endsWith(".mpg")
}

private suspend fun saveWallpaper(context: Context, uri: Uri): Pair<String?, String> {
    val directory = File(context.filesDir, "wallpapers")
    if (!directory.exists()) directory.mkdirs()

    val mime = context.contentResolver.getType(uri)?.lowercase()
    val nameHint = (uri.lastPathSegment ?: uri.path ?: "").lowercase()

    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return null to "Não foi possível ler o ficheiro"
    if (bytes.isEmpty()) return null to "Ficheiro vazio"

    val extension = resolveExtension(mime, nameHint, bytes)
    val rawFile = File(directory, "wallpaper_raw_${System.currentTimeMillis()}$extension")
    rawFile.writeBytes(bytes)
    if (!rawFile.exists() || rawFile.length() == 0L) return null to "Falha ao gravar"

    if (!isVideoPath(rawFile.absolutePath) && extension != ".mp4" && extension != ".webm") {
        val finalFile = File(directory, "wallpaper_${System.currentTimeMillis()}$extension")
        rawFile.renameTo(finalFile)
        return finalFile.absolutePath to ""
    }

    val (w, h) = readVideoSize(rawFile)
    val maxSide = maxOf(w, h)

    if (w <= 0 || h <= 0 || maxSide <= MAX_SIDE_BEFORE_SCALE) {
        val finalFile = File(directory, "wallpaper_${System.currentTimeMillis()}.mp4")
        if (extension == ".mp4") rawFile.renameTo(finalFile)
        else {
            rawFile.copyTo(finalFile, overwrite = true)
            rawFile.delete()
        }
        return finalFile.absolutePath to ""
    }

    val outFile = File(directory, "wallpaper_${System.currentTimeMillis()}_540p.mp4")
    val ok = try {
        downscaleVideo(context, rawFile, outFile, MAX_WALLPAPER_HEIGHT)
    } catch (_: Exception) {
        false
    }

    if (ok && outFile.exists() && outFile.length() > 0L) {
        rawFile.delete()
        return outFile.absolutePath to ""
    }

    outFile.delete()
    val fallback = File(directory, "wallpaper_${System.currentTimeMillis()}.mp4")
    rawFile.renameTo(fallback)
    return fallback.absolutePath to ""
}

private fun readVideoSize(file: File): Pair<Int, Int> {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(file.absolutePath)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        w to h
    } catch (_: Exception) {
        0 to 0
    } finally {
        try { r.release() } catch (_: Exception) {}
    }
}

@OptIn(UnstableApi::class)
private suspend fun downscaleVideo(
    context: Context,
    input: File,
    output: File,
    targetHeight: Int,
): Boolean = suspendCancellableCoroutine { cont ->
    val transformer = Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .addListener(
            object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    if (cont.isActive) cont.resume(false)
                }
            },
        )
        .build()

    val edited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
        .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(targetHeight))))
        .build()

    try {
        transformer.start(edited, output.absolutePath)
    } catch (_: Exception) {
        if (cont.isActive) cont.resume(false)
        return@suspendCancellableCoroutine
    }
    cont.invokeOnCancellation {
        try { transformer.cancel() } catch (_: Exception) {}
    }
}

private fun resolveExtension(mime: String?, nameHint: String, bytes: ByteArray): String {
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
    listOf(
        ".gif", ".mp4", ".webm", ".mkv", ".3gp", ".mov", ".m4v",
        ".avi", ".ts", ".m2ts", ".flv", ".mpeg", ".mpg",
        ".png", ".jpg", ".jpeg", ".webp",
    ).forEach { ext ->
        if (nameHint.endsWith(ext)) return if (ext == ".jpeg") ".jpg" else ext
    }
    if (bytes.size >= 12) {
        if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()) return ".gif"
        if (bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) return ".png"
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return ".jpg"
        if (bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte()) return ".webm"
        if (bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte()
        ) {
            val brand = String(bytes, 8, minOf(4, bytes.size - 8))
            return if (brand.startsWith("qt")) ".mov" else ".mp4"
        }
    }
    return ".mp4"
}
