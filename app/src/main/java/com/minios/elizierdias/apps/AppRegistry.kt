package com.minios.elizierdias.apps

import androidx.compose.material.icons.Icons
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

    val settings =
        MiniApp(
            "settings",
            "Settings",
            Icons.Filled.Settings,
            defaultSize = Size(560f, 420f),
        )

    val softwareCenter =
        MiniApp(
            "software_center",
            "Software Center",
            Icons.Filled.Widgets,
            defaultSize = Size(540f, 400f),
        )

    val browser =
        MiniApp(
            "browser",
            "Browser",
            Icons.Filled.Language,
            // Mais largo tipo PC; o WindowManager limita a ~92% do ecrã
            defaultSize = Size(720f, 520f),
        )

    val mediaPlayer =
        MiniApp(
            "media_player",
            "MediaPlayerOS",
            Icons.Filled.Headphones,
            defaultSize = Size(560f, 420f),
        )

    val smartPlay =
        MiniApp(
            "smartplay",
            "SmartPlay",
            Icons.Filled.Language,
            defaultSize = Size(520f, 400f),
        )

    val all =
        listOf(
            files,
            terminal,
            settings,
            softwareCenter,
            browser,
            mediaPlayer,
            smartPlay,
        )

    fun byId(id: String): MiniApp? =
        all.firstOrNull { it.id == id }
}
