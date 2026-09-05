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
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Manages the persistent Linux RootFS on app private storage.
 *
 * - Download Debian bookworm ARM64 tarball (proot-distro)
 * - Extract .tar.xz into rootfs/
 * - Marker file records successful installation
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

    fun interface ProgressListener {
        fun onProgress(message: String)
    }

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
                        IllegalStateException("Failed to create directory: ${dir.absolutePath}"),
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isInstalled(): Boolean =
        LinuxConfig.installedMarker(context).isFile

    fun installedDistro(): String? {
        val marker = LinuxConfig.installedMarker(context)
        if (!marker.isFile) return null
        return try {
            marker.readText().trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun markInstalled(distro: String = LinuxConfig.DEFAULT_DISTRO): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                ensureDirectories()
                LinuxConfig.installedMarker(context).writeText(distro)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun clearInstalledMarker(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val marker = LinuxConfig.installedMarker(context)
            if (marker.exists()) marker.delete()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun wipeRootFs(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val root = LinuxConfig.rootfsDir(context)
            if (root.exists()) root.deleteRecursively()
            LinuxConfig.rootfsDir(context).mkdirs()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun status(): Status = withContext(Dispatchers.IO) {
        val rootfs = LinuxConfig.rootfsDir(context)
        val size = if (rootfs.exists()) {
            rootfs.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } else {
            0L
        }
        Status(
            runtimeDirExists = LinuxConfig.runtimeDir(context).exists(),
            rootfsDirExists = rootfs.exists(),
            isInstalled = isInstalled(),
            distro = installedDistro(),
            rootfsPath = rootfs.absolutePath,
            estimatedSizeBytes = size,
        )
    }

    suspend fun prepareForInstallation(): Result<String> = withContext(Dispatchers.IO) {
        val dirsResult = ensureDirectories()
        if (dirsResult.isFailure) {
            return@withContext Result.failure(dirsResult.exceptionOrNull()!!)
        }
        if (isInstalled()) {
            return@withContext Result.success(
                "RootFS already marked as installed (${installedDistro() ?: "unknown"})",
            )
        }
        Result.success("Directories ready. Run 'install' in Linux shell to download Debian ARM64.")
    }

    /**
     * Full install: download tarball → verify → extract → mark installed.
     */
    suspend fun install(
        onProgress: ProgressListener? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInstalled()) {
                onProgress?.onProgress("RootFS already installed (${installedDistro()}).")
                return@withContext Result.success(Unit)
            }

            onProgress?.onProgress("Creating directories...")
            ensureDirectories().getOrThrow()

            val tarball = LinuxConfig.rootfsTarball(context)
            if (!tarball.exists() || tarball.length() < 1_000_000L) {
                onProgress?.onProgress("Downloading Debian bookworm ARM64 rootfs...")
                onProgress?.onProgress("URL: ${LinuxConfig.ROOTFS_URL}")
                downloadFile(LinuxConfig.ROOTFS_URL, tarball, onProgress).getOrThrow()
            } else {
                onProgress?.onProgress("Tarball already present (${formatSize(tarball.length())}).")
            }

            onProgress?.onProgress("Verifying SHA-256...")
            val actualSha = sha256(tarball)
            if (!actualSha.equals(LinuxConfig.ROOTFS_SHA256, ignoreCase = true)) {
                tarball.delete()
                return@withContext Result.failure(
                    IllegalStateException(
                        "SHA-256 mismatch.\nExpected: ${LinuxConfig.ROOTFS_SHA256}\nActual:   $actualSha",
                    ),
                )
            }
            onProgress?.onProgress("SHA-256 OK.")

            onProgress?.onProgress("Extracting rootfs (tar.xz) — this may take several minutes...")
            val rootfs = LinuxConfig.rootfsDir(context)
            // Clean previous partial extract
            if (rootfs.exists()) {
                rootfs.listFiles()?.forEach { child ->
                    if (child.name != LinuxConfig.INSTALLED_MARKER) {
                        child.deleteRecursively()
                    }
                }
            }
            extractTarXz(tarball, rootfs, onProgress).getOrThrow()

            onProgress?.onProgress("Marking RootFS as installed...")
            markInstalled(LinuxConfig.DEFAULT_DISTRO).getOrThrow()

            // Optional: delete tarball to free space
            onProgress?.onProgress("Cleaning download cache...")
            tarball.delete()

            val finalStatus = status()
            onProgress?.onProgress(
                "RootFS installed successfully · ${formatSize(finalStatus.estimatedSizeBytes)}",
            )
            Result.success(Unit)
        } catch (e: Exception) {
            onProgress?.onProgress("ERROR: ${e.message}")
            Result.failure(e)
        }
    }

    private fun downloadFile(
        urlString: String,
        dest: File,
        onProgress: ProgressListener?,
    ): Result<Unit> {
        return try {
            dest.parentFile?.mkdirs()
            val url = URL(urlString)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                return Result.failure(
                    IllegalStateException("HTTP ${conn.responseCode}: ${conn.responseMessage}"),
                )
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastPct = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val pct = ((downloaded * 100) / total).toInt()
                            if (pct != lastPct && pct % 5 == 0) {
                                lastPct = pct
                                onProgress?.onProgress(
                                    "Download: $pct% (${formatSize(downloaded)} / ${formatSize(total)})",
                                )
                            }
                        }
                    }
                }
            }
            conn.disconnect()
            onProgress?.onProgress("Download complete: ${formatSize(dest.length())}")
            Result.success(Unit)
        } catch (e: Exception) {
            dest.delete()
            Result.failure(e)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractTarXz(
        tarball: File,
        destDir: File,
        onProgress: ProgressListener?,
    ): Result<Unit> {
        return try {
            destDir.mkdirs()
            var count = 0
            FileInputStream(tarball).use { fis ->
                XZCompressorInputStream(fis).use { xz ->
                    TarArchiveInputStream(xz).use { tar ->
                        var entry: TarArchiveEntry? = tar.nextEntry
                        while (entry != null) {
                            val name = entry.name
                                .removePrefix("./")
                                .removePrefix("/")
                            if (name.isBlank()) {
                                entry = tar.nextEntry
                                continue
                            }
                            val outFile = File(destDir, name)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else if (entry.isSymbolicLink) {
                                // Skip symlinks for safety on first version; proot handles many later
                                // Write a placeholder note only if needed — skip is safer
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { output ->
                                    tar.copyTo(output)
                                }
                                // Best-effort executable bit
                                if ((entry.mode and 0b001_001_001) != 0) {
                                    outFile.setExecutable(true, false)
                                }
                            }
                            count++
                            if (count % 500 == 0) {
                                onProgress?.onProgress("Extracted $count entries...")
                            }
                            entry = tar.nextEntry
                        }
                    }
                }
            }
            onProgress?.onProgress("Extraction finished ($count entries).")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }
}
