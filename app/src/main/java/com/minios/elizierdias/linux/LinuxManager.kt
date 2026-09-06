/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.linux

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LinuxManager(
    private val context: Context,
) {

    private val rootFs = LinuxRootFs(context)
    private val runtime = LinuxRuntime(context)

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
        _statusMessage.value = "Preparing Linux runtime..."
        rootFs.prepareForInstallation()
        val status = rootFs.status()
        _rootFsStatus.value = status

        val rootOk = status.isInstalled
        val prootOk = runtime.isProotInstalled()

        when {
            rootOk && prootOk -> {
                _statusMessage.value = "Linux ready (RootFS + PRoot)"
                _isReady.value = true
                LinuxConfig.setEnabled(true)
            }
            rootOk && !prootOk -> {
                _statusMessage.value = "RootFS OK — run: setup-runtime"
                _isReady.value = false
                LinuxConfig.setEnabled(false)
            }
            else -> {
                _statusMessage.value = "Run: install   then: setup-runtime"
                _isReady.value = false
                LinuxConfig.setEnabled(false)
            }
        }
    }

    suspend fun installRootFs(): Result<Unit> {
        _installProgress.value = "Starting RootFS installation..."
        val result = rootFs.install { msg ->
            _installProgress.value = msg
            _statusMessage.value = msg
        }
        _rootFsStatus.value = rootFs.status()
        if (result.isSuccess) {
            _statusMessage.value = "RootFS installed — next: setup-runtime"
        }
        return result
    }

    /**
     * Wipe broken RootFS (e.g. missing symlinks) and install from scratch.
     */
    suspend fun reinstallRootFs(): Result<Unit> {
        _isReady.value = false
        LinuxConfig.setEnabled(false)
        currentSession?.close()
        currentSession = null

        _installProgress.value = "Wiping old RootFS..."
        _statusMessage.value = "Wiping old RootFS..."
        rootFs.clearInstalledMarker()
        rootFs.wipeRootFs().getOrElse { e ->
            return Result.failure(e)
        }

        _installProgress.value = "Reinstalling RootFS (symlinks included)..."
        val result = rootFs.install { msg ->
            _installProgress.value = msg
            _statusMessage.value = msg
        }
        _rootFsStatus.value = rootFs.status()
        if (result.isSuccess) {
            _statusMessage.value = "RootFS reinstalled — next: setup-runtime"
        }
        return result
    }

    suspend fun setupRuntime(): Result<Unit> {
        _installProgress.value = "Setting up PRoot..."
        val result = runtime.ensureProot { msg ->
            _installProgress.value = msg
            _statusMessage.value = msg
        }
        if (result.isSuccess && rootFs.isInstalled()) {
            _isReady.value = true
            LinuxConfig.setEnabled(true)
            _statusMessage.value = "Linux ready (RootFS + PRoot)"
        }
        return result
    }

    suspend fun repairProot(): Result<Unit> {
        _installProgress.value = "Repairing proot..."
        val result = runtime.repairProot { msg ->
            _installProgress.value = msg
            _statusMessage.value = msg
        }
        if (result.isSuccess && rootFs.isInstalled()) {
            _isReady.value = true
            LinuxConfig.setEnabled(true)
        }
        return result
    }

    suspend fun setupStorage(): Result<Unit> {
        _installProgress.value = "setup-storage..."
        val result = runtime.setupStorage { msg ->
            _installProgress.value = msg
            _statusMessage.value = msg
        }
        return result
    }

    suspend fun refreshRootFsStatus() {
        _rootFsStatus.value = rootFs.status()
        _isReady.value = runtime.isFullyReady()
        LinuxConfig.setEnabled(_isReady.value)
    }

    fun getRootFs(): LinuxRootFs = rootFs
    fun getRuntime(): LinuxRuntime = runtime

    fun startSession(): LinuxSession {
        val session = LinuxSession(runtime)
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
