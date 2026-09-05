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

    const val RUNTIME_DIR_NAME = "linux_runtime"
    const val ROOTFS_DIR_NAME = "rootfs"
    const val DOWNLOAD_DIR_NAME = "downloads"
    const val BIN_DIR_NAME = "bin"
    const val INSTALLED_MARKER = ".minios_rootfs_installed"

    const val DEFAULT_DISTRO = "debian-bookworm-arm64"

    const val DEFAULT_SHELL = "/bin/bash"
    const val FALLBACK_SHELL = "/bin/sh"

    /**
     * Official proot-distro Debian bookworm aarch64 rootfs.
     * Source: termux/proot-distro releases
     */
    const val ROOTFS_URL =
        "https://github.com/termux/proot-distro/releases/download/v4.17.3/debian-bookworm-aarch64-pd-v4.17.3.tar.xz"

    const val ROOTFS_SHA256 =
        "3a841a794ae5999b33e33b329582ed0379d4f54ca62c6ce5a8eb9cff5ef8900b"

    const val ROOTFS_FILENAME = "debian-bookworm-aarch64-pd-v4.17.3.tar.xz"

    val SUPPORTED_DISTROS = listOf(
        "debian-bookworm-arm64",
        "ubuntu-arm64",
        "alpine-arm64",
    )

    var enabled: Boolean = false
        private set

    fun setEnabled(value: Boolean) {
        enabled = value
    }

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

    fun rootfsTarball(context: Context): File =
        File(downloadDir(context), ROOTFS_FILENAME)
}
