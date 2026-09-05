/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
 *
 * This file is part of MiniOS.
 * Licensed under the MIT License. See LICENSE for details.
 *
 * All rights reserved under applicable law (Republic of Angola & international treaties),
 * subject to the terms of the MIT License.
 */

package com.minios.elizierdias.linux

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central entry point for the MiniOS Linux subsystem.
 *
 * Responsibilities (future):
 * - Bootstrap / stop the Linux runtime (PRoot / native ARM64)
 * - Manage sessions and processes
 * - Coordinate package installation and app registration
 *
 * Current state: skeleton only. No native runtime linked yet.
 */
class LinuxManager(
    private val context: Context,
) {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _statusMessage = MutableStateFlow("Linux subsystem not initialized")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var currentSession: LinuxSession? = null

    /**
     * Initialize the Linux subsystem.
     * In later stages this will extract / verify RootFS and start the runtime.
     */
    suspend fun initialize() {
        _statusMessage.value = "Initializing Linux subsystem (skeleton)..."
        // TODO: check private storage, extract RootFS if needed, start runtime
        _statusMessage.value = "Linux subsystem skeleton ready (no runtime yet)"
        _isReady.value = false // stays false until real runtime is connected
        LinuxConfig.setEnabled(false)
    }

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
