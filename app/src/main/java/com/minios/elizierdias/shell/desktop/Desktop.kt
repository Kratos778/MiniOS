package com.minios.elizierdias.shell.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.minios.elizierdias.apps.AppRegistry
import com.minios.elizierdias.apps.files.FilesApp
import com.minios.elizierdias.apps.settings.SettingsApp
import com.minios.elizierdias.apps.softwarecenter.SoftwareCenterApp
import com.minios.elizierdias.apps.terminal.TerminalApp
import com.minios.elizierdias.core.MiniApp
import com.minios.elizierdias.core.MiniOSConfig
import com.minios.elizierdias.core.MouseButton
import com.minios.elizierdias.input.VirtualMouseOverlay
import com.minios.elizierdias.personalization.Wallpapers
import com.minios.elizierdias.shell.startmenu.StartMenu
import com.minios.elizierdias.shell.taskbar.Taskbar
import com.minios.elizierdias.window.frame.WindowFrame
import com.minios.elizierdias.window.manager.WindowManager

@Composable
fun Desktop() {
    val context = LocalContext.current
    val config = remember { MiniOSConfig(context) }
    val windowManager = remember { WindowManager() }
    val wallpaperId by config.wallpaperId.collectAsState(initial = "default_gradient")
    val wallpaperUri by config.wallpaperUri.collectAsState(initial = "")
    val wallpaper = Wallpapers.byId(wallpaperId)
    var startMenuOpen by remember { mutableStateOf(false) }
    var desktopSizePx by remember { mutableStateOf(Size(1280f, 720f)) }
    var lastClickHint by remember { mutableStateOf("toque curto = ESQ · longo = DIR") }
    val iconLayout = remember {
        AppRegistry.all.mapIndexed { i, app -> app to Offset(24f, 24f + i * 88f) }
    }

    fun handleMouseClick(pos: Offset, button: MouseButton) {
        lastClickHint = if (button == MouseButton.LEFT) "clique ESQUERDO" else "clique DIREITO"
        val hitWindow = windowManager.hitTest(pos)
        if (hitWindow != null) { windowManager.focus(hitWindow.instanceId); return }
        val hitIcon = iconLayout.firstOrNull { (_, o) ->
            pos.x >= o.x && pos.x <= o.x + 72f && pos.y >= o.y && pos.y <= o.y + 72f
        }
        if (hitIcon != null) { windowManager.openApp(hitIcon.first, desktopSizePx); return }
        if (startMenuOpen) startMenuOpen = false
    }

    Box(Modifier.fillMaxSize().onSizeChanged {
        desktopSizePx = Size(it.width.toFloat(), it.height.toFloat())
    }) {
        if (wallpaperUri.isNotEmpty()) {
            Image(
                painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(context).data(wallpaperUri).crossfade(true).build()
                ),
                contentDescription = "Wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize().background(wallpaper.brush))
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            AppRegistry.all.forEach { app ->
                DesktopIcon(app) { windowManager.openApp(app, desktopSizePx) }
            }
        }

        windowManager.windows.sortedBy { it.zIndex }.forEach { window ->
            WindowFrame(
                window = window,
                onFocus = { windowManager.focus(window.instanceId) },
                onMove = { windowManager.move(window.instanceId, it) },
                onResize = { windowManager.resize(window.instanceId, it) },
                onClose = { windowManager.close(window.instanceId) },
                onMinimize = { windowManager.minimize(window.instanceId) },
                onToggleMaximize = { windowManager.toggleMaximize(window.instanceId, desktopSizePx) },
            ) {
                when (window.app.id) {
                    "files" -> FilesApp()
                    "terminal" -> TerminalApp()
                    "settings" -> SettingsApp()
                    "software_center" -> SoftwareCenterApp()
                    else -> Text("App: ${window.app.id}", color = Color.White)
                }
            }
        }

        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            Taskbar(
                openWindows = windowManager.windows,
                onStartClick = { startMenuOpen = !startMenuOpen },
                onWindowClick = { id ->
                    val w = windowManager.windows.firstOrNull { it.instanceId == id } ?: return@Taskbar
                    if (w.isFocused && !w.isMinimized) windowManager.minimize(id)
                    else windowManager.restore(id)
                },
                mouseHint = lastClickHint,
            )
        }

        if (startMenuOpen) {
            StartMenu(
                apps = AppRegistry.all,
                onAppClick = { windowManager.openApp(it, desktopSizePx) },
                onDismiss = { startMenuOpen = false },
            )
        }

        VirtualMouseOverlay(
            enabled = true,
            pointerOffset = Offset(-20f, -52f),
            onClick = { pos, button -> handleMouseClick(pos, button) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopIcon(app: MiniApp, onOpen: () -> Unit) {
    Column(
        Modifier.width(72.dp).combinedClickable(onDoubleClick = onOpen, onClick = {}),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(app.icon, app.title, tint = Color.White, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(4.dp))
        Text(app.title, color = Color.White, fontSize = 11.sp)
    }
}
