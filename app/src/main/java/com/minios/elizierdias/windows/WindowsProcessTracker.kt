/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS / NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.windows

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Regista processos Windows iniciados através do Wine.
 * Permite listar, terminar e associar processos a janelas.
 */
class WindowsProcessTracker {

    private val processes = ConcurrentHashMap<String, WindowsProcessInfo>()

    private val _activeProcesses = MutableStateFlow<List<WindowsProcessInfo>>(emptyList())
    val activeProcesses: StateFlow<List<WindowsProcessInfo>> = _activeProcesses.asStateFlow()

    fun register(
        executablePath: String,
        prefixName: String,
        displayName: String,
    ): String {
        val id = UUID.randomUUID().toString()
        val info = WindowsProcessInfo(
            processId = id,
            executablePath = executablePath,
            prefixName = prefixName,
            startedAt = System.currentTimeMillis(),
            displayName = displayName,
            isRunning = true,
        )
        processes[id] = info
        publish()
        return id
    }

    fun markFinished(processId: String) {
        processes[processId]?.let {
            processes[processId] = it.copy(isRunning = false)
            publish()
        }
    }

    fun remove(processId: String) {
        processes.remove(processId)
        publish()
    }

    fun get(processId: String): WindowsProcessInfo? = processes[processId]

    fun listRunning(): List<WindowsProcessInfo> =
        processes.values.filter { it.isRunning }

    fun clearFinished() {
        val toRemove = processes.filter { !it.value.isRunning }.keys
        toRemove.forEach { processes.remove(it) }
        publish()
    }

    private fun publish() {
        _activeProcesses.value = processes.values.sortedByDescending { it.startedAt }
    }
}
