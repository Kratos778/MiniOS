package com.minios.elizierdias.window.manager

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.minios.elizierdias.core.MiniApp
import com.minios.elizierdias.core.MiniWindow
import java.util.UUID

class WindowManager {

    private val _windows =
        mutableStateListOf<MiniWindow>()

    val windows: List<MiniWindow>
        get() = _windows

    private var nextZIndex =
        mutableStateOf(1)

    private var cascadeOffset = 0

    /*
     * Guarda a posição e o tamanho da janela
     * antes de ela ser maximizada.
     *
     * Não precisamos alterar MiniWindow para isso.
     */
    private data class NormalGeometry(
        val position: Offset,
        val size: Size,
    )

    private val normalGeometry =
        mutableMapOf<String, NormalGeometry>()

    fun openApp(
        app: MiniApp,
        desktopSize: Size,
    ): String {

        /*
         * Se a aplicação já possui uma janela minimizada,
         * restauramos a mesma instância em vez de criar
         * uma nova.
         */
        val existing =
            _windows.firstOrNull {
                it.app.id == app.id &&
                    it.isMinimized
            }

        if (existing != null) {
            restore(existing.instanceId)
            return existing.instanceId
        }

        val instanceId =
            UUID.randomUUID().toString()

        val startPos =
            nextCascadePosition(
                desktopSize,
                app.defaultSize,
            )

        /*
         * Todas as outras janelas perdem o foco.
         */
        _windows.replaceInPlace {
            it.copy(isFocused = false)
        }

        val window =
            MiniWindow(
                instanceId = instanceId,
                app = app,
                position = startPos,
                size = app.defaultSize,
                zIndex = nextZIndex.value,
                isFocused = true,
            )

        nextZIndex.value += 1

        _windows.add(window)

        return instanceId
    }

    fun close(
        instanceId: String,
    ) {
        _windows.removeAll {
            it.instanceId == instanceId
        }

        /*
         * A janela deixou de existir.
         * A geometria antiga também deixa de ser necessária.
         */
        normalGeometry.remove(instanceId)
    }

    fun focus(
        instanceId: String,
    ) {
        val target =
            _windows.firstOrNull {
                it.instanceId == instanceId
            }
                ?: return

        nextZIndex.value += 1

        val z =
            nextZIndex.value

        _windows.replaceInPlace { window ->
            if (window.instanceId == target.instanceId) {
                window.copy(
                    zIndex = z,
                    isFocused = true,
                    isMinimized = false,
                )
            } else {
                window.copy(
                    isFocused = false,
                )
            }
        }
    }

    fun move(
        instanceId: String,
        newPosition: Offset,
    ) {
        _windows.replaceInPlace { window ->

            if (window.instanceId != instanceId) {
                return@replaceInPlace window
            }

            /*
             * Se a janela estiver maximizada,
             * não alteramos a posição normal salva.
             *
             * Isso garante que, ao sair do maximizado,
             * ela volte exatamente para onde estava.
             */
            if (window.isMaximized) {
                window
            } else {
                window.copy(
                    position = newPosition,
                )
            }
        }
    }

    fun resize(
        instanceId: String,
        newSize: Size,
    ) {
        _windows.replaceInPlace { window ->

            if (window.instanceId != instanceId) {
                return@replaceInPlace window
            }

            /*
             * Tamanho mínimo da janela.
             */
            val safeSize =
                Size(
                    width =
                        newSize.width
                            .coerceAtLeast(MIN_WIDTH),
                    height =
                        newSize.height
                            .coerceAtLeast(MIN_HEIGHT),
                )

            /*
             * Não permitimos que o resize durante
             * o maximizado destrua o tamanho normal salvo.
             */
            if (window.isMaximized) {
                window
            } else {
                window.copy(
                    size = safeSize,
                )
            }
        }
    }

    fun minimize(
        instanceId: String,
    ) {
        _windows.replaceInPlace { window ->

            if (window.instanceId == instanceId) {
                window.copy(
                    isMinimized = true,
                    isFocused = false,
                )
            } else {
                window
            }
        }
    }

    /*
     * Restaura exatamente a mesma janela.
     *
     * A posição, tamanho, maximização e estado interno
     * da app continuam preservados.
     */
    fun restore(
        instanceId: String,
    ) {
        val exists =
            _windows.any {
                it.instanceId == instanceId
            }

        if (!exists) {
            return
        }

        focus(instanceId)
    }

    fun toggleMaximize(
        instanceId: String,
        desktopSize: Size,
    ) {
        val target =
            _windows.firstOrNull {
                it.instanceId == instanceId
            }
                ?: return

        if (target.isMaximized) {

            /*
             * DESMAXIMIZAR
             *
             * Se temos uma geometria salva, restauramos
             * exatamente a posição e o tamanho anteriores.
             */
            val saved =
                normalGeometry[instanceId]

            _windows.replaceInPlace { window ->

                if (window.instanceId != instanceId) {
                    return@replaceInPlace window
                }

                if (saved != null) {
                    window.copy(
                        position = saved.position,
                        size = saved.size,
                        isMaximized = false,
                    )
                } else {
                    /*
                     * Fallback para janelas antigas que,
                     * por alguma razão, não tenham geometria
                     * registrada.
                     */
                    window.copy(
                        isMaximized = false,
                    )
                }
            }

            /*
             * Depois de restaurar, a geometria salva não
             * precisa mais ficar armazenada.
             */
            normalGeometry.remove(instanceId)

        } else {

            /*
             * MAXIMIZAR
             *
             * Primeiro guardamos a geometria atual.
             */
            normalGeometry[instanceId] =
                NormalGeometry(
                    position = target.position,
                    size = target.size,
                )

            /*
             * Depois ocupamos exatamente a área do Desktop.
             */
            _windows.replaceInPlace { window ->

                if (window.instanceId != instanceId) {
                    return@replaceInPlace window
                }

                window.copy(
                    position = Offset.Zero,
                    size = desktopSize,
                    isMaximized = true,
                )
            }
        }

        /*
         * A janela maximizada/desmaximizada continua sendo
         * a janela ativa.
         */
        focus(instanceId)
    }

    fun hitTest(
        point: Offset,
    ): MiniWindow? =
        _windows
            .filter {
                !it.isMinimized
            }
            .sortedByDescending {
                it.zIndex
            }
            .firstOrNull { window ->

                point.x >= window.position.x &&
                    point.x <=
                    window.position.x +
                        window.size.width &&

                    point.y >= window.position.y &&
                    point.y <=
                    window.position.y +
                        window.size.height
            }

    private fun nextCascadePosition(
        desktopSize: Size,
        windowSize: Size,
    ): Offset {

        cascadeOffset =
            (cascadeOffset + 1) % 6

        val step = 28f

        var x =
            80f +
                cascadeOffset * step

        var y =
            60f +
                cascadeOffset * step

        if (
            x + windowSize.width >
            desktopSize.width
        ) {
            x = 80f
        }

        if (
            y + windowSize.height >
            desktopSize.height
        ) {
            y = 60f
        }

        return Offset(
            x,
            y,
        )
    }

    private inline fun <T>
        androidx.compose.runtime.snapshots.SnapshotStateList<T>
        .replaceInPlace(
            transform: (T) -> T,
        ) {
        for (i in indices) {
            this[i] =
                transform(this[i])
        }
    }

    companion object {
        const val MIN_WIDTH = 280f
        const val MIN_HEIGHT = 200f
    }
}
