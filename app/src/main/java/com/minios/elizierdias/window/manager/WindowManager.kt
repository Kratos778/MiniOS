package com.minios.elizierdias.window.manager

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.minios.elizierdias.core.MiniApp
import com.minios.elizierdias.core.MiniWindow
import java.util.UUID

class WindowManager {
    private val _windows = mutableStateListOf<MiniWindow>()
    val windows: List<MiniWindow> get() = _windows
    private var nextZIndex = mutableStateOf(1)
    private var cascadeOffset = 0

    fun openApp(app: MiniApp, desktopSize: Size): String {
        val existing = _windows.firstOrNull { it.app.id == app.id && it.isMinimized }
        if (existing != null) {
            restore(existing.instanceId)
            return existing.instanceId
        }
        val instanceId = UUID.randomUUID().toString()
        val startPos = nextCascadePosition(desktopSize, app.defaultSize)
        val window = MiniWindow(
            instanceId = instanceId, app = app, position = startPos,
            size = app.defaultSize, zIndex = nextZIndex.value, isFocused = true,
        )
        nextZIndex.value += 1
        _windows.replaceAll { it.copy(isFocused = false) }
        _windows.add(window)
        return instanceId
    }

    fun close(instanceId: String) { _windows.removeAll { it.instanceId == instanceId } }

    fun focus(instanceId: String) {
        nextZIndex.value += 1
        val z = nextZIndex.value
        _windows.replaceInPlace { w ->
            if (w.instanceId == instanceId) w.copy(zIndex = z, isFocused = true, isMinimized = false)
            else w.copy(isFocused = false)
        }
    }

    fun move(instanceId: String, newPosition: Offset) {
        _windows.replaceInPlace { w ->
            if (w.instanceId == instanceId) w.copy(position = newPosition) else w
        }
    }

    fun resize(instanceId: String, newSize: Size) {
        _windows.replaceInPlace { w ->
            if (w.instanceId == instanceId) w.copy(
                size = Size(newSize.width.coerceAtLeast(MIN_WIDTH), newSize.height.coerceAtLeast(MIN_HEIGHT))
            ) else w
        }
    }

    fun minimize(instanceId: String) {
        _windows.replaceInPlace { w ->
            if (w.instanceId == instanceId) w.copy(isMinimized = true, isFocused = false) else w
        }
    }

    fun restore(instanceId: String) = focus(instanceId)

    fun toggleMaximize(instanceId: String, desktopSize: Size) {
        _windows.replaceInPlace { w ->
            if (w.instanceId != instanceId) return@replaceInPlace w
            if (w.isMaximized) w.copy(isMaximized = false, size = w.app.defaultSize, position = Offset(60f, 40f))
            else w.copy(isMaximized = true, position = Offset.Zero, size = desktopSize)
        }
        focus(instanceId)
    }

    fun hitTest(point: Offset): MiniWindow? =
        _windows.filter { !it.isMinimized }.sortedByDescending { it.zIndex }.firstOrNull { w ->
            point.x >= w.position.x && point.x <= w.position.x + w.size.width &&
                point.y >= w.position.y && point.y <= w.position.y + w.size.height
        }

    private fun nextCascadePosition(desktopSize: Size, windowSize: Size): Offset {
        cascadeOffset = (cascadeOffset + 1) % 6
        val step = 28f
        var x = 80f + cascadeOffset * step
        var y = 60f + cascadeOffset * step
        if (x + windowSize.width > desktopSize.width) x = 80f
        if (y + windowSize.height > desktopSize.height) y = 60f
        return Offset(x, y)
    }

    private inline fun <T> androidx.compose.runtime.snapshots.SnapshotStateList<T>.replaceInPlace(transform: (T) -> T) {
        for (i in indices) this[i] = transform(this[i])
    }

    companion object {
        const val MIN_WIDTH = 280f
        const val MIN_HEIGHT = 200f
    }
}
