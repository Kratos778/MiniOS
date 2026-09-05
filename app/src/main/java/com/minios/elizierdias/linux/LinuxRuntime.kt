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
import android.os.Environment
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Runs commands inside the Debian RootFS via PRoot (no root required).
 */
class LinuxRuntime(
    private val context: Context,
) {

    fun interface ProgressListener {
        fun onProgress(message: String)
    }

    data class ExecResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    /** Preferred locations for the proot binary (Android often blocks exec in files/). */
    private fun prootCandidates(): List<File> = listOf(
        File(context.codeCacheDir, "proot"),
        File(context.noBackupFilesDir, "proot"),
        LinuxConfig.prootFile(context),
        File(context.applicationInfo.nativeLibraryDir, "libproot.so"),
    )

    fun resolveProot(): File? =
        prootCandidates().firstOrNull { it.isFile && it.length() > 10_000 }

    fun isProotInstalled(): Boolean =
        resolveProot() != null

    fun isRootFsReady(): Boolean =
        LinuxConfig.installedMarker(context).isFile

    fun isStorageReady(): Boolean =
        LinuxConfig.storageMarker(context).isFile

    fun isFullyReady(): Boolean =
        isRootFsReady() && isProotInstalled()

    /**
     * Make file executable using Os.chmod (setExecutable alone is often not enough).
     */
    private fun forceExecutable(file: File): Boolean {
        return try {
            file.setReadable(true, false)
            file.setWritable(true, true)
            file.setExecutable(true, false)
            try {
                // 0755
                Os.chmod(file.absolutePath, 493)
            } catch (_: Exception) {
                // some devices throw; setExecutable may still work
            }
            file.canExecute() || file.length() > 10_000
        } catch (_: Exception) {
            false
        }
    }

    private fun copyFile(from: File, to: File) {
        to.parentFile?.mkdirs()
        FileInputStream(from).use { input ->
            FileOutputStream(to).use { output ->
                input.copyTo(output)
            }
        }
    }

    suspend fun ensureProot(onProgress: ProgressListener? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                LinuxConfig.binDir(context).mkdirs()
                LinuxConfig.tmpDir(context).mkdirs()
                context.codeCacheDir.mkdirs()
                context.noBackupFilesDir.mkdirs()

                val existing = resolveProot()
                if (existing != null) {
                    forceExecutable(existing)
                    // Ensure codeCache copy exists (best chance to exec on Android 10+)
                    val cacheCopy = File(context.codeCacheDir, "proot")
                    if (existing.absolutePath != cacheCopy.absolutePath) {
                        copyFile(existing, cacheCopy)
                        forceExecutable(cacheCopy)
                    }
                    onProgress?.onProgress("PRoot already present (${existing.length()} bytes).")
                    return@withContext Result.success(Unit)
                }

                onProgress?.onProgress("Downloading PRoot (aarch64)...")
                val primary = LinuxConfig.prootFile(context)
                downloadBinary(LinuxConfig.PROOT_URL, primary, onProgress).getOrThrow()
                forceExecutable(primary)

                // Install into codeCacheDir + noBackup (exec often allowed there)
                val cacheProot = File(context.codeCacheDir, "proot")
                copyFile(primary, cacheProot)
                forceExecutable(cacheProot)

                val noBackupProot = File(context.noBackupFilesDir, "proot")
                copyFile(primary, noBackupProot)
                forceExecutable(noBackupProot)

                val resolved = resolveProot()
                    ?: return@withContext Result.failure(
                        IllegalStateException("proot downloaded but not found on disk"),
                    )

                onProgress?.onProgress("PRoot ready (${resolved.length()} bytes) @ ${resolved.parent}")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Re-apply chmod / re-copy proot (use if you still get Permission denied).
     */
    suspend fun repairProot(onProgress: ProgressListener? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val src = resolveProot()
                    ?: return@withContext Result.failure(
                        IllegalStateException("PRoot missing. Run: setup-runtime"),
                    )
                onProgress?.onProgress("Repairing proot permissions...")
                val targets = listOf(
                    File(context.codeCacheDir, "proot"),
                    File(context.noBackupFilesDir, "proot"),
                    LinuxConfig.prootFile(context),
                )
                for (t in targets) {
                    if (src.absolutePath != t.absolutePath) {
                        copyFile(src, t)
                    }
                    forceExecutable(t)
                    onProgress?.onProgress("chmod 0755 → ${t.absolutePath}")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun setupStorage(onProgress: ProgressListener? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val rootfs = LinuxConfig.rootfsDir(context)
                if (!rootfs.exists()) {
                    return@withContext Result.failure(
                        IllegalStateException("RootFS not found. Run 'install' first."),
                    )
                }

                val external = Environment.getExternalStorageDirectory()
                val downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS,
                )
                val dcim = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM,
                )
                val pictures = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES,
                )

                val mounts = mutableListOf<Pair<String, File>>()
                if (external != null) mounts += "/sdcard" to external
                if (downloads != null) mounts += "/sdcard/Download" to downloads
                if (dcim != null) mounts += "/sdcard/DCIM" to dcim
                if (pictures != null) mounts += "/sdcard/Pictures" to pictures

                context.getExternalFilesDir(null)?.let { appExt ->
                    mounts += "/sdcard/MiniOS" to appExt
                }

                for ((linuxPath, androidPath) in mounts) {
                    File(rootfs, linuxPath.removePrefix("/")).parentFile?.mkdirs()
                    onProgress?.onProgress(
                        "storage: $linuxPath → ${androidPath.absolutePath}",
                    )
                }

                val profileDir = File(rootfs, "root")
                profileDir.mkdirs()
                File(profileDir, ".minios_storage").writeText(
                    buildString {
                        appendLine("# MiniOS storage binds (applied by proot -b)")
                        for ((linuxPath, androidPath) in mounts) {
                            appendLine("$linuxPath=${androidPath.absolutePath}")
                        }
                    },
                )

                LinuxConfig.storageMarker(context).writeText("ok")
                onProgress?.onProgress("setup-storage: OK")
                onProgress?.onProgress("Inside Linux use: ls /sdcard  or  ls /sdcard/Download")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun buildProotCommand(command: String): List<String> {
        val prootFile = resolveProot()
            ?: error("PRoot binary not found")
        forceExecutable(prootFile)

        val proot = prootFile.absolutePath
        val rootfs = LinuxConfig.rootfsDir(context).absolutePath
        val tmp = LinuxConfig.tmpDir(context).absolutePath
        LinuxConfig.tmpDir(context).mkdirs()

        val args = mutableListOf(
            proot,
            "--link2symlink",
            "-0",
            "-r", rootfs,
            "-w", "/root",
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "$tmp:/tmp",
        )

        val external = Environment.getExternalStorageDirectory()
        if (external != null && external.exists()) {
            args += listOf("-b", "${external.absolutePath}:/sdcard")
        }
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        )
        if (downloads != null && downloads.exists()) {
            args += listOf("-b", "${downloads.absolutePath}:/sdcard/Download")
        }
        context.getExternalFilesDir(null)?.let { appExt ->
            args += listOf("-b", "${appExt.absolutePath}:/sdcard/MiniOS")
        }

        val shell = File(LinuxConfig.rootfsDir(context), "bin/bash")
        val shellPath = if (shell.exists()) "/bin/bash" else "/bin/sh"

        args += listOf(shellPath, "-c", command)
        return args
    }

    suspend fun exec(
        command: String,
        timeoutSec: Long = 120,
    ): Result<ExecResult> = withContext(Dispatchers.IO) {
        try {
            if (!isRootFsReady()) {
                return@withContext Result.failure(
                    IllegalStateException("RootFS not installed. Run: install"),
                )
            }
            if (!isProotInstalled()) {
                return@withContext Result.failure(
                    IllegalStateException("PRoot missing. Run: setup-runtime"),
                )
            }

            // Repair perms every run (cheap) — fixes many Permission denied cases
            resolveProot()?.let { forceExecutable(it) }

            val cmd = buildProotCommand(command)
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(false)
            pb.environment()["PROOT_TMP_DIR"] = LinuxConfig.tmpDir(context).absolutePath
            pb.environment()["HOME"] = "/root"
            pb.environment()["TERM"] = "xterm-256color"
            pb.environment()["PATH"] =
                "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            pb.environment()["LANG"] = "C.UTF-8"
            pb.environment().remove("LD_LIBRARY_PATH")

            val process = try {
                pb.start()
            } catch (e: Exception) {
                // Auto-repair once and retry
                repairProot(null)
                val retryCmd = buildProotCommand(command)
                try {
                    ProcessBuilder(retryCmd).also { p ->
                        p.redirectErrorStream(false)
                        p.environment()["PROOT_TMP_DIR"] =
                            LinuxConfig.tmpDir(context).absolutePath
                        p.environment()["HOME"] = "/root"
                        p.environment()["TERM"] = "xterm-256color"
                        p.environment()["PATH"] =
                            "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                        p.environment().remove("LD_LIBRARY_PATH")
                    }.start()
                } catch (e2: Exception) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Cannot execute proot (Permission denied). " +
                                "Run: repair-proot\n" +
                                "Detail: ${e2.message}",
                            e2,
                        ),
                    )
                }
            }

            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val outThread = Thread {
                BufferedReader(InputStreamReader(process.inputStream)).use { r ->
                    var line = r.readLine()
                    while (line != null) {
                        stdout.appendLine(line)
                        line = r.readLine()
                    }
                }
            }
            val errThread = Thread {
                BufferedReader(InputStreamReader(process.errorStream)).use { r ->
                    var line = r.readLine()
                    while (line != null) {
                        stderr.appendLine(line)
                        line = r.readLine()
                    }
                }
            }
            outThread.start()
            errThread.start()

            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext Result.failure(
                    IllegalStateException("Command timed out after ${timeoutSec}s"),
                )
            }
            outThread.join(5_000)
            errThread.join(5_000)

            Result.success(
                ExecResult(
                    exitCode = process.exitValue(),
                    stdout = stdout.toString().trimEnd(),
                    stderr = stderr.toString().trimEnd(),
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downloadBinary(
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
                    IllegalStateException("HTTP ${conn.responseCode} ${conn.responseMessage}"),
                )
            }
            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()
            onProgress?.onProgress("Downloaded ${dest.name} (${dest.length()} bytes)")
            Result.success(Unit)
        } catch (e: Exception) {
            dest.delete()
            Result.failure(e)
        }
    }
}
