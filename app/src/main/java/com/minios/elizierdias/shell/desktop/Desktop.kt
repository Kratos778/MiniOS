package com.minios.elizierdias.shell.desktop

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.minios.elizierdias.apps.AppRegistry
import com.minios.elizierdias.apps.browser.BrowserApp
import com.minios.elizierdias.apps.files.FilesApp
import com.minios.elizierdias.apps.linuxgui.LinuxDesktopApp
import com.minios.elizierdias.apps.media.MediaPlayerOS
import com.minios.elizierdias.apps.settings.SettingsApp
import com.minios.elizierdias.apps.softwarecenter.SoftwareCenterApp
import com.minios.elizierdias.apps.terminal.TerminalApp
import com.minios.elizierdias.core.MiniApp
import com.minios.elizierdias.core.MiniOSConfig
import com.minios.elizierdias.core.PowerMode
import com.minios.elizierdias.personalization.AnimatedWallpaper
import com.minios.elizierdias.personalization.VideoWallpaper
import com.minios.elizierdias.personalization.Wallpapers
import com.minios.elizierdias.shell.startmenu.StartMenu
import com.minios.elizierdias.shell.taskbar.Taskbar
import com.minios.elizierdias.window.frame.WindowFrame
import com.minios.elizierdias.window.manager.WindowManager
import java.io.File

@Composable
fun Desktop(windowManager: WindowManager) {
    val context = LocalContext.current
    var showStartMenu by remember { mutableStateOf(false) }
    var desktopSizePx by remember { mutableStateOf(Size(1080f, 1920f)) }

    val wallpaperId by MiniOSConfig.wallpaperIdFlow.collectAsState(initial = MiniOSConfig.wallpaperId)
    val wallpaperUri by MiniOSConfig.wallpaperUriFlow.collectAsState(initial = MiniOSConfig.wallpaperUri)
    val wallpaperVersion by MiniOSConfig.wallpaperVersionFlow.collectAsState(initial = MiniOSConfig.wallpaperVersion)
    val wallpaperVideoSound by MiniOSConfig.wallpaperVideoSoundFlow.collectAsState(initial = MiniOSConfig.wallpaperVideoSound)
    val powerMode by MiniOSConfig.powerModeFlow.collectAsState(initial = MiniOSConfig.powerMode)

    val wallpaper = remember(wallpaperId) { Wallpapers.byId(wallpaperId) }

    LaunchedEffect(Unit) {
        MiniOSConfig.init(context)
    }

    fun launchApp(app: MiniApp) {
        showStartMenu = false
        if (app.id == "smartplay") {
            val intent = context.packageManager.getLaunchIntentForPackage("com.appplayysmartt")
            if (intent != null) context.startActivity(intent)
            else Toast.makeText(context, "SmartPlay nao esta instalado", Toast.LENGTH_SHORT).show()
        } else {
            windowManager.openApp(app, desktopSizePx)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .onSizeChanged { size ->
                        if (size.width > 0 && size.height > 0) {
                            desktopSizePx = Size(size.width.toFloat(), size.height.toFloat())
                        }
                    },
            ) {
                WallpaperLayer(
                    wallpaperUri = wallpaperUri,
                    wallpaperVersion = wallpaperVersion,
                    gradientBrush = wallpaper.brush,
                    videoSound = wallpaperVideoSound,
                    videoEnabled = powerMode != PowerMode.BATTERY_SAVER,
                )

                val icons = AppRegistry.desktopIcons
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)
                        .width(168.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    icons.forEach { app ->
                        DesktopIcon(app = app, onOpen = { launchApp(app) })
                    }
                }

                windowManager.windows
                    .filter { !it.isMinimized }
                    .sortedBy { it.zIndex }
                    .forEach { window ->
                        key(window.instanceId) {
                            WindowFrame(
                                window = window,
                                onFocus = { windowManager.focus(window.instanceId) },
                                onMove = { pos -> windowManager.move(window.instanceId, pos) },
                                onResize = { size -> windowManager.resize(window.instanceId, size) },
                                onClose = { windowManager.close(window.instanceId) },
                                onMinimize = { windowManager.minimize(window.instanceId) },
                                onToggleMaximize = {
                                    windowManager.toggleMaximize(window.instanceId, desktopSizePx)
                                },
                            ) {
                                when (window.app.id) {
                                    "files" -> FilesApp()
                                    "terminal" -> TerminalApp()
                                    "browser" -> BrowserApp()
                                    "settings" -> SettingsApp()
                                    "software" -> SoftwareCenterApp()
                                    "media" -> MediaPlayerOS()
                                    "linux-desktop" -> LinuxDesktopApp()
                                    else -> Text(
                                        "App: ${window.app.title}",
                                        color = Color(0xFFC9D1D9),
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            }
                        }
                    }

                if (showStartMenu) {
                    StartMenu(
                        onDismiss = { showStartMenu = false },
                        onOpenApp = { app -> launchApp(app) },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 8.dp)
                            .zIndex(10_000f),
                    )
                }
            }

            Taskbar(
                windowManager = windowManager,
                onStartClick = { showStartMenu = !showStartMenu },
                startMenuOpen = showStartMenu,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            )
        }
    }
}

@Composable
private fun WallpaperLayer(
    wallpaperUri: String,
    wallpaperVersion: Long,
    gradientBrush: Brush,
    videoSound: Boolean,
    videoEnabled: Boolean,
) {
    val context = LocalContext.current
    val lower = wallpaperUri.lowercase()
    val isVideo = lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv")
    val isGif = lower.endsWith(".gif")

    Box(modifier = Modifier.fillMaxSize()) {
        if (isVideo && videoEnabled) {
            key(wallpaperUri, wallpaperVersion) {
                VideoWallpaper(
                    source = wallpaperUri,
                    contentKey = wallpaperVersion,
                    modifier = Modifier.fillMaxSize(),
                    soundEnabled = videoSound,
                    enabled = true,
                )
            }
        } else if (isGif) {
            key(wallpaperUri, wallpaperVersion) {
                AnimatedWallpaper(
                    source = wallpaperUri,
                    contentKey = wallpaperVersion,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else if (wallpaperUri.isNotBlank()) {
            key(wallpaperUri, wallpaperVersion) {
                val file = File(wallpaperUri)
                val model = if (file.exists()) {
                    ImageRequest.Builder(context)
                        .data(file)
                        .memoryCacheKey("wp-$wallpaperVersion")
                        .diskCacheKey("wp-$wallpaperVersion")
                        .crossfade(true)
                        .build()
                } else {
                    ImageRequest.Builder(context)
                        .data(wallpaperUri)
                        .memoryCacheKey("wp-$wallpaperVersion")
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBrush),
            )
        }
    }
}

@Composable
private fun DesktopIcon(
    app: MiniApp,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .width(72.dp)
            .clickable(onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = app.icon,
            contentDescription = app.title,
            tint = Color(0xFFE6EDF3),
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = app.title,
            color = Color(0xFFE6EDF3),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
