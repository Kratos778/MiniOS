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
 * Configuration for the MiniOS Linux subsystem.
 *
 * The RootFS is stored outside the APK (private storage) so the APK stays small
 * and the user can later choose Debian / Ubuntu / Alpine ARM64 environments.
 */
object LinuxConfig {

    /** Base directory name inside app private storage for the Linux runtime */
    const val RUNTIME_DIR_NAME = "linux_runtime"

    /** Directory that will hold the extracted RootFS */
    const val ROOTFS_DIR_NAME = "rootfs"

    /** Default distribution for the first implementation */
    const val DEFAULT_DISTRO = "debian-arm64"

    /** Path of the default shell inside the RootFS */
    const val DEFAULT_SHELL = "/bin/bash"

    /** Fallback shell if bash is not available */
    const val FALLBACK_SHELL = "/bin/sh"

    /** Whether the Linux subsystem is enabled (feature flag) */
    var enabled: Boolean = false
        private set

    fun setEnabled(value: Boolean) {
        enabled = value
    }
}
