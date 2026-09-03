package com.minios.elizierdias.apps.settings

import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
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

    val context =
        LocalContext.current

    val config =
        remember(context) {
            MiniOSConfig(context)
        }

    val scope =
        rememberCoroutineScope()

    val scrollState =
        rememberScrollState()

    val wallpaper by
        config.wallpaperId.collectAsState(
            initial = "default_gradient",
        )

    val wallpaperUri by
        config.wallpaperUri.collectAsState(
            initial = "",
        )

    val power by
        config.powerMode.collectAsState(
            initial = PowerMode.BALANCED,
        )

    var statusMsg by
        remember {
            mutableStateOf("")
        }

    val pickImage =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->

            if (uri == null) {
                statusMsg =
                    "Nenhuma foto escolhida"

                return@rememberLauncherForActivityResult
            }

            scope.launch {

                statusMsg =
                    "A guardar wallpaper..."

                val savedPath =
                    withContext(Dispatchers.IO) {

                        try {
                            saveWallpaper(
                                context,
                                uri,
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }

                if (savedPath != null) {

                    config.setWallpaperUri(
                        savedPath,
                    )

                    statusMsg =
                        "Wallpaper personalizado ativo"

                } else {

                    statusMsg =
                        "Erro ao guardar a foto"
                }
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color(0xFF0D1117),
            )
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {

        Text(
            text = "Wallpaper",
            color = Color(0xFFC9D1D9),
            fontSize = 14.sp,
        )

        Spacer(
            Modifier.height(8.dp),
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {

            Wallpapers.all.forEach { wp ->

                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(
                            RoundedCornerShape(8.dp),
                        )
                        .background(
                            wp.previewColor,
                        )
                        .clickable {

                            scope.launch {

                                config.setWallpaper(
                                    wp.id,
                                )

                                statusMsg =
                                    "Wallpaper: ${wp.id}"
                            }
                        },
                    contentAlignment =
                        Alignment.Center,
                ) {

                    if (
                        wp.id == wallpaper &&
                        wallpaperUri.isEmpty()
                    ) {

                        Text(
                            text = "OK",
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        Spacer(
            Modifier.height(12.dp),
        )

        Button(
            onClick = {

                statusMsg = ""

                pickImage.launch(
                    "image/*",
                )
            },
        ) {

            Text(
                "Escolher foto da galeria",
            )
        }

        if (wallpaperUri.isNotEmpty()) {

            Spacer(
                Modifier.height(6.dp),
            )

            Text(
                text =
                    "Foto personalizada ativa",
                color = Color(0xFF3FB950),
                fontSize = 12.sp,
            )
        }

        if (statusMsg.isNotEmpty()) {

            Spacer(
                Modifier.height(4.dp),
            )

            Text(
                text = statusMsg,
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
            )
        }

        Spacer(
            Modifier.height(24.dp),
        )

        Text(
            text = "Desempenho",
            color = Color(0xFFC9D1D9),
            fontSize = 14.sp,
        )

        Spacer(
            Modifier.height(8.dp),
        )

        PowerMode.entries.forEach { mode ->

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {

                        scope.launch {
                            config.setPowerMode(
                                mode,
                            )
                        }
                    }
                    .padding(
                        vertical = 4.dp,
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {

                RadioButton(
                    selected =
                        power == mode,

                    onClick = {

                        scope.launch {
                            config.setPowerMode(
                                mode,
                            )
                        }
                    },
                )

                Text(
                    text =
                        when (mode) {

                            PowerMode.PERFORMANCE ->
                                "Performance"

                            PowerMode.BALANCED ->
                                "Balanced"

                            PowerMode.BATTERY_SAVER ->
                                "Battery Saver"
                        },
                    color = Color(0xFFC9D1D9),
                    fontSize = 13.sp,
                )
            }
        }

        Spacer(
            Modifier.height(24.dp),
        )

        Text(
            text = "Sobre",
            color = Color(0xFFC9D1D9),
            fontSize = 14.sp,
        )

        Spacer(
            Modifier.height(4.dp),
        )

        Text(
            text = "MiniOS 0.2.0",
            color = Color(0xFF8B949E),
            fontSize = 12.sp,
        )

        Text(
            text =
                "Wallpaper persistente · Desktop landscape",
            color = Color(0xFF8B949E),
            fontSize = 11.sp,
        )

        Spacer(
            Modifier.height(24.dp),
        )

        Text(
            text = "MiniOS v0.3",
            color = Color(0xFFC9D1D9),
            fontSize = 14.sp,
        )

        Spacer(
            Modifier.height(6.dp),
        )

        Text(
            text =
                "Linux Runtime e Browser em desenvolvimento",
            color = Color(0xFF8B949E),
            fontSize = 11.sp,
        )

        Spacer(
            Modifier.height(40.dp),
        )
    }
}

private fun saveWallpaper(
    context: Context,
    uri: Uri,
): String {

    val directory =
        File(
            context.filesDir,
            "wallpapers",
        )

    if (!directory.exists()) {
        directory.mkdirs()
    }

    val mime =
        context.contentResolver
            .getType(uri)

    val extension =
        when (mime) {

            "image/jpeg" ->
                ".jpg"

            "image/png" ->
                ".png"

            "image/webp" ->
                ".webp"

            "image/gif" ->
                ".gif"

            else ->
                ".img"
        }

    val filename =
        "wallpaper_${System.currentTimeMillis()}$extension"

    val destination =
        File(
            directory,
            filename,
        )

    context.contentResolver
        .openInputStream(uri)
        ?.use { input ->

            destination.outputStream()
                .use { output ->

                    input.copyTo(output)
                }
        }
        ?: throw IllegalStateException(
            "Não foi possível ler a imagem",
        )

    return destination.absolutePath
}
