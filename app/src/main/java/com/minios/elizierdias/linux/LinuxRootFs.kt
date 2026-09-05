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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages the persistent Linux RootFS on app private storage.
 *
 * Design decisions:
 * - RootFS lives under Context.filesDir (no storage permission required)
 * - APK never contains the full distro (keeps size small)
 * - Future: download + extract a Debian/Ubuntu/Alpine ARM64 tarball
 * - Marker file records successful installation + distro name
 *
 * Current stage (Etapa 2): directory structure + detection + marker.
 * Real tarball extraction will be added in a later step.
 */
class LinuxRootFs(
    private val context: Context,
) {

    data class Status(
        val runtimeDirExists: Boolean,
        val rootfsDirExists: Boolean,
        val isInstalled: Boolean,
        val distro: String?,
        val rootfsPath: String,
        val estimatedSizeBytes: Long,
    )

    /**
     * Create the base directory tree if it does not exist.
     * Safe to call multiple times.
     */
    suspend fun ensureDirectories(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dirs = listOf(
                LinuxConfig.runtimeDir(context),
                LinuxConfig.rootfsDir(context),
                LinuxConfig.downloadDir(context),
                LinuxConfig.binDir(context),
            )
            for (dir in dirs) {
                if (!dir.exists() && !dir.mkdirs()) {
                    return@withContext Result.failure(
                        IllegalStateException("Failed to create directory: ${dir.absolutePath}")
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Returns true when the marker file is present (RootFS considered installed).
     */
    fun isInstalled(): Boolean =
        LinuxConfig.installedMarker(context).isFile

    /**
     * Read the distro name stored inside the marker file (if any).
     */
    fun installedDistro(): String? {
        val marker = LinuxConfig.installedMarker(context)
        if (!marker.isFile) return null
        return try {
            marker.readText().trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Mark the RootFS as installed (writes the marker file).
     * Called after a successful real extraction in the future.
     */
    suspend fun markInstalled(distro: String = LinuxConfig.DEFAULT_DISTRO): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                ensureDirectories()
                val marker = LinuxConfig.installedMarker(context)
                marker.writeText(distro)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Remove the installed marker (does not delete the whole RootFS).
     */
    suspend fun clearInstalledMarker(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val marker = LinuxConfig.installedMarker(context)
            if (marker.exists()) marker.delete()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete the entire RootFS tree (irreversible).
     * Use with care — future UI will ask for confirmation.
     */
    suspend fun wipeRootFs(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val root = LinuxConfig.rootfsDir(context)
            if (root.exists()) {
                root.deleteRecursively()
            }
            // Recreate empty rootfs dir so structure stays valid
            LinuxConfig.rootfsDir(context).mkdirs()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Collect current status for UI / debugging.
     */
    suspend fun status(): Status = withContext(Dispatchers.IO) {
        val rootfs = LinuxConfig.rootfsDir(context)
        val size = if (rootfs.exists()) rootfs.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        else 0L

        Status(
            runtimeDirExists = LinuxConfig.runtimeDir(context).exists(),
            rootfsDirExists = rootfs.exists(),
            isInstalled = isInstalled(),
            distro = installedDistro(),
            rootfsPath = rootfs.absolutePath,
            estimatedSizeBytes = size,
        )
    }

    /**
     * Placeholder for future real installation.
     *
     * Later this will:
     * 1. Download a minimal Debian ARM64 rootfs tarball into downloadDir
     * 2. Extract it into rootfsDir
     * 3. Call markInstalled()
     *
     * For now it only ensures directories and reports that extraction is not yet implemented.
     */
    suspend fun prepareForInstallation(): Result<String> = withContext(Dispatchers.IO) {
        val dirsResult = ensureDirectories()
        if (dirsResult.isFailure) {
            return@withContext Result.failure(dirsResult.exceptionOrNull()!!)
        }

        if (isInstalled()) {
            return@withContext Result.success(
                "RootFS already marked as installed (${installedDistro() ?: "unknown"})"
            )
        }

        Result.success(
            "Directories ready. Real RootFS extraction (Debian ARM64) not implemented yet."
        )
    }

    companion object {
        /** Human-readable size helper */
        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }
}
