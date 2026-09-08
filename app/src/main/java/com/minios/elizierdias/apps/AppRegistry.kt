package com.minios.elizierdias.apps

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.geometry.Size
import com.minios.elizierdias.core.MiniApp

object AppRegistry {

    val files =
        MiniApp(
            "files",
            "Files",
            Icons.Filled.Folder,
            defaultSize = Size(520f, 400f),
        )

    val terminal =
        MiniApp(
            "terminal",
            "Terminal",
            Icons.Filled.Terminal,
            defaultSize = Size(520f, 360f),
        )

    val browser =
        MiniApp(
            "browser",
            "Browser",
            Icons.Filled.Language,
            defaultSize = Size(720f, 520f),
        )

    val softwareCenter =
        MiniApp(
            "software_center",
            "Software",
            Icons.Filled.Widgets,
            defaultSize = Size(540f, 400f),
        )

    val mediaPlayer =
        MiniApp(
            "media_player",
            "Media",
            Icons.Filled.Headphones,
            defaultSize = Size(560f, 420f),
        )

    val linuxDesktop =
        MiniApp(
            "linux_desktop",
            "Linux",
            Icons.Filled.Computer,
            defaultSize = Size(900f, 560f),
        )

    val settings =
        MiniApp(
            "settings",
            "Settings",
            Icons.Filled.Settings,
            defaultSize = Size(560f, 420f),
        )

    val smartPlay =
        MiniApp(
            "smartplay",
            "SmartPlay",
            Icons.Filled.Language,
            defaultSize = Size(520f, 400f),
        )

    /**
     * Ordem fixa no desktop (grelha 2 colunas).
     * Labels curtos para caber no ecrã.
     */
    val desktopIcons: List<MiniApp> =
        listOf(
            files,
            terminal,
            browser,
            softwareCenter,
            mediaPlayer,
            linuxDesktop,
            settings,
            smartPlay,
        )

    val all: List<MiniApp> = desktopIcons

    fun byId(id: String): MiniApp? =
        all.firstOrNull { it.id == id }
}
