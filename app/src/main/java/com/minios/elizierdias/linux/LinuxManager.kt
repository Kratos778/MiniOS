/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 * This file is part of MiniOS.
 * See LICENSE and COPYRIGHT.md for full terms.
 *
 * Unauthorized copying, modification, distribution or reuse of this file,
 * via any medium, is strictly prohibited without prior written permission.
 */

package com.minios.elizierdias.linux

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central entry point for the MiniOS Linux subsystem.
 *
 * Etapa 2: prepares persistent directories for RootFS on private storage.
 * Later stages will add real runtime (PRoot / native), sessions and packages.
 */
class LinuxManager(
    private val context: Context,
) {

    private val rootFs = LinuxRootFs(context)

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _statusMessage = MutableStateFlow("Linux subsystem not initialized")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _rootFsStatus = MutableStateFlow<LinuxRootFs.Status?>(null)
    val rootFsStatus: StateFlow<LinuxRootFs.Status?> = _rootFsStatus.asStateFlow()

    private var currentSession: LinuxSession? = null

    /**
     * Initialize the Linux subsystem.
     * - Creates runtime / rootfs / downloads / bin directories
     * - Checks whether a RootFS is already marked as installed
     * - Updates status flows for UI
     */
    suspend fun initialize() {
        _statusMessage.value = "Preparing Linux runtime directories..."

        val prepareResult = rootFs.prepareForInstallation()
        if (prepareResult.isFailure) {
            _statusMessage.value = "Failed to prepare directories: ${prepareResult.exceptionOrNull()?.message}"
            _isReady.value = false
            LinuxConfig.setEnabled(false)
            return
        }

        val status = rootFs.status()
        _rootFsStatus.value = status

        if (status.isInstalled) {
            _statusMessage.value = "RootFS installed (${status.distro ?: "unknown"}) — runtime not connected yet"
            // Still not fully ready until native runtime is linked
            _isReady.value = false
            LinuxConfig.setEnabled(false)
        } else {
            _statusMessage.value = prepareResult.getOrNull()
                ?: "Directories ready. RootFS not installed yet."
            _isReady.value = false
            LinuxConfig.setEnabled(false)
        }
    }

    /** Refresh RootFS status (e.g. after external changes) */
    suspend fun refreshRootFsStatus() {
        _rootFsStatus.value = rootFs.status()
    }

    fun getRootFs(): LinuxRootFs = rootFs

    /**
     * Start a new Linux session (shell).
     * Returns null while the real runtime is not available.
     */
    fun startSession(): LinuxSession? {
        if (!LinuxConfig.enabled || !_isReady.value) {
            return null
        }
        val session = LinuxSession()
        currentSession = session
        return session
    }

    fun getCurrentSession(): LinuxSession? = currentSession

    fun shutdown() {
        currentSession?.close()
        currentSession = null
        _isReady.value = false
        _statusMessage.value = "Linux subsystem stopped"
        LinuxConfig.setEnabled(false)
    }
}
