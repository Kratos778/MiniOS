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
            defaultSize = Size(560f, 400f),
        )

    val terminal =
        MiniApp(
            "terminal",
            "Terminal",
            Icons.Filled.Terminal,
            defaultSize = Size(560f, 360f),
        )

    val settings =
        MiniApp(
            "settings",
            "Settings",
            Icons.Filled.Settings,
            defaultSize = Size(520f, 420f),
        )

    val softwareCenter =
        MiniApp(
            "software_center",
            "Software Center",
            Icons.Filled.Widgets,
            defaultSize = Size(600f, 420f),
        )

    val browser =
        MiniApp(
            "browser",
            "Browser",
            Icons.Filled.Language,
            defaultSize = Size(900f, 620f),
        )

    val mediaPlayer =
        MiniApp(
            "media_player",
            "MediaPlayerOS",
            Icons.Filled.Headphones,
            defaultSize = Size(900f, 560f),
        )

    val smartPlay =
        MiniApp(
            "smartplay",
            "SmartPlay",
            Icons.Filled.Language,
            defaultSize = Size(900f, 620f),
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
        all.firstOrNull {
            it.id == id
        }
}
