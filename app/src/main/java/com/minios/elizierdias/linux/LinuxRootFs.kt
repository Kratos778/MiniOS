/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.linux

import android.content.Context
import android.system.Os
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
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Persistent Debian ARM64 RootFS.
 *
 * Important fixes:
 * - preserves symbolic links instead of silently deleting them;
 * - preserves hard links when possible;
 * - preserves Unix executable/file modes;
 * - extracts into a staging directory;
 * - validates /root, /bin, /usr, /etc and the shell before marking installed;
 * - an old broken installation is no longer accepted only because its marker exists.
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
            listOf(
                LinuxConfig.runtimeDir(context),
                LinuxConfig.rootfsDir(context),
                LinuxConfig.downloadDir(context),
                LinuxConfig.binDir(context),
            ).forEach { dir ->
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
     * Marker + actual RootFS validation.
     * This prevents a broken RootFS from being reported as installed.
     */
    fun isInstalled(): Boolean {
        val marker = LinuxConfig.installedMarker(context)
        if (!marker.isFile) return false

        val root = LinuxConfig.rootfsDir(context)
        val valid = validateRootFs(root)

        if (!valid) {
            try { marker.delete() } catch (_: Exception) {}
        }
        return valid
    }

    fun installedDistro(): String? {
        if (!LinuxConfig.installedMarker(context).isFile) return null
        return try {
            LinuxConfig.installedMarker(context).readText().trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun validateRootFs(root: File): Boolean {
        if (!root.isDirectory) return false

        val requiredDirs = listOf("root", "etc", "usr", "bin")
        if (requiredDirs.any { !File(root, it).exists() }) return false

        val bash = File(root, "bin/bash")
        val sh = File(root, "bin/sh")
        if (!bash.exists() && !sh.exists()) return false

        return File(root, "usr").isDirectory || File(root, "usr").isFile
    }

    suspend fun markInstalled(
        distro: String = LinuxConfig.DEFAULT_DISTRO,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureDirectories().getOrThrow()
            val root = LinuxConfig.rootfsDir(context)
            if (!validateRootFs(root)) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "RootFS validation failed: /root, /bin, /etc, /usr and /bin/bash or /bin/sh are required."
                    )
                )
            }
            LinuxConfig.installedMarker(context).writeText(distro)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearInstalledMarker(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            LinuxConfig.installedMarker(context).delete()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun wipeRootFs(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            LinuxConfig.rootfsDir(context).deleteRecursively()
            LinuxConfig.rootfsDir(context).mkdirs()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun status(): Status = withContext(Dispatchers.IO) {
        val rootfs = LinuxConfig.rootfsDir(context)
        val size = if (rootfs.exists()) {
            rootfs.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L

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
        ensureDirectories().getOrThrow()

        if (isInstalled()) {
            return@withContext Result.success(
                "RootFS already installed (${installedDistro() ?: "unknown"})"
            )
        }

        Result.success("Directories ready. Run 'install' in Linux shell to download Debian ARM64.")
    }

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
                downloadFile(
                    LinuxConfig.ROOTFS_URL,
                    tarball,
                    onProgress,
                ).getOrThrow()
            } else {
                onProgress?.onProgress(
                    "Tarball already present (${formatSize(tarball.length())})."
                )
            }

            onProgress?.onProgress("Verifying SHA-256...")
            val actualSha = sha256(tarball)
            if (!actualSha.equals(LinuxConfig.ROOTFS_SHA256, ignoreCase = true)) {
                tarball.delete()
                return@withContext Result.failure(
                    IllegalStateException(
                        "SHA-256 mismatch.\nExpected: ${LinuxConfig.ROOTFS_SHA256}\nActual:   $actualSha"
                    )
                )
            }
            onProgress?.onProgress("SHA-256 OK.")

            val rootfs = LinuxConfig.rootfsDir(context)
            val staging = File(
                LinuxConfig.runtimeDir(context),
                "${LinuxConfig.ROOTFS_DIR_NAME}.staging"
            )

            if (staging.exists()) {
                onProgress?.onProgress("Removing previous incomplete extraction...")
                staging.deleteRecursively()
            }
            if (!staging.mkdirs()) {
                throw IllegalStateException("Cannot create staging RootFS: ${staging.absolutePath}")
            }

            onProgress?.onProgress(
                "Extracting rootfs (tar.xz) with symlink support..."
            )
            extractTarXz(tarball, staging, onProgress).getOrThrow()

            // A Debian rootfs must have a real /root directory.
            val stagedRoot = File(staging, "root")
            if (!stagedRoot.exists()) {
                onProgress?.onProgress("Archive has no /root; creating /root...")
                if (!stagedRoot.mkdirs()) {
                    throw IllegalStateException("Cannot create ${stagedRoot.absolutePath}")
                }
            }

            if (!validateRootFs(staging)) {
                staging.deleteRecursively()
                throw IllegalStateException(
                    "Extracted RootFS is invalid. Required: /root, /bin, /etc, /usr and /bin/bash or /bin/sh."
                )
            }

            // Only replace the live RootFS after validation succeeds.
            onProgress?.onProgress("Installing validated RootFS...")
            LinuxConfig.installedMarker(context).delete()
            if (rootfs.exists()) rootfs.deleteRecursively()

            if (!staging.renameTo(rootfs)) {
                // Cross-filesystem/rename fallback.
                copyDirectory(staging, rootfs)
                staging.deleteRecursively()
            }

            // Final validation after the move.
            if (!validateRootFs(rootfs)) {
                rootfs.deleteRecursively()
                throw IllegalStateException("Final RootFS validation failed.")
            }

            LinuxConfig.installedMarker(context).writeText(
                LinuxConfig.DEFAULT_DISTRO
            )

            tarball.delete()

            val finalStatus = status()
            onProgress?.onProgress(
                "RootFS installed successfully · ${formatSize(finalStatus.estimatedSizeBytes)}"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            onProgress?.onProgress("ERROR: ${e.message ?: e.javaClass.simpleName}")
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
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }

            try {
                conn.connect()
                if (conn.responseCode !in 200..299) {
                    return Result.failure(
                        IllegalStateException("HTTP ${conn.responseCode}: ${conn.responseMessage}")
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
                                        "Download: $pct% (${formatSize(downloaded)} / ${formatSize(total)})"
                                    )
                                }
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

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

    /**
     * Extracts tar.xz while preserving the filesystem structure.
     * Symlinks are essential for Debian (/bin, /lib, etc.).
     */
    private fun extractTarXz(
        tarball: File,
        destDir: File,
        onProgress: ProgressListener?,
    ): Result<Unit> {
        return try {
            destDir.mkdirs()
            val pendingHardLinks = mutableListOf<Pair<File, String>>()
            var count = 0

            FileInputStream(tarball).use { fis ->
                XZCompressorInputStream(fis).use { xz ->
                    TarArchiveInputStream(xz).use { tar ->
                        var entry: TarArchiveEntry? = tar.nextEntry

                        while (entry != null) {
                            val current = entry!!
                            val name = safeTarPath(current.name)

                            if (name.isNotEmpty()) {
                                val outFile = File(destDir, name)

                                when {
                                    current.isDirectory -> {
                                        outFile.mkdirs()
                                        applyMode(outFile, current.mode)
                                    }

                                    current.isSymbolicLink -> {
                                        outFile.parentFile?.mkdirs()
                                        deleteIfExists(outFile)

                                        val target = current.linkName
                                        try {
                                            Files.createSymbolicLink(
                                                outFile.toPath(),
                                                Path.of(target)
                                            )
                                        } catch (e: Exception) {
                                            throw IllegalStateException(
                                                "Failed to create symlink $name -> $target: ${e.message}",
                                                e
                                            )
                                        }
                                    }

                                    current.isLink -> {
                                        outFile.parentFile?.mkdirs()
                                        pendingHardLinks += outFile to safeTarPath(current.linkName)
                                    }

                                    current.isFile -> {
                                        outFile.parentFile?.mkdirs()
                                        FileOutputStream(outFile).use { output ->
                                            tar.copyTo(output)
                                        }
                                        applyMode(outFile, current.mode)
                                    }

                                    else -> {
                                        // Device nodes/FIFOs are not required here because
                                        // /dev is bound by PRoot at runtime.
                                    }
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

            // Resolve hard links after all normal files exist.
            for ((outFile, targetName) in pendingHardLinks) {
                val target = File(destDir, targetName)
                if (!target.exists()) {
                    throw IllegalStateException(
                        "Hard-link target missing: $targetName"
                    )
                }
                deleteIfExists(outFile)
                Files.createLink(outFile.toPath(), target.toPath())
            }

            onProgress?.onProgress("Extraction finished ($count entries).")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun safeTarPath(raw: String): String {
        val normalized = raw
            .replace('\\', '/')
            .removePrefix("./")
            .removePrefix("/")

        val parts = normalized.split('/').filter { it.isNotEmpty() }
        val stack = ArrayDeque<String>()

        for (part in parts) {
            when (part) {
                "." -> Unit
                ".." -> {
                    if (stack.isNotEmpty()) stack.removeLast()
                    else throw SecurityException("Unsafe tar path: $raw")
                }
                else -> stack.addLast(part)
            }
        }

        return stack.joinToString("/")
    }

    private fun deleteIfExists(file: File) {
        if (file.exists() || Files.isSymbolicLink(file.toPath())) {
            file.deleteRecursively()
        }
    }

    private fun applyMode(file: File, mode: Int) {
        try {
            val unixMode = mode and 0x1FF
            Os.chmod(file.absolutePath, unixMode)
        } catch (_: Exception) {
            // Best effort; Android filesystem permissions may restrict chmod.
            if ((mode and 0b001_001_001) != 0) {
                file.setExecutable(true, false)
            }
        }
    }

    private fun copyDirectory(source: File, destination: File) {
        destination.mkdirs()
        source.listFiles()?.forEach { child ->
            val target = File(destination, child.name)

            if (Files.isSymbolicLink(child.toPath())) {
                val linkTarget = Files.readSymbolicLink(child.toPath())
                Files.createSymbolicLink(target.toPath(), linkTarget)
            } else if (child.isDirectory) {
                copyDirectory(child, target)
            } else {
                child.copyTo(target, overwrite = true)
            }
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
