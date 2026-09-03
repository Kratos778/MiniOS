package com.minios.elizierdias.core

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
data class MiniApp(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val kind: AppKind = AppKind.NATIVE,
    val defaultSize: Size = Size(520f, 380f),
)

enum class AppKind { NATIVE, LINUX }

@Immutable
data class MiniWindow(
    val instanceId: String,
    val app: MiniApp,
    val position: Offset,
    val size: Size,
    val zIndex: Int,
    val isMinimized: Boolean = false,
    val isMaximized: Boolean = false,
    val isFocused: Boolean = false,
)

enum class PowerMode { PERFORMANCE, BALANCED, BATTERY_SAVER }

enum class MouseButton { LEFT, RIGHT }
