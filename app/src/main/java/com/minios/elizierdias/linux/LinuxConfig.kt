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
