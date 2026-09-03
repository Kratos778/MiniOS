package com.minios.elizierdias.shell.desktop

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.key
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

    /*
     * O WindowManager pertence à instância atual do Desktop.
     *
     * Isso permite que as janelas existentes continuem
     * sendo reutilizadas enquanto o Desktop recompõe.
     */
    val windowManager = remember {
        WindowManager()
    }

    /*
     * =========================================================
     * CONFIGURAÇÃO DO WALLPAPER
     * =========================================================
     */

    val wallpaperId by config.wallpaperId.collectAsState(
        initial = "default_gradient"
    )

    val wallpaperUri by config.wallpaperUri.collectAsState(
        initial = ""
    )

    val wallpaper = Wallpapers.byId(wallpaperId)

    /*
     * =========================================================
     * ESTADOS DO DESKTOP
     * =========================================================
     */

    var startMenuOpen by remember {
        mutableStateOf(false)
    }

    /*
     * Fonte única da verdade para o mouse.
     *
     * O Taskbar apenas altera este estado.
     * O VirtualMouse apenas lê este estado.
     */
    var mouseEnabled by remember {
        mutableStateOf(false)
    }

    /*
     * Tamanho REAL da área do Desktop.
     *
     * A Taskbar não faz parte desta área.
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
     * A Column separa:
     *
     * Desktop
     *   ↓
     * Taskbar
     *
     * O wallpaper fica exclusivamente dentro do Desktop.
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
                 * GIF ANIMADO
                 *
                 * AnimatedWallpaper é responsável pelo
                 * desenho/crop do GIF.
                 */
                isGifWallpaper -> {

                    AnimatedWallpaper(
                        source = wallpaperUri,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                /*
                 * IMAGEM PERSONALIZADA
                 */
                wallpaperUri.isNotBlank() -> {

                    Image(
                        painter = rememberAsyncImagePainter(
                            model = wallpaperUri
                        ),
                        contentDescription = "Wallpaper",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                /*
                 * WALLPAPER PADRÃO
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
             *
             * IMPORTANTE:
             *
             * Não removemos uma janela minimizada da
             * composição.
             *
             * Isso permite que aplicações como Browser e Files
             * mantenham o estado criado através de remember.
             *
             * key(instanceId) garante identidade estável mesmo
             * quando a ordem das janelas muda pelo zIndex.
             */
            windowManager.windows
                .sortedBy {
                    it.zIndex
                }
                .forEach { window ->

                    key(window.instanceId) {

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

                            /*
                             * =================================================
                             * CONTEÚDO DA APLICAÇÃO
                             * =================================================
                             *
                             * O conteúdo continua associado ao
                             * mesmo instanceId.
                             */
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

                                "browser" -> {

                                    BrowserApp()
                                }

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
             * O cursor existe apenas dentro da área do Desktop.
             *
             * Ele não invade a Taskbar porque a Taskbar está
             * fora deste Box.
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
         * A Taskbar é uma área independente do Desktop.
         *
         * O wallpaper e o VirtualMouse ficam somente na área
         * superior. A Taskbar não recebe o desenho do cursor.
         */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {

            Taskbar(

                /*
                 * Lista atual das janelas abertas.
                 *
                 * A Taskbar não cria nem destrói janelas.
                 * Apenas controla as instâncias existentes.
                 */
                openWindows =
                    windowManager.windows,

                /*
                 * Estado centralizado do mouse.
                 *
                 * O estado pertence ao Desktop.
                 */
                mouseEnabled =
                    mouseEnabled,

                /*
                 * O Taskbar informa ao Desktop quando
                 * o botão do mouse foi pressionado.
                 */
                onMouseToggle = { enabled ->

                    mouseEnabled = enabled
                },

                /*
                 * Abrir/fechar Start Menu.
                 */
                onStartClick = {

                    startMenuOpen =
                        !startMenuOpen
                },

                /*
                 * Controle das janelas através da Taskbar.
                 */
                onWindowClick = { id ->

                    val window =
                        windowManager.windows
                            .firstOrNull {
                                it.instanceId == id
                            }
                            ?: return@Taskbar

                    /*
                     * Se a janela está ativa e visível,
                     * clicar novamente no botão da Taskbar
                     * minimiza-a.
                     */
                    if (
                        window.isFocused &&
                        !window.isMinimized
                    ) {

                        windowManager.minimize(id)

                    } else {

                        /*
                         * Se está minimizada ou não focada,
                         * restauramos a MESMA instância.
                         *
                         * Não abrimos uma aplicação nova.
                         */
                        windowManager.restore(id)
                    }
                }
            )
        }
    }
}


/*
 * =============================================================
 * ÍCONE DO DESKTOP
 * =============================================================
 *
 * Cada aplicação registrada no AppRegistry recebe um ícone
 * clicável no Desktop.
 */
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

        /*
         * Ícone da aplicação.
         */
        Icon(
            imageVector =
                app.icon,

            contentDescription =
                app.title,

            tint =
                Color.White,

            modifier =
                Modifier.size(30.dp)
        )

        /*
         * Espaçamento entre o ícone e o nome.
         */
        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        /*
         * Nome da aplicação.
         */
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
