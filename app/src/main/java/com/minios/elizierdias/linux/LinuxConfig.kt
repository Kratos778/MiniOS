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
import java.io.File

/**
 * Configuration for the MiniOS Linux subsystem.
 *
 * The RootFS is stored outside the APK (app private storage) so the APK stays
 * small and the user can later choose Debian / Ubuntu / Alpine ARM64 environments.
 */
object LinuxConfig {

    /** Base directory name inside app private storage for the Linux runtime */
    const val RUNTIME_DIR_NAME = "linux_runtime"

    /** Directory that will hold the extracted RootFS */
    const val ROOTFS_DIR_NAME = "rootfs"

    /** Directory for temporary downloads (rootfs tarballs, etc.) */
    const val DOWNLOAD_DIR_NAME = "downloads"

    /** Directory for runtime binaries (proot, busybox, etc.) when added later */
    const val BIN_DIR_NAME = "bin"

    /** Marker file written when a RootFS has been successfully prepared */
    const val INSTALLED_MARKER = ".minios_rootfs_installed"

    /** Default distribution for the first implementation */
    const val DEFAULT_DISTRO = "debian-arm64"

    /** Path of the default shell inside the RootFS */
    const val DEFAULT_SHELL = "/bin/bash"

    /** Fallback shell if bash is not available */
    const val FALLBACK_SHELL = "/bin/sh"

    /** Supported distros (future multi-distro support) */
    val SUPPORTED_DISTROS = listOf(
        "debian-arm64",
        "ubuntu-arm64",
        "alpine-arm64",
    )

    /** Whether the Linux subsystem is enabled (feature flag) */
    var enabled: Boolean = false
        private set

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    // -------------------------------------------------------------------------
    // Path helpers (all under Context.filesDir — no extra permissions needed)
    // -------------------------------------------------------------------------

    fun runtimeDir(context: Context): File =
        File(context.filesDir, RUNTIME_DIR_NAME)

    fun rootfsDir(context: Context): File =
        File(runtimeDir(context), ROOTFS_DIR_NAME)

    fun downloadDir(context: Context): File =
        File(runtimeDir(context), DOWNLOAD_DIR_NAME)

    fun binDir(context: Context): File =
        File(runtimeDir(context), BIN_DIR_NAME)

    fun installedMarker(context: Context): File =
        File(rootfsDir(context), INSTALLED_MARKER)
}
