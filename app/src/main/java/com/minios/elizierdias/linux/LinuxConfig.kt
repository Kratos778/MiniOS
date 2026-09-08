/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.linux

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Caminhos do runtime Linux.
 *
 * IMPORTANTE (Android):
 * - /sdcard (FAT/FUSE) muitas vezes **não permite symlinks** → o Debian quebra na extração.
 * - Por isso o **RootFS extrai-se sempre** em storage privado (ext4 da app).
 * - Tarball + imports ficam em /sdcard/MiniOS (visíveis e sobrevivem ao uninstall).
 */
object LinuxConfig {

    const val PUBLIC_DIR_NAME = "MiniOS"
    const val RUNTIME_DIR_NAME = "linux_runtime"
    const val ROOTFS_DIR_NAME = "rootfs"
    const val DOWNLOAD_DIR_NAME = "downloads"
    const val IMPORTS_DIR_NAME = "imports"
    const val BIN_DIR_NAME = "bin"
    const val TMP_DIR_NAME = "tmp"
    const val INSTALLED_MARKER = ".minios_rootfs_installed"
    const val STORAGE_MARKER = ".minios_storage_ready"
    const val BOOTSTRAP_MARKER = ".minios_debian_bootstrap_ok"
    const val DNS_MARKER = ".minios_dns_ok"

    const val DEFAULT_DISTRO = "debian-bookworm-arm64"
    const val DEFAULT_SHELL = "/bin/bash"
    const val FALLBACK_SHELL = "/bin/sh"

    const val ROOTFS_URL =
        "https://github.com/termux/proot-distro/releases/download/v4.17.3/debian-bookworm-aarch64-pd-v4.17.3.tar.xz"
    const val ROOTFS_SHA256 =
        "3a841a794ae5999b33e33b329582ed0379d4f54ca62c6ce5a8eb9cff5ef8900b"
    const val ROOTFS_FILENAME = "debian-bookworm-aarch64-pd-v4.17.3.tar.xz"

    const val PROOT_URL =
        "https://skirsten.github.io/proot-portable-android-binaries/aarch64/proot"
    const val PROOT_FILENAME = "proot"

    var enabled: Boolean = false
        private set

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /** /sdcard/MiniOS — pasta pública (visível, persiste) */
    fun publicMiniOsDir(): File =
        File(Environment.getExternalStorageDirectory(), PUBLIC_DIR_NAME)

    /**
     * Runtime do RootFS = SEMPRE privado (precisa de symlinks Unix).
     * /data/data/.../files/linux_runtime
     */
    fun runtimeDir(context: Context): File =
        File(context.filesDir, RUNTIME_DIR_NAME)

    /** RootFS Debian — privado (symlinks OK) */
    fun rootfsDir(context: Context): File =
        File(runtimeDir(context), ROOTFS_DIR_NAME)

    /**
     * Downloads do tarball — preferência pública (não re-baixar após uninstall).
     * Fallback: privado.
     */
    fun downloadDir(context: Context): File {
        val public = File(publicMiniOsDir(), DOWNLOAD_DIR_NAME)
        return try {
            publicMiniOsDir().mkdirs()
            if (!public.exists()) public.mkdirs()
            if (public.exists() && public.canWrite()) public
            else File(runtimeDir(context), DOWNLOAD_DIR_NAME).also { it.mkdirs() }
        } catch (_: Exception) {
            File(runtimeDir(context), DOWNLOAD_DIR_NAME).also { it.mkdirs() }
        }
    }

    /** Ficheiros do utilizador — /sdcard/MiniOS/imports */
    fun importsDir(context: Context): File {
        val external = File(publicMiniOsDir(), IMPORTS_DIR_NAME)
        return try {
            if (!external.exists()) external.mkdirs()
            if (external.exists()) external
            else File(runtimeDir(context), IMPORTS_DIR_NAME).also { it.mkdirs() }
        } catch (_: Exception) {
            File(runtimeDir(context), IMPORTS_DIR_NAME).also { it.mkdirs() }
        }
    }

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

    fun bootstrapMarker(context: Context): File =
        File(runtimeDir(context), BOOTSTRAP_MARKER)

    fun dnsMarker(context: Context): File =
        File(runtimeDir(context), DNS_MARKER)

    fun rootfsTarball(context: Context): File =
        File(downloadDir(context), ROOTFS_FILENAME)

    /** true se o tarball/imports estão no sdcard */
    fun isUsingPublicDownloads(context: Context): Boolean {
        val path = downloadDir(context).absolutePath
        return path.startsWith(publicMiniOsDir().absolutePath)
    }

    /** RootFS é sempre privado — legível para o utilizador no diagnostic */
    fun isUsingPublicStorage(context: Context): Boolean =
        isUsingPublicDownloads(context)

    fun legacyPrivateRootfs(context: Context): File =
        File(context.filesDir, "$RUNTIME_DIR_NAME/$ROOTFS_DIR_NAME")
}
