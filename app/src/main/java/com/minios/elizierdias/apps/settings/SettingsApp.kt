package com.minios.elizierdias.apps.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.minios.elizierdias.core.purgeWallpaperFiles
import com.minios.elizierdias.core.wallpaperStorageDir
import com.minios.elizierdias.personalization.Wallpapers
import com.minios.elizierdias.ui.components.PcScrollVerticalScrollbar
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val MAX_WALLPAPER_HEIGHT = 540
private const val MAX_SIDE_BEFORE_SCALE = 960
private const val MAX_IMAGE_SIDE = 1920

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

    fun saveAndApply(uri: Uri, preferImage: Boolean = false) {
        scope.launch {
            statusMsg = "A guardar wallpaper..."
            // 1) Tira o player do ecrã (liberta current.mp4/gif)
            config.detachWallpaperForReplace()
            delay(120)

            // 2) Grava o novo ficheiro (purge + current.*)
            val result = withContext(Dispatchers.IO) {
                try {
                    saveWallpaper(context, uri, preferImage)
                } catch (e: OutOfMemoryError) {
                    null to "Ficheiro demasiado grande (memória)"
                } catch (e: Exception) {
                    null to (e.message ?: "erro")
                }
            }
            val savedPath = result.first
            if (savedPath != null) {
                // 3) Aplica path + incrementa versão (Compose recria player)
                config.setWallpaperUri(savedPath)
                val kind = when {
                    isVideoPath(savedPath) -> "vídeo"
                    savedPath.endsWith(".gif", true) -> "GIF"
                    else -> "imagem"
                }
                statusMsg = "Wallpaper $kind ativo"
            } else {
                statusMsg = "Erro: ${result.second}"
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

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            statusMsg = "Nenhuma imagem"
            return@rememberLauncherForActivityResult
        }
        saveAndApply(uri, preferImage = true)
    }
    val pickVideoPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            statusMsg = "Nenhum vídeo"
            return@rememberLauncherForActivityResult
        }
        statusMsg = "A processar vídeo..."
        saveAndApply(uri)
    }
    val pickVideoClassic = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            statusMsg = "Nenhum vídeo"
            return@rememberLauncherForActivityResult
        }
        saveAndApply(uri)
    }
    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            statusMsg = "Nenhum ficheiro"
            return@rememberLauncherForActivityResult
        }
        takePersist(uri)
        saveAndApply(uri)
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
            Button(onClick = {
                statusMsg = ""
                pickImage.launch("image/*")
            }) {
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
            OutlinedButton(onClick = {
                statusMsg = ""
                pickVideoClassic.launch("video/*")
            }) {
                Text("Galeria (modo clássico)")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                statusMsg = ""
                pickDoc.launch(arrayOf("video/*", "image/*", "application/octet-stream"))
            }) { Text("Escolher ficheiro (Downloads)") }
            Spacer(Modifier.height(4.dp))
            Text(
                "Vídeo→vídeo e GIF→GIF funcionam (versão de conteúdo).",
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
            )

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
                }) {
                    Text("Remover wallpaper custom")
                }
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
            Text("Sobre", color = Color(0xFFC9D1D9), fontSize = 14.sp)
            Text("MiniOS · live wallpaper fix", color = Color(0xFF8B949E), fontSize = 12.sp)
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
    return listOf(
        ".mp4", ".webm", ".mkv", ".3gp", ".mov", ".m4v",
        ".avi", ".ts", ".m2ts", ".flv", ".mpeg", ".mpg",
    ).any { p.endsWith(it) }
}

private fun streamCopy(context: Context, uri: Uri, dest: File): Boolean {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                }
                output.flush()
            }
        } != null && dest.exists() && dest.length() > 0L
    } catch (_: Exception) {
        false
    }
}

private fun downscaleStillImage(src: File, dest: File, maxSide: Int): Boolean {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return false
        var sample = 1
        while (maxOf(w, h) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(src.absolutePath, opts) ?: return false
        FileOutputStream(dest).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 88, out) }
        bmp.recycle()
        dest.exists() && dest.length() > 0L
    } catch (_: Exception) {
        false
    }
}

private suspend fun saveWallpaper(
    context: Context,
    uri: Uri,
    preferImage: Boolean = false,
): Pair<String?, String> {
    val directory = wallpaperStorageDir(context)
    if (!directory.exists()) directory.mkdirs()

    purgeWallpaperFiles(context)
    directory.mkdirs()

    val mime = context.contentResolver.getType(uri)?.lowercase()
    val nameHint = (uri.lastPathSegment ?: uri.path ?: "").lowercase()

    val tmp = File(directory, "_tmp.bin")
    if (!streamCopy(context, uri, tmp)) {
        tmp.delete()
        return null to "Não foi possível ler o ficheiro"
    }
    val extension = sniffExtension(tmp, mime, nameHint, preferImage)

    if (extension in listOf(".jpg", ".jpeg", ".png", ".webp") ||
        (mime?.startsWith("image/") == true && extension != ".gif")
    ) {
        val scaled = File(directory, "current.jpg")
        if (downscaleStillImage(tmp, scaled, MAX_IMAGE_SIDE)) {
            tmp.delete()
            return scaled.absolutePath to ""
        }
        val finalFile = File(directory, "current$extension")
        tmp.renameTo(finalFile)
        return finalFile.absolutePath to ""
    }

    if (extension == ".gif") {
        val finalFile = File(directory, "current.gif")
        if (!tmp.renameTo(finalFile)) {
            tmp.copyTo(finalFile, overwrite = true)
            tmp.delete()
        }
        return finalFile.absolutePath to ""
    }

    val (w, h) = readVideoSize(tmp)
    val maxSide = maxOf(w, h)
    if (w <= 0 || h <= 0 || maxSide <= MAX_SIDE_BEFORE_SCALE) {
        val finalFile = File(directory, "current.mp4")
        if (extension == ".mp4") {
            if (!tmp.renameTo(finalFile)) {
                tmp.copyTo(finalFile, overwrite = true)
                tmp.delete()
            }
        } else {
            tmp.copyTo(finalFile, overwrite = true)
            tmp.delete()
        }
        return finalFile.absolutePath to ""
    }

    val outFile = File(directory, "current.mp4")
    val ok = try {
        downscaleVideo(context, tmp, outFile, MAX_WALLPAPER_HEIGHT)
    } catch (_: Exception) {
        false
    }
    if (ok && outFile.exists() && outFile.length() > 0L) {
        tmp.delete()
        return outFile.absolutePath to ""
    }
    outFile.delete()
    val fallback = File(directory, "current.mp4")
    if (!tmp.renameTo(fallback)) {
        tmp.copyTo(fallback, overwrite = true)
        tmp.delete()
    }
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
        try {
            r.release()
        } catch (_: Exception) {
        }
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
        ).build()
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
        try {
            transformer.cancel()
        } catch (_: Exception) {
        }
    }
}

private fun sniffExtension(
    file: File,
    mime: String?,
    nameHint: String,
    preferImage: Boolean,
): String {
    try {
        file.inputStream().use { ins ->
            val head = ByteArray(16)
            val n = ins.read(head)
            if (n >= 3) {
                if (head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() && head[2] == 'F'.code.toByte()) {
                    return ".gif"
                }
                if (head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte()) return ".jpg"
                if (head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte()) return ".png"
                if (n >= 12 && head[0] == 'R'.code.toByte() && head[8] == 'W'.code.toByte()) return ".webp"
                if (n >= 8 && head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte()) return ".mp4"
                if (head[0] == 0x1A.toByte() && head[1] == 0x45.toByte()) return ".webm"
            }
        }
    } catch (_: Exception) {
    }
    when {
        mime == "image/jpeg" || mime == "image/jpg" -> return ".jpg"
        mime == "image/png" -> return ".png"
        mime == "image/webp" -> return ".webp"
        mime == "image/gif" -> return ".gif"
        mime == "video/mp4" -> return ".mp4"
        mime == "video/webm" -> return ".webm"
        mime?.startsWith("video/") == true -> return ".mp4"
        mime?.startsWith("image/") == true -> return ".jpg"
    }
    listOf(".gif", ".mp4", ".webm", ".mkv", ".mov", ".png", ".jpg", ".jpeg", ".webp").forEach { ext ->
        if (nameHint.contains(ext)) return ext
    }
    return if (preferImage) ".jpg" else ".mp4"
}
