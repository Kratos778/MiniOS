package com.minios.elizierdias.linux

import com.minios.elizierdias.core.NoskLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Tabela de processos Linux ativos (plano §11).
 * Thread-safe; o Runtime Service / Manager consulta isto.
 */
class ProcessTable {

    private val byId = ConcurrentHashMap<String, LinuxProcessHandle>()

    fun put(handle: LinuxProcessHandle) {
        byId[handle.id] = handle
        NoskLog.event(
            NoskLog.PROCESS,
            "table_put",
            to = handle.state.name,
            pid = handle.pid,
            detail = "id=${handle.id} role=${handle.role} cmd=${handle.command.take(80)}",
        )
    }

    fun get(id: String): LinuxProcessHandle? = byId[id]

    fun remove(id: String): LinuxProcessHandle? {
        val h = byId.remove(id)
        if (h != null) {
            NoskLog.event(
                NoskLog.PROCESS,
                "table_remove",
                from = h.state.name,
                pid = h.pid,
                exitCode = h.exitCode,
                detail = "id=$id role=${h.role}",
            )
        }
        return h
    }

    fun all(): List<LinuxProcessHandle> = byId.values.toList()

    fun byRole(role: String): List<LinuxProcessHandle> =
        byId.values.filter { it.role == role }

    fun alive(): List<LinuxProcessHandle> =
        byId.values.filter { it.isAlive() }

    fun markExited(id: String, exitCode: Int) {
        val h = byId[id] ?: return
        h.markExited(exitCode)
        NoskLog.event(
            NoskLog.PROCESS,
            "exited",
            from = ProcessState.RUNNING.name,
            to = ProcessState.EXITED.name,
            pid = h.pid,
            exitCode = exitCode,
            detail = "id=$id role=${h.role}",
        )
    }

    fun clearDead() {
        val dead = byId.filterValues { !it.isAlive() }.keys
        dead.forEach { byId.remove(it) }
    }

    fun snapshot(): String = buildString {
        appendLine("ProcessTable size=${byId.size}")
        byId.values.sortedBy { it.role }.forEach { h ->
            appendLine(
                "  [${h.role}] id=${h.id} pid=${h.pid} state=${h.state} " +
                    "exit=${h.exitCode} cmd=${h.command.take(60)}",
            )
        }
    }
}
