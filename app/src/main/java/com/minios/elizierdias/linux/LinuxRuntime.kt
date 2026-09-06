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
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Debian RootFS via PRoot.
 *
 * Android blocks execve on writable app dirs (files/, code_cache/) → error 13.
 * PRoot stack is shipped in the APK as jniLibs and runs from nativeLibraryDir:
 *   libproot.so + libproot_loader.so + libtalloc.so
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

    private fun libDir(): File? =
        context.applicationInfo.nativeLibraryDir?.let { File(it) }

    fun prootFile(): File? =
        libDir()?.let { File(it, "libproot.so") }?.takeIf { it.isFile && it.length() > 20_000 }

    fun loaderFile(): File? =
        libDir()?.let { File(it, "libproot_loader.so") }?.takeIf { it.isFile && it.length() > 1_000 }

    fun tallocFile(): File? =
        libDir()?.let { File(it, "libtalloc.so") }?.takeIf { it.isFile && it.length() > 1_000 }

    fun resolveProot(): File? = prootFile()

    fun isProotInstalled(): Boolean = prootFile() != null

    fun isRootFsReady(): Boolean =
        LinuxConfig.installedMarker(context).isFile

    fun isStorageReady(): Boolean =
        LinuxConfig.storageMarker(context).isFile

    fun isFullyReady(): Boolean =
        isRootFsReady() && isProotInstalled()

    fun diagnostic(): String = buildString {
        appendLine("nativeLibraryDir: ${libDir()?.absolutePath ?: "null"}")
        appendLine("libproot.so: ${prootFile()?.let { "${it.absolutePath} (${it.length()}) exec=${it.canExecute()}" } ?: "MISSING"}")
        appendLine("libproot_loader.so: ${loaderFile()?.let { "${it.absolutePath} (${it.length()})" } ?: "MISSING"}")
        appendLine("libtalloc.so: ${tallocFile()?.let { "${it.absolutePath} (${it.length()})" } ?: "MISSING"}")
        appendLine("rootfs: ${LinuxConfig.rootfsDir(context).absolutePath} installed=${isRootFsReady()}")
    }

    private fun forceExecutable(file: File) {
        try {
            file.setReadable(true, false)
            file.setExecutable(true, false)
            try {
                Os.chmod(file.absolutePath, 493)
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    fun ensureDns() {
        try {
            val etc = File(LinuxConfig.rootfsDir(context), "etc")
            if (!etc.exists()) return
            val resolv = File(etc, "resolv.conf")
            if (!resolv.exists() || resolv.length() < 8) {
                resolv.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            }
            val hosts = File(etc, "hosts")
            if (!hosts.exists()) {
                hosts.writeText("127.0.0.1\tlocalhost\n::1\tlocalhost\n")
            }
        } catch (_: Exception) {
        }
    }

    suspend fun ensureProot(onProgress: ProgressListener? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                LinuxConfig.tmpDir(context).mkdirs()
                val proot = prootFile()
                if (proot == null) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "libproot.so not in APK.\n" +
                                diagnostic() +
                                "\nReinstall MiniOS 0.3.7+ (uninstall old first).",
                        ),
                    )
                }
                forceExecutable(proot)
                loaderFile()?.let { forceExecutable(it) }
                ensureDns()
                onProgress?.onProgress("PRoot OK (jniLibs)\n${diagnostic()}")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun repairProot(onProgress: ProgressListener? = null): Result<Unit> =
        ensureProot(onProgress)

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

                val mounts = mutableListOf<Pair<String, File>>()
                if (external != null) mounts += "/sdcard" to external
                if (downloads != null) mounts += "/sdcard/Download" to downloads
                context.getExternalFilesDir(null)?.let { mounts += "/sdcard/MiniOS" to it }

                for ((linuxPath, androidPath) in mounts) {
                    File(rootfs, linuxPath.removePrefix("/")).parentFile?.mkdirs()
                    onProgress?.onProgress("storage: $linuxPath → ${androidPath.absolutePath}")
                }

                LinuxConfig.storageMarker(context).writeText("ok")
                ensureDns()
                onProgress?.onProgress("setup-storage: OK")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Build argv for proot. Does not include linker64 wrapper. */
    private fun prootArgv(command: String): List<String> {
        val proot = prootFile() ?: error("libproot.so missing")
        forceExecutable(proot)

        val rootfs = LinuxConfig.rootfsDir(context).absolutePath
        val tmp = LinuxConfig.tmpDir(context).absolutePath
        LinuxConfig.tmpDir(context).mkdirs()

        val args = mutableListOf(
            proot.absolutePath,
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
        context.getExternalFilesDir(null)?.let {
            args += listOf("-b", "${it.absolutePath}:/sdcard/MiniOS")
        }

        val shell = File(LinuxConfig.rootfsDir(context), "bin/bash")
        val shellPath = if (shell.exists()) "/bin/bash" else "/bin/sh"
        args += listOf(shellPath, "-c", command)
        return args
    }

    private fun applyProotEnv(env: MutableMap<String, String>) {
        val lib = libDir()?.absolutePath
        env["PROOT_TMP_DIR"] = LinuxConfig.tmpDir(context).absolutePath
        env["HOME"] = "/root"
        env["TERM"] = "xterm-256color"
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        env["LANG"] = "C.UTF-8"
        env["USER"] = "root"
        // Loader required by Termux/UserLAnd proot builds
        loaderFile()?.let { env["PROOT_LOADER"] = it.absolutePath }
        // talloc + any other jni libs
        if (lib != null) {
            env["LD_LIBRARY_PATH"] = lib
        } else {
            env.remove("LD_LIBRARY_PATH")
        }
    }

    private fun startProotProcess(command: String): Process {
        val argv = prootArgv(command)

        // Strategy 1: direct exec of libproot.so (works when extracted from APK)
        try {
            val pb = ProcessBuilder(argv)
            applyProotEnv(pb.environment())
            return pb.start()
        } catch (e1: Exception) {
            // Strategy 2: system linker loads the ELF (bypass some exec restrictions)
            val linkerCandidates = listOf(
                "/system/bin/linker64",
                "/system/bin/linker",
            )
            var last: Exception = e1
            for (linker in linkerCandidates) {
                if (!File(linker).exists()) continue
                try {
                    val pb = ProcessBuilder(listOf(linker) + argv)
                    applyProotEnv(pb.environment())
                    return pb.start()
                } catch (e2: Exception) {
                    last = e2
                }
            }
            throw IllegalStateException(
                "Cannot start proot.\n${diagnostic()}\n" +
                    "direct: ${e1.message}\nlinker: ${last.message}",
                last,
            )
        }
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
                        "libproot.so missing from APK.\n${diagnostic()}\n" +
                            "Uninstall MiniOS and install build 0.3.7+.",
                    ),
                )
            }

            ensureDns()
            val process = startProotProcess(command)

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
}
