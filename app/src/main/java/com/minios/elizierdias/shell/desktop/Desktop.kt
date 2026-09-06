package com.minios.elizierdias.shell.desktop

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.minios.elizierdias.apps.AppRegistry
import com.minios.elizierdias.apps.browser.BrowserApp
import com.minios.elizierdias.apps.files.FilesApp
import com.minios.elizierdias.apps.media.MediaPlayerOS
import com.minios.elizierdias.apps.settings.SettingsApp
import com.minios.elizierdias.apps.settings.isVideoPath
import com.minios.elizierdias.apps.softwarecenter.SoftwareCenterApp
import com.minios.elizierdias.apps.terminal.TerminalApp
import com.minios.elizierdias.core.MiniApp
import com.minios.elizierdias.core.MiniOSConfig
import com.minios.elizierdias.personalization.AnimatedWallpaper
import com.minios.elizierdias.personalization.VideoWallpaper
import com.minios.elizierdias.personalization.Wallpapers
import com.minios.elizierdias.shell.mouse.VirtualMouse
import com.minios.elizierdias.shell.startmenu.StartMenu
import com.minios.elizierdias.shell.taskbar.Taskbar
import com.minios.elizierdias.window.frame.WindowFrame
import com.minios.elizierdias.window.manager.WindowManager
import java.io.File

private val TaskbarHeight = 44.dp

@Composable
fun Desktop() {
    val context = LocalContext.current
    val config = remember(context) { MiniOSConfig(context) }
    val windowManager = remember { WindowManager() }

    val wallpaperId by config.wallpaperId.collectAsState(initial = "default_gradient")
    val wallpaperUri by config.wallpaperUri.collectAsState(initial = "")
    val wallpaperVersion by config.wallpaperVersion.collectAsState(initial = 0L)
    val wallpaperVideoSound by config.wallpaperVideoSound.collectAsState(initial = false)
    val wallpaper = Wallpapers.byId(wallpaperId)

    var startMenuOpen by remember { mutableStateOf(false) }
    var mouseEnabled by remember { mutableStateOf(false) }
    var desktopSizePx by remember { mutableStateOf(Size(1280f, 676f)) }

    LaunchedEffect(desktopSizePx) {
        windowManager.updateDesktopSize(desktopSizePx)
    }

    fun launchApp(app: MiniApp) {
        if (app.id == "smartplay") {
            val intent = context.packageManager.getLaunchIntentForPackage("com.appplayysmartt")
            if (intent != null) context.startActivity(intent)
            else Toast.makeText(context, "SmartPlay não está instalado", Toast.LENGTH_SHORT).show()
        } else {
            windowManager.openApp(app, desktopSizePx)
        }
    }

    // Root: UI + mouse overlay (mouse ALWAYS on top when enabled)
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    AppRegistry.all.forEach { app ->
                        DesktopIcon(app = app, onOpen = {
                            startMenuOpen = false
                            launchApp(app)
                        })
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
                                    "settings" -> SettingsApp()
                                    "software_center" -> SoftwareCenterApp()
                                    "browser" -> BrowserApp()
                                    "media_player" -> MediaPlayerOS()
                                    else -> Text("App: ${window.app.id}", color = Color.White)
                                }
                            }
                        }
                    }

                if (startMenuOpen) {
                    StartMenu(
                        apps = AppRegistry.all,
                        onAppClick = { app ->
                            startMenuOpen = false
                            launchApp(app)
                        },
                        onDismiss = { startMenuOpen = false },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TaskbarHeight),
            ) {
                Taskbar(
                    openWindows = windowManager.windows,
                    mouseEnabled = mouseEnabled,
                    onMouseToggle = { mouseEnabled = it },
                    onStartClick = { startMenuOpen = !startMenuOpen },
                    onWindowClick = { id ->
                        val window = windowManager.windows.firstOrNull { it.instanceId == id }
                            ?: return@Taskbar
                        if (window.isFocused && !window.isMinimized) {
                            windowManager.minimize(id)
                        } else {
                            windowManager.restore(id)
                        }
                    },
                )
            }
        }

        // Full-screen mouse layer — above windows AND taskbar
        VirtualMouse(
            enabled = mouseEnabled,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10_000f),
        )
    }
}

@Composable
private fun WallpaperLayer(
    wallpaperUri: String,
    wallpaperVersion: Long,
    gradientBrush: Brush,
    videoSound: Boolean,
) {
    val context = LocalContext.current
    val isGif = wallpaperUri.endsWith(".gif", ignoreCase = true)
    val isVideo = isVideoPath(wallpaperUri)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        if (isVideo) {
            key(wallpaperUri, wallpaperVersion, videoSound) {
                VideoWallpaper(
                    source = wallpaperUri,
                    contentKey = wallpaperVersion,
                    modifier = Modifier.fillMaxSize(),
                    soundEnabled = videoSound,
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
                    contentDescription = "Wallpaper",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
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
private fun DesktopIcon(app: MiniApp, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable { onOpen() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = app.icon,
            contentDescription = app.title,
            tint = Color.White,
            modifier = Modifier.size(30.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = app.title, color = Color.White, fontSize = 11.sp)
    }
}
