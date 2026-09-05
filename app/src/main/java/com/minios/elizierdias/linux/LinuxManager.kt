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

    private val _installProgress = MutableStateFlow<String?>(null)
    val installProgress: StateFlow<String?> = _installProgress.asStateFlow()

    private var currentSession: LinuxSession? = null

    suspend fun initialize() {
        _statusMessage.value = "Preparing Linux runtime directories..."

        val prepareResult = rootFs.prepareForInstallation()
        if (prepareResult.isFailure) {
            _statusMessage.value =
                "Failed to prepare directories: ${prepareResult.exceptionOrNull()?.message}"
            _isReady.value = false
            LinuxConfig.setEnabled(false)
            return
        }

        val status = rootFs.status()
        _rootFsStatus.value = status

        if (status.isInstalled) {
            _statusMessage.value =
                "RootFS installed (${status.distro ?: "unknown"}) — runtime not connected yet"
            _isReady.value = false
            LinuxConfig.setEnabled(false)
        } else {
            _statusMessage.value = prepareResult.getOrNull()
                ?: "Directories ready. RootFS not installed yet."
            _isReady.value = false
            LinuxConfig.setEnabled(false)
        }
    }

    /**
     * Download + extract Debian ARM64 RootFS.
     * Call from Terminal with command `install`.
     */
    suspend fun installRootFs(): Result<Unit> {
        _installProgress.value = "Starting RootFS installation..."
        val result = rootFs.install { msg ->
            _installProgress.value = msg
            _statusMessage.value = msg
        }
        _rootFsStatus.value = rootFs.status()
        if (result.isSuccess) {
            _statusMessage.value =
                "RootFS installed (${_rootFsStatus.value?.distro}) — runtime still pending"
        }
        return result
    }

    suspend fun refreshRootFsStatus() {
        _rootFsStatus.value = rootFs.status()
    }

    fun getRootFs(): LinuxRootFs = rootFs

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
