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
import com.minios.elizierdias.apps.settings.isVideoPath
import com.minios.elizierdias.apps.softwarecenter.SoftwareCenterApp
import com.minios.elizierdias.apps.terminal.TerminalApp
import com.minios.elizierdias.core.MiniApp
import com.minios.elizierdias.core.MiniOSConfig
import com.minios.elizierdias.core.PowerMode
import com.minios.elizierdias.personalization.AnimatedWallpaper
import com.minios.elizierdias.personalization.VideoWallpaper
import com.minios.elizierdias.shell.LauncherExit
import com.minios.elizierdias.shell.mouse.VirtualMouseOverlay
import com.minios.elizierdias.shell.startmenu.StartMenu
import com.minios.elizierdias.shell.taskbar.Taskbar
import com.minios.elizierdias.shell.taskbar.TaskbarHeight
import com.minios.elizierdias.window.frame.WindowFrame
import com.minios.elizierdias.window.manager.WindowManager
import java.io.File

@Composable
fun Desktop(
    windowManager: WindowManager,
    powerMode: PowerMode = PowerMode.BALANCED,
) {
    val context = LocalContext.current
    var startMenuOpen by remember { mutableStateOf(false) }
    var mouseEnabled by remember { mutableStateOf(false) }
    var desktopSizePx by remember { mutableStateOf(Size(1080f, 1920f)) }

    val wallpaperUri by MiniOSConfig.wallpaperUri.collectAsState()
    val wallpaperVersion by MiniOSConfig.wallpaperVersion.collectAsState()
    val videoSound by MiniOSConfig.videoWallpaperSound.collectAsState()

    val icons = remember { AppRegistry.all }

    fun launchApp(app: MiniApp) {
        windowManager.open(app, desktopSizePx)
    }

    fun exitMiniOS(ctx: android.content.Context) {
        LauncherExit.exit(ctx)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { sz ->
                desktopSizePx = Size(sz.width.toFloat(), sz.height.toFloat())
                windowManager.updateDesktopSize(desktopSizePx)
            },
    ) {
        WallpaperLayer(
            wallpaperUri = wallpaperUri,
            wallpaperVersion = wallpaperVersion,
            videoSound = videoSound,
        )

        Box(modifier = Modifier.fillMaxSize().padding(bottom = TaskbarHeight)) {
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
                                "linux_desktop" -> LinuxDesktopApp()
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
                    onExitMiniOS = { exitMiniOS(context) },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TaskbarHeight)
                .align(Alignment.BottomCenter)
                .zIndex(200_000f),
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
                onExitMiniOS = { exitMiniOS(context) },
            )
        }

        if (mouseEnabled) {
            VirtualMouseOverlay(
                windowManager = windowManager,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(300_000f),
            )
        }
    }
}

@Composable
private fun WallpaperLayer(
    wallpaperUri: String,
    wallpaperVersion: Long,
    videoSound: Boolean,
) {
    val context = LocalContext.current
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0D1117), Color(0xFF161B22), Color(0xFF21262D)),
    )
    val isVideo = isVideoPath(wallpaperUri)
    val isGif = wallpaperUri.endsWith(".gif", ignoreCase = true)

    Box(Modifier.fillMaxSize()) {
        if (isVideo) {
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
