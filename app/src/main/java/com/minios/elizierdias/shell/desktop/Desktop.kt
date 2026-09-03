package com.minios.elizierdias.shell.desktop

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.minios.elizierdias.apps.AppRegistry
import com.minios.elizierdias.apps.browser.BrowserApp
import com.minios.elizierdias.apps.files.FilesApp
import com.minios.elizierdias.apps.settings.SettingsApp
import com.minios.elizierdias.apps.softwarecenter.SoftwareCenterApp
import com.minios.elizierdias.apps.terminal.TerminalApp
import com.minios.elizierdias.core.MiniApp
import com.minios.elizierdias.core.MiniOSConfig
import com.minios.elizierdias.personalization.AnimatedWallpaper
import com.minios.elizierdias.personalization.Wallpapers
import com.minios.elizierdias.shell.mouse.VirtualMouse
import com.minios.elizierdias.shell.startmenu.StartMenu
import com.minios.elizierdias.shell.taskbar.Taskbar
import com.minios.elizierdias.window.frame.WindowFrame
import com.minios.elizierdias.window.manager.WindowManager

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

    val wallpaper = Wallpapers.byId(wallpaperId)

    var startMenuOpen by remember {
        mutableStateOf(false)
    }

    /*
     * Mouse state.
     *
     * A Taskbar controla este estado futuramente.
     * Por enquanto começa desligado para não bloquear
     * a interação normal do MiniOS.
     */
    var mouseEnabled by remember {
        mutableStateOf(false)
    }

    /*
     * Tamanho REAL da área do desktop.
     *
     * A Taskbar fica fora desta área.
     */
    var desktopSizePx by remember {
        mutableStateOf(
            Size(
                1280f,
                676f
            )
        )
    }

    val isGifWallpaper =
        wallpaperUri.endsWith(
            ".gif",
            ignoreCase = true
        )

    /*
     * =========================================================
     * MINI OS
     * =========================================================
     *
     * A Column divide a tela em duas partes:
     *
     * 1. Desktop  -> ocupa todo o espaço restante
     * 2. Taskbar  -> 44dp fixos
     *
     * Isso garante que o wallpaper nunca fique atrás
     * da Taskbar.
     */
    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        /*
         * =====================================================
         * ÁREA DO DESKTOP
         * =====================================================
         */
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size ->

                    desktopSizePx = Size(
                        size.width.toFloat(),
                        size.height.toFloat()
                    )
                }
        ) {

            /*
             * =================================================
             * WALLPAPER
             * =================================================
             */

            when {

                /*
                 * GIF animado
                 */
                isGifWallpaper -> {

                    AnimatedWallpaper(
                        source = wallpaperUri,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                /*
                 * Imagem personalizada
                 */
                wallpaperUri.isNotBlank() -> {

                    androidx.compose.foundation.Image(
                        painter = rememberAsyncImagePainter(
                            model = wallpaperUri
                        ),
                        contentDescription = "Wallpaper",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                /*
                 * Wallpaper padrão
                 */
                else -> {

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                wallpaper.brush
                            )
                    )
                }
            }

            /*
             * =================================================
             * ÍCONES DO DESKTOP
             * =================================================
             */

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
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
             * =================================================
             * JANELAS
             * =================================================
             */

            windowManager.windows
                .sortedBy {
                    it.zIndex
                }
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

                                "files" ->
                                    FilesApp()

                                "terminal" ->
                                    TerminalApp()

                                "settings" ->
                                    SettingsApp()

                                "software_center" ->
                                    SoftwareCenterApp()

                                "browser" ->
                                    BrowserApp()

                                else -> {

                                    Text(
                                        text =
                                            "App: ${window.app.id}",

                                        color =
                                            Color.White
                                    )
                                }
                            }
                        }
                    }
                }

            /*
             * =================================================
             * START MENU
             * =================================================
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

            /*
             * =================================================
             * VIRTUAL MOUSE
             * =================================================
             *
             * Fica SOMENTE dentro do Desktop.
             *
             * Portanto nunca aparece sobre a Taskbar.
             */
            VirtualMouse(
                enabled = mouseEnabled,
                modifier = Modifier.fillMaxSize()
            )
        }

        /*
         * =====================================================
         * TASKBAR
         * =====================================================
         *
         * Área independente.
         *
         * O wallpaper termina exatamente acima daqui.
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
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

            contentDescription =
                app.title,

            tint =
                Color.White,

            modifier =
                Modifier.size(30.dp)
        )

        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        Text(
            text =
                app.title,

            color =
                Color.White,

            fontSize =
                11.sp
        )
    }
}
