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

/**
 * Low-level representation of a process running inside the Linux RootFS.
 *
 * Future responsibilities:
 * - Launch via native bridge (PRoot / bionic / custom)
 * - Capture stdout / stderr / exit code
 * - Signal handling (SIGINT, SIGTERM, etc.)
 *
 * Current state: pure data class + stubs.
 */
data class LinuxProcess(
    val pid: Int = -1,
    val command: String,
    val args: List<String> = emptyList(),
    val workingDirectory: String = "/",
    val environment: Map<String, String> = emptyMap(),
) {

    var exitCode: Int? = null
        private set

    var isRunning: Boolean = false
        private set

    fun start() {
        // TODO: call native layer to spawn the process inside RootFS
        isRunning = false
    }

    fun kill(signal: Int = 9) {
        // TODO: send signal via native bridge
        isRunning = false
        exitCode = -1
    }

    fun waitFor(): Int {
        // TODO: block / suspend until process finishes
        return exitCode ?: -1
    }
}
