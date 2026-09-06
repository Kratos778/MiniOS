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
                        IllegalStateException("Failed to create directory: ${dir.absolutePath}"),
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isInstalled(): Boolean {
        val marker = LinuxConfig.installedMarker(context)
        if (!marker.isFile) return false
        val root = LinuxConfig.rootfsDir(context)
        val valid = validateRootFs(root)
        if (!valid) {
            try {
                marker.delete()
            } catch (_: Exception) {
            }
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

    /**
     * Accepts real dirs, files, or symlinks that exist on disk (even if target is relative).
     */
    private fun pathExists(root: File, rel: String): Boolean {
        val f = File(root, rel)
        if (f.exists()) return true
        return try {
            Files.exists(f.toPath()) || Files.isSymbolicLink(f.toPath())
        } catch (_: Exception) {
            false
        }
    }

    private fun validateRootFs(root: File): Boolean {
        if (!root.isDirectory) return false
        val hasEtc = pathExists(root, "etc")
        val hasUsr = pathExists(root, "usr")
        val hasBin = pathExists(root, "bin") || pathExists(root, "usr/bin")
        val hasShell =
            pathExists(root, "bin/bash") ||
                pathExists(root, "bin/sh") ||
                pathExists(root, "usr/bin/bash") ||
                pathExists(root, "usr/bin/sh")
        val hasRootHome = pathExists(root, "root")
        return hasEtc && hasUsr && hasBin && hasShell && hasRootHome
    }

    private fun describeValidation(root: File): String = buildString {
        appendLine("root: ${root.absolutePath}")
        listOf("bin", "etc", "usr", "root", "bin/bash", "bin/sh", "usr/bin/bash", "usr/bin/sh").forEach { rel ->
            val f = File(root, rel)
            val symlink = try {
                Files.isSymbolicLink(f.toPath())
            } catch (_: Exception) {
                false
            }
            appendLine("$rel: exists=${f.exists()} symlink=$symlink")
        }
        val children = root.list()?.take(20)?.joinToString() ?: "(empty)"
        appendLine("children: $children")
    }

    /**
     * proot-distro packs: debian-bookworm-aarch64/{bin,etc,usr,...}
     * Hoist that single top-level directory to the staging root.
     */
    private fun unwrapNestedRootFs(staging: File, onProgress: ProgressListener?) {
        // Already valid at top level
        if (validateRootFs(staging)) return

        val children = staging.listFiles()?.filter {
            it.name != "." && it.name != ".." && !it.name.startsWith(".minios")
        } ?: return

        // Case 1: single directory prefix (proot-distro)
        if (children.size == 1 && children[0].isDirectory) {
            val nested = children[0]
            if (validateRootFs(nested) || pathExists(nested, "etc") || pathExists(nested, "usr")) {
                onProgress?.onProgress("Unpacking nested root: ${nested.name}/ → /")
                hoistDirectory(nested, staging)
                return
            }
        }

        // Case 2: find a child that looks like a rootfs
        for (child in children) {
            if (!child.isDirectory) continue
            if (pathExists(child, "etc") && pathExists(child, "usr")) {
                onProgress?.onProgress("Unpacking nested root: ${child.name}/ → /")
                hoistDirectory(child, staging)
                return
            }
        }
    }

    private fun hoistDirectory(from: File, to: File) {
        from.listFiles()?.forEach { child ->
            val dest = File(to, child.name)
            if (dest.exists()) {
                dest.deleteRecursively()
            }
            val moved = child.renameTo(dest)
            if (!moved) {
                if (Files.isSymbolicLink(child.toPath())) {
                    val target = Files.readSymbolicLink(child.toPath()).toString()
                    createSymlink(dest, target)
                } else if (child.isDirectory) {
                    copyDirectory(child, dest)
                    child.deleteRecursively()
                } else {
                    child.copyTo(dest, overwrite = true)
                    child.delete()
                }
            }
        }
        from.deleteRecursively()
    }

    private fun ensureRootHome(root: File) {
        val home = File(root, "root")
        if (!home.exists()) home.mkdirs()
    }

    suspend fun markInstalled(distro: String = LinuxConfig.DEFAULT_DISTRO): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                ensureDirectories().getOrThrow()
                val root = LinuxConfig.rootfsDir(context)
                if (!validateRootFs(root)) {
                    return@withContext Result.failure(
                        IllegalStateException("RootFS validation failed.\n${describeValidation(root)}"),
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
            // also wipe failed staging
            File(LinuxConfig.runtimeDir(context), "${LinuxConfig.ROOTFS_DIR_NAME}.staging")
                .deleteRecursively()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun status(): Status = withContext(Dispatchers.IO) {
        val rootfs = LinuxConfig.rootfsDir(context)
        val size = if (rootfs.exists()) {
            rootfs.walkTopDown().filter { it.isFile }.sumOf { it.length() }
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
        ensureDirectories().getOrThrow()
        if (isInstalled()) {
            return@withContext Result.success(
                "RootFS already installed (${installedDistro() ?: "unknown"})",
            )
        }
        Result.success("Directories ready. Run install or reinstall-rootfs.")
    }

    suspend fun install(onProgress: ProgressListener? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
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
                    downloadFile(LinuxConfig.ROOTFS_URL, tarball, onProgress).getOrThrow()
                } else {
                    onProgress?.onProgress(
                        "Tarball already present (${formatSize(tarball.length())}).",
                    )
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

                val rootfs = LinuxConfig.rootfsDir(context)
                val staging = File(
                    LinuxConfig.runtimeDir(context),
                    "${LinuxConfig.ROOTFS_DIR_NAME}.staging",
                )
                if (staging.exists()) {
                    onProgress?.onProgress("Removing previous incomplete extraction...")
                    staging.deleteRecursively()
                }
                if (!staging.mkdirs()) {
                    throw IllegalStateException("Cannot create staging: ${staging.absolutePath}")
                }

                onProgress?.onProgress("Extracting rootfs (symlinks + modes)...")
                extractTarXz(tarball, staging, onProgress).getOrThrow()

                // CRITICAL: proot-distro uses debian-bookworm-aarch64/ prefix
                onProgress?.onProgress("Normalizing RootFS layout...")
                unwrapNestedRootFs(staging, onProgress)
                ensureRootHome(staging)

                if (!validateRootFs(staging)) {
                    val detail = describeValidation(staging)
                    staging.deleteRecursively()
                    throw IllegalStateException(
                        "Extracted RootFS invalid.\n$detail",
                    )
                }

                onProgress?.onProgress("Installing validated RootFS...")
                LinuxConfig.installedMarker(context).delete()
                if (rootfs.exists()) rootfs.deleteRecursively()

                if (!staging.renameTo(rootfs)) {
                    copyDirectory(staging, rootfs)
                    staging.deleteRecursively()
                }

                if (!validateRootFs(rootfs)) {
                    val detail = describeValidation(rootfs)
                    rootfs.deleteRecursively()
                    throw IllegalStateException("Final RootFS validation failed.\n$detail")
                }

                LinuxConfig.installedMarker(context).writeText(LinuxConfig.DEFAULT_DISTRO)
                tarball.delete()

                val finalStatus = status()
                onProgress?.onProgress(
                    "RootFS installed · ${formatSize(finalStatus.estimatedSizeBytes)}",
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
                readTimeout = 120_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            try {
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

    private fun extractTarXz(
        tarball: File,
        destDir: File,
        onProgress: ProgressListener?,
    ): Result<Unit> {
        return try {
            destDir.mkdirs()
            val pendingHardLinks = mutableListOf<Pair<File, String>>()
            var count = 0
            var symlinks = 0

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
                                        createSymlink(outFile, current.linkName)
                                        symlinks++
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
                                    else -> Unit
                                }
                            }
                            count++
                            if (count % 500 == 0) {
                                onProgress?.onProgress(
                                    "Extracted $count entries ($symlinks symlinks)...",
                                )
                            }
                            entry = tar.nextEntry
                        }
                    }
                }
            }

            for ((outFile, targetName) in pendingHardLinks) {
                val target = File(destDir, targetName)
                if (!target.exists()) continue
                deleteIfExists(outFile)
                try {
                    Files.createLink(outFile.toPath(), target.toPath())
                } catch (_: Exception) {
                    try {
                        target.copyTo(outFile, overwrite = true)
                    } catch (_: Exception) {
                    }
                }
            }

            onProgress?.onProgress(
                "Extraction finished ($count entries, $symlinks symlinks).",
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createSymlink(linkFile: File, target: String) {
        try {
            Os.symlink(target, linkFile.absolutePath)
            return
        } catch (_: Exception) {
        }
        try {
            Files.createSymbolicLink(linkFile.toPath(), Path.of(target))
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed symlink ${linkFile.name} -> $target: ${e.message}",
                e,
            )
        }
    }

    private fun safeTarPath(raw: String): String {
        val normalized = raw.replace('\\', '/').removePrefix("./").removePrefix("/")
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
        try {
            if (Files.isSymbolicLink(file.toPath()) || file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
            file.deleteRecursively()
        }
    }

    private fun applyMode(file: File, mode: Int) {
        try {
            Os.chmod(file.absolutePath, mode and 0x1FF)
        } catch (_: Exception) {
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
                val linkTarget = Files.readSymbolicLink(child.toPath()).toString()
                createSymlink(target, linkTarget)
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
