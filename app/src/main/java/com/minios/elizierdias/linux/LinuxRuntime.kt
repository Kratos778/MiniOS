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
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Runs commands inside the Debian RootFS via PRoot (no root required).
 *
 * Also handles setup-storage: bind shared Android storage into the Linux tree
 * (similar idea to Termux's termux-setup-storage).
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

    fun isProotInstalled(): Boolean {
        val f = LinuxConfig.prootFile(context)
        return f.isFile && f.canExecute() && f.length() > 10_000
    }

    fun isRootFsReady(): Boolean =
        LinuxConfig.installedMarker(context).isFile

    fun isStorageReady(): Boolean =
        LinuxConfig.storageMarker(context).isFile

    fun isFullyReady(): Boolean =
        isRootFsReady() && isProotInstalled()

    suspend fun ensureProot(onProgress: ProgressListener? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                LinuxConfig.binDir(context).mkdirs()
                LinuxConfig.tmpDir(context).mkdirs()
                val proot = LinuxConfig.prootFile(context)
                if (isProotInstalled()) {
                    onProgress?.onProgress("PRoot already present.")
                    return@withContext Result.success(Unit)
                }
                onProgress?.onProgress("Downloading PRoot (aarch64)...")
                downloadBinary(LinuxConfig.PROOT_URL, proot, onProgress).getOrThrow()
                proot.setExecutable(true, false)
                if (!proot.canExecute()) {
                    return@withContext Result.failure(
                        IllegalStateException("proot downloaded but not executable"),
                    )
                }
                onProgress?.onProgress("PRoot ready (${proot.length()} bytes).")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Create bind targets and marker so /sdcard and common folders are visible
     * inside the Linux environment (no root — uses Android shared storage paths).
     */
    suspend fun setupStorage(onProgress: ProgressListener? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val rootfs = LinuxConfig.rootfsDir(context)
                if (!rootfs.exists()) {
                    return@withContext Result.failure(
                        IllegalStateException("RootFS not found. Run 'install' first."),
                    )
                }

                // Paths Android exposes without root (when permission granted)
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

                // Also expose app-specific external dir
                context.getExternalFilesDir(null)?.let { appExt ->
                    mounts += "/sdcard/MiniOS" to appExt
                }

                for ((linuxPath, androidPath) in mounts) {
                    val linkDir = File(rootfs, linuxPath.removePrefix("/"))
                    linkDir.parentFile?.mkdirs()
                    onProgress?.onProgress(
                        "storage: $linuxPath → ${androidPath.absolutePath}",
                    )
                }

                // Write a small helper script inside rootfs for the user
                val profileDir = File(rootfs, "root")
                profileDir.mkdirs()
                val note = File(profileDir, ".minios_storage")
                note.writeText(
                    buildString {
                        appendLine("# MiniOS storage binds (applied by proot -b)")
                        for ((linuxPath, androidPath) in mounts) {
                            appendLine("$linuxPath=${androidPath.absolutePath}")
                        }
                    },
                )

                LinuxConfig.storageMarker(context).writeText("ok")
                onProgress?.onProgress("setup-storage: OK")
                onProgress?.onProgress("Inside Linux use: cd /sdcard/Download")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun buildProotCommand(command: String): List<String> {
        val proot = LinuxConfig.prootFile(context).absolutePath
        val rootfs = LinuxConfig.rootfsDir(context).absolutePath
        val tmp = LinuxConfig.tmpDir(context).absolutePath

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

        // Storage binds (if Android paths exist)
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

    /**
     * Execute a command inside the RootFS via PRoot. Blocking IO.
     */
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

            val cmd = buildProotCommand(command)
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(false)
            pb.environment()["PROOT_TMP_DIR"] = LinuxConfig.tmpDir(context).absolutePath
            pb.environment()["HOME"] = "/root"
            pb.environment()["TERM"] = "xterm-256color"
            pb.environment()["PATH"] =
                "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            pb.environment()["LANG"] = "C.UTF-8"

            // On some devices clear LD_LIBRARY_PATH helps proot
            pb.environment().remove("LD_LIBRARY_PATH")

            val process = pb.start()
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
