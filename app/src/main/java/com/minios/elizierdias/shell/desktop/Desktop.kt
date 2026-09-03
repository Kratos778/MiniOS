package com.minios.elizierdias.shell.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.minios.elizierdias.personalization.Wallpapers
import com.minios.elizierdias.shell.startmenu.StartMenu
import com.minios.elizierdias.shell.taskbar.Taskbar
import com.minios.elizierdias.window.frame.WindowFrame
import com.minios.elizierdias.window.manager.WindowManager
import java.io.File

@Composable
fun Desktop() {

    val context = LocalContext.current

    val config = remember(context) {
        MiniOSConfig(context)
    }

    val windowManager = remember {
        WindowManager()
    }

    val wallpaperId by config.wallpaperId.collectAsState(
        initial = "default_gradient"
    )

    val wallpaperUri by config.wallpaperUri.collectAsState(
        initial = ""
    )

    val wallpaper =
        Wallpapers.byId(wallpaperId)

    var startMenuOpen by remember {
        mutableStateOf(false)
    }

    var desktopSizePx by remember {
        mutableStateOf(
            Size(
                width = 1280f,
                height = 720f
            )
        )
    }

    val wallpaperFile =
        remember(wallpaperUri) {

            if (
                wallpaperUri.isNotEmpty() &&
                wallpaperUri.startsWith("/")
            ) {
                File(wallpaperUri)
            } else {
                null
            }
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->

                desktopSizePx =
                    Size(
                        width = size.width.toFloat(),
                        height = size.height.toFloat()
                    )
            }
    ) {

        /*
         * Wallpaper
         */

        if (wallpaperFile != null) {

            Image(
                painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context)
                        .data(wallpaperFile)
                        .memoryCacheKey(
                            "minios_wallpaper_$wallpaperUri"
                        )
                        .diskCacheKey(
                            "minios_wallpaper_$wallpaperUri"
                        )
                        .crossfade(true)
                        .build()
                ),
                contentDescription = "Wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

        } else {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(wallpaper.brush)
            )
        }

        /*
         * Desktop icons
         */

        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(18.dp)
        ) {

            AppRegistry.all.forEach { app ->

                DesktopIcon(
                    app = app,
                    onOpen = {

                        startMenuOpen = false

                        windowManager.openApp(
                            app,
                            desktopSizePx
                        )
                    }
                )
            }
        }

        /*
         * Application windows
         */

        windowManager.windows
            .sortedBy { it.zIndex }
            .forEach { window ->

                if (!window.isMinimized) {

                    WindowFrame(
                        window = window,

                        onFocus = {
                            windowManager.focus(
                                window.instanceId
                            )
                        },

                        onMove = { position ->
                            windowManager.move(
                                window.instanceId,
                                position
                            )
                        },

                        onResize = { size ->
                            windowManager.resize(
                                window.instanceId,
                                size
                            )
                        },

                        onClose = {
                            windowManager.close(
                                window.instanceId
                            )
                        },

                        onMinimize = {
                            windowManager.minimize(
                                window.instanceId
                            )
                        },

                        onToggleMaximize = {
                            windowManager.toggleMaximize(
                                window.instanceId,
                                desktopSizePx
                            )
                        }
                    ) {

                        when (window.app.id) {

                            "files" -> {
                                FilesApp()
                            }

                            "terminal" -> {
                                TerminalApp()
                            }

                            "settings" -> {
                                SettingsApp()
                            }

                            "software_center" -> {
                                SoftwareCenterApp()
                            }

                            else -> {
                                Text(
                                    text =
                                        "App: ${window.app.id}",
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

        /*
         * Taskbar
         */

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
        ) {

            Taskbar(
                openWindows =
                    windowManager.windows,

                onStartClick = {
                    startMenuOpen =
                        !startMenuOpen
                },

                onWindowClick = { id ->

                    val window =
                        windowManager.windows
                            .firstOrNull {
                                it.instanceId == id
                            }
                            ?: return@Taskbar

                    if (
                        window.isFocused &&
                        !window.isMinimized
                    ) {

                        windowManager.minimize(id)

                    } else {

                        windowManager.restore(id)
                    }
                }
            )
        }

        /*
         * Start Menu
         */

        if (startMenuOpen) {

            StartMenu(
                apps = AppRegistry.all,

                onAppClick = { app ->

                    startMenuOpen = false

                    windowManager.openApp(
                        app,
                        desktopSizePx
                    )
                },

                onDismiss = {
                    startMenuOpen = false
                }
            )
        }
    }
}

@Composable
private fun DesktopIcon(
    app: MiniApp,
    onOpen: () -> Unit
) {

    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable {
                onOpen()
            },
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Icon(
            imageVector = app.icon,
            contentDescription = app.title,
            tint = Color.White,
            modifier = Modifier.size(30.dp)
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = app.title,
            color = Color.White,
            fontSize = 11.sp
        )
    }
}
