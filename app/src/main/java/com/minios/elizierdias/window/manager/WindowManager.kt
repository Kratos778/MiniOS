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

    val windows: List<MiniWindow>
        get() = _windows

    private var nextZIndex = mutableStateOf(1)
    private var cascadeOffset = 0

    /** Última área útil do Desktop (acima da taskbar). */
    private var desktopBounds = Size(1280f, 676f)

    private data class NormalGeometry(
        val position: Offset,
        val size: Size,
    )

    private val normalGeometry = mutableMapOf<String, NormalGeometry>()

    fun updateDesktopSize(size: Size) {
        if (size.width > 0f && size.height > 0f) {
            desktopBounds = size
        }
    }

    fun openApp(app: MiniApp, desktopSize: Size): String {
        updateDesktopSize(desktopSize)

        val existing = _windows.firstOrNull { it.app.id == app.id && it.isMinimized }
        if (existing != null) {
            restore(existing.instanceId)
            return existing.instanceId
        }

        val instanceId = UUID.randomUUID().toString()

        // Tamanho inicial: nunca maior que 92% do desktop
        val maxW = (desktopSize.width * 0.92f).coerceAtLeast(MIN_WIDTH)
        val maxH = (desktopSize.height * 0.92f).coerceAtLeast(MIN_HEIGHT)
        val winSize = Size(
            width = app.defaultSize.width.coerceIn(MIN_WIDTH, maxW),
            height = app.defaultSize.height.coerceIn(MIN_HEIGHT, maxH),
        )

        val startPos = nextCascadePosition(desktopSize, winSize)

        _windows.replaceInPlace { it.copy(isFocused = false) }

        _windows.add(
            MiniWindow(
                instanceId = instanceId,
                app = app,
                position = startPos,
                size = winSize,
                zIndex = nextZIndex.value,
                isFocused = true,
            ),
        )
        nextZIndex.value += 1
        return instanceId
    }

    fun close(instanceId: String) {
        _windows.removeAll { it.instanceId == instanceId }
        normalGeometry.remove(instanceId)
    }

    fun focus(instanceId: String) {
        val target = _windows.firstOrNull { it.instanceId == instanceId } ?: return
        nextZIndex.value += 1
        val z = nextZIndex.value
        _windows.replaceInPlace { window ->
            if (window.instanceId == target.instanceId) {
                window.copy(zIndex = z, isFocused = true, isMinimized = false)
            } else {
                window.copy(isFocused = false)
            }
        }
    }

    fun move(instanceId: String, newPosition: Offset) {
        _windows.replaceInPlace { window ->
            if (window.instanceId != instanceId) return@replaceInPlace window
            if (window.isMaximized) return@replaceInPlace window
            window.copy(position = clampPosition(newPosition, window.size))
        }
    }

    fun resize(instanceId: String, newSize: Size) {
        _windows.replaceInPlace { window ->
            if (window.instanceId != instanceId) return@replaceInPlace window
            if (window.isMaximized) return@replaceInPlace window

            val safe = Size(
                width = newSize.width.coerceIn(MIN_WIDTH, desktopBounds.width),
                height = newSize.height.coerceIn(MIN_HEIGHT, desktopBounds.height),
            )
            // Mantém a janela dentro do desktop após resize
            val pos = clampPosition(window.position, safe)
            window.copy(size = safe, position = pos)
        }
    }

    fun minimize(instanceId: String) {
        _windows.replaceInPlace { window ->
            if (window.instanceId == instanceId) {
                window.copy(isMinimized = true, isFocused = false)
            } else window
        }
    }

    fun restore(instanceId: String) {
        if (_windows.none { it.instanceId == instanceId }) return
        focus(instanceId)
    }

    fun toggleMaximize(instanceId: String, desktopSize: Size) {
        updateDesktopSize(desktopSize)
        val target = _windows.firstOrNull { it.instanceId == instanceId } ?: return

        if (target.isMaximized) {
            val saved = normalGeometry[instanceId]
            _windows.replaceInPlace { window ->
                if (window.instanceId != instanceId) return@replaceInPlace window
                if (saved != null) {
                    window.copy(
                        position = clampPosition(saved.position, saved.size),
                        size = Size(
                            saved.size.width.coerceIn(MIN_WIDTH, desktopBounds.width),
                            saved.size.height.coerceIn(MIN_HEIGHT, desktopBounds.height),
                        ),
                        isMaximized = false,
                    )
                } else {
                    window.copy(isMaximized = false)
                }
            }
            normalGeometry.remove(instanceId)
        } else {
            normalGeometry[instanceId] = NormalGeometry(target.position, target.size)
            _windows.replaceInPlace { window ->
                if (window.instanceId != instanceId) return@replaceInPlace window
                window.copy(
                    position = Offset.Zero,
                    size = desktopSize,
                    isMaximized = true,
                )
            }
        }
        focus(instanceId)
    }

    fun hitTest(point: Offset): MiniWindow? =
        _windows
            .filter { !it.isMinimized }
            .sortedByDescending { it.zIndex }
            .firstOrNull { window ->
                point.x >= window.position.x &&
                    point.x <= window.position.x + window.size.width &&
                    point.y >= window.position.y &&
                    point.y <= window.position.y + window.size.height
            }

    /**
     * Título da janela (36px) tem de ficar sempre acessível no desktop.
     * Pelo menos TITLE_GRAB px de barra de título visível.
     */
    private fun clampPosition(pos: Offset, size: Size): Offset {
        val desk = desktopBounds
        val maxX = (desk.width - TITLE_GRAB).coerceAtLeast(0f)
        val minX = -(size.width - TITLE_GRAB).coerceAtLeast(0f)
        val maxY = (desk.height - TITLE_BAR).coerceAtLeast(0f)
        val minY = 0f // nunca acima do topo do desktop
        return Offset(
            x = pos.x.coerceIn(minX, maxX),
            y = pos.y.coerceIn(minY, maxY),
        )
    }

    private fun nextCascadePosition(desktopSize: Size, windowSize: Size): Offset {
        cascadeOffset = (cascadeOffset + 1) % 6
        val step = 28f
        var x = 40f + cascadeOffset * step
        var y = 40f + cascadeOffset * step
        if (x + windowSize.width > desktopSize.width) x = 40f
        if (y + windowSize.height > desktopSize.height) y = 40f
        return clampPosition(Offset(x, y), windowSize)
    }

    private inline fun <T> androidx.compose.runtime.snapshots.SnapshotStateList<T>.replaceInPlace(
        transform: (T) -> T,
    ) {
        for (i in indices) this[i] = transform(this[i])
    }

    companion object {
        const val MIN_WIDTH = 280f
        const val MIN_HEIGHT = 200f
        private const val TITLE_BAR = 36f
        private const val TITLE_GRAB = 80f
    }
}
