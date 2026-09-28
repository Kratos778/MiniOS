package com.minios.elizierdias.apps

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
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
     * Desktop sem Terminal/Linux por agora (subsistema Linux a refazer do zero).
     * Ordem fixa — ícones NÃO são arrastáveis.
     */
    val desktopIcons: List<MiniApp> =
        listOf(
            files,
            browser,
            softwareCenter,
            mediaPlayer,
            settings,
            smartPlay,
        )

    val all: List<MiniApp> = desktopIcons

    fun byId(id: String): MiniApp? =
        all.firstOrNull { it.id == id }
}
