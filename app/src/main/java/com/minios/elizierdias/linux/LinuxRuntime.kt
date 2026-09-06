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
 *
 * CRITICAL: On modern Android, binaries under files/ and code_cache cannot be
 * executed (error=13). PRoot must live in nativeLibraryDir as libproot.so
 * (packaged via jniLibs in the APK).
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

    /** Only nativeLibraryDir is reliably executable on Android 10+. */
    fun bundledProot(): File? {
        val libDir = context.applicationInfo.nativeLibraryDir ?: return null
        val f = File(libDir, "libproot.so")
        return if (f.isFile && f.length() > 10_000) f else null
    }

    fun resolveProot(): File? {
        // 1) Bundled in APK (the real fix for Permission denied)
        bundledProot()?.let { return it }

        // 2) Fallbacks (often blocked by SELinux — kept for older devices)
        val fallbacks = listOf(
            File(context.applicationInfo.nativeLibraryDir, "proot"),
            File(context.codeCacheDir, "proot"),
            File(context.noBackupFilesDir, "proot"),
            LinuxConfig.prootFile(context),
        )
        return fallbacks.firstOrNull { it.isFile && it.length() > 10_000 }
    }

    fun isProotInstalled(): Boolean = resolveProot() != null

    fun isRootFsReady(): Boolean =
        LinuxConfig.installedMarker(context).isFile

    fun isStorageReady(): Boolean =
        LinuxConfig.storageMarker(context).isFile

    fun isFullyReady(): Boolean =
        isRootFsReady() && isProotInstalled()

    private fun forceExecutable(file: File): Boolean {
        return try {
            file.setReadable(true, false)
            file.setExecutable(true, false)
            try {
                Os.chmod(file.absolutePath, 493) // 0755
            } catch (_: Exception) {
            }
            true
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

    fun ensureDns() {
        try {
            val etc = File(LinuxConfig.rootfsDir(context), "etc")
            if (!etc.exists()) return
            val resolv = File(etc, "resolv.conf")
            if (!resolv.exists() || resolv.length() < 8) {
                resolv.writeText(
                    "nameserver 8.8.8.8\nnameserver 1.1.1.1\nnameserver 8.8.4.4\n",
                )
            }
            val hosts = File(etc, "hosts")
            if (!hosts.exists()) {
                hosts.writeText("127.0.0.1\tlocalhost\n::1\tlocalhost\n")
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Prefer bundled libproot.so. Only download to files/ as last resort
     * (will still fail with error=13 on many devices).
     */
    suspend fun ensureProot(onProgress: ProgressListener? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                LinuxConfig.binDir(context).mkdirs()
                LinuxConfig.tmpDir(context).mkdirs()

                val bundled = bundledProot()
                if (bundled != null) {
                    forceExecutable(bundled)
                    ensureDns()
                    onProgress?.onProgress(
                        "PRoot bundled OK (${bundled.length()} bytes)\n" +
                            "path: ${bundled.absolutePath}",
                    )
                    return@withContext Result.success(Unit)
                }

                onProgress?.onProgress(
                    "AVISO: libproot.so não está no APK (jniLibs). " +
                        "A tentar download (pode falhar com Permission denied)…",
                )

                val primary = LinuxConfig.prootFile(context)
                downloadBinary(LinuxConfig.PROOT_URL, primary, onProgress).getOrThrow()
                forceExecutable(primary)

                val cacheProot = File(context.codeCacheDir, "proot")
                copyFile(primary, cacheProot)
                forceExecutable(cacheProot)

                ensureDns()
                onProgress?.onProgress(
                    "PRoot downloaded (${primary.length()} bytes). " +
                        "Se deres error=13, reinstala APK build com jniLibs.",
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun repairProot(onProgress: ProgressListener? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val bundled = bundledProot()
                if (bundled != null) {
                    forceExecutable(bundled)
                    ensureDns()
                    onProgress?.onProgress("Using bundled libproot.so @ ${bundled.absolutePath}")
                    return@withContext Result.success(Unit)
                }
                onProgress?.onProgress(
                    "libproot.so missing from APK. Rebuild with jniLibs or run setup-runtime.",
                )
                ensureProot(onProgress)
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
                context.getExternalFilesDir(null)?.let { mounts += "/sdcard/MiniOS" to it }

                for ((linuxPath, androidPath) in mounts) {
                    File(rootfs, linuxPath.removePrefix("/")).parentFile?.mkdirs()
                    onProgress?.onProgress("storage: $linuxPath → ${androidPath.absolutePath}")
                }

                val profileDir = File(rootfs, "root")
                profileDir.mkdirs()
                File(profileDir, ".minios_storage").writeText(
                    buildString {
                        appendLine("# MiniOS storage binds")
                        for ((linuxPath, androidPath) in mounts) {
                            appendLine("$linuxPath=${androidPath.absolutePath}")
                        }
                    },
                )

                LinuxConfig.storageMarker(context).writeText("ok")
                ensureDns()
                onProgress?.onProgress("setup-storage: OK")
                onProgress?.onProgress("Inside Linux: ls /sdcard")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun buildProotCommand(command: String): List<String> {
        val prootFile = resolveProot() ?: error("PRoot binary not found (libproot.so)")
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
                    IllegalStateException(
                        "PRoot missing. Install APK built with jniLibs, then: setup-runtime",
                    ),
                )
            }

            ensureDns()
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
                return@withContext Result.failure(
                    IllegalStateException(
                        "Cannot execute proot (Permission denied).\n" +
                            "path tried: ${resolveProot()?.absolutePath}\n" +
                            "bundled: ${bundledProot()?.absolutePath ?: "NONE — reinstall APK"}\n" +
                            "Detail: ${e.message}",
                        e,
                    ),
                )
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
