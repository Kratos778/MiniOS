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

object LinuxConfig {

    const val RUNTIME_DIR_NAME = "linux_runtime"
    const val ROOTFS_DIR_NAME = "rootfs"
    const val DOWNLOAD_DIR_NAME = "downloads"
    const val BIN_DIR_NAME = "bin"
    const val TMP_DIR_NAME = "tmp"
    const val INSTALLED_MARKER = ".minios_rootfs_installed"
    const val STORAGE_MARKER = ".minios_storage_ready"

    const val DEFAULT_DISTRO = "debian-bookworm-arm64"
    const val DEFAULT_SHELL = "/bin/bash"
    const val FALLBACK_SHELL = "/bin/sh"

    const val ROOTFS_URL =
        "https://github.com/termux/proot-distro/releases/download/v4.17.3/debian-bookworm-aarch64-pd-v4.17.3.tar.xz"
    const val ROOTFS_SHA256 =
        "3a841a794ae5999b33e33b329582ed0379d4f54ca62c6ce5a8eb9cff5ef8900b"
    const val ROOTFS_FILENAME = "debian-bookworm-aarch64-pd-v4.17.3.tar.xz"

    /** Portable PRoot for Android aarch64 (Termux-based build) */
    const val PROOT_URL =
        "https://skirsten.github.io/proot-portable-android-binaries/aarch64/proot"
    const val PROOT_FILENAME = "proot"

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

    fun tmpDir(context: Context): File =
        File(runtimeDir(context), TMP_DIR_NAME)

    fun prootFile(context: Context): File =
        File(binDir(context), PROOT_FILENAME)

    fun installedMarker(context: Context): File =
        File(rootfsDir(context), INSTALLED_MARKER)

    fun storageMarker(context: Context): File =
        File(runtimeDir(context), STORAGE_MARKER)

    fun rootfsTarball(context: Context): File =
        File(downloadDir(context), ROOTFS_FILENAME)
}
