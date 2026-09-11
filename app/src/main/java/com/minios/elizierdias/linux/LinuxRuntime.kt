/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 * PROPRIETARY SOFTWARE — All Rights Reserved.
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
import java.nio.file.Files
import java.util.concurrent.TimeUnit

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

    private fun linkLibDir(): File =
        File(context.codeCacheDir, "minios_libs").also { it.mkdirs() }

    fun prootFile(): File? =
        libDir()?.let { File(it, "libproot.so") }
            ?.takeIf { it.isFile && it.length() > 20_000 }

    fun loaderFile(): File? =
        libDir()?.let { File(it, "libproot_loader.so") }
            ?.takeIf { it.isFile && it.length() > 1_000 }

    fun tallocFile(): File? =
        libDir()?.let { File(it, "libtalloc.so") }
            ?.takeIf { it.isFile && it.length() > 1_000 }

    fun resolveProot(): File? = prootFile()
    fun isProotInstalled(): Boolean = prootFile() != null

    fun isRootFsReady(): Boolean {
        val root = LinuxConfig.rootfsDir(context)
        val marker = LinuxConfig.installedMarker(context)
        if (!marker.isFile || !root.isDirectory) return false
        val rootHome = File(root, "root")
        return rootHome.isDirectory &&
            File(root, "etc").exists() &&
            File(root, "usr").exists() &&
            File(root, "bin").exists() &&
            resolveShellPath(root) != null
    }

    fun resolveShellPath(root: File = LinuxConfig.rootfsDir(context)): String? {
        for (rel in listOf("bin/bash", "usr/bin/bash", "bin/sh", "usr/bin/sh")) {
            if (File(root, rel).exists()) return "/$rel"
        }
        return null
    }

    fun isStorageReady(): Boolean = LinuxConfig.storageMarker(context).isFile
    fun isFullyReady(): Boolean = isRootFsReady() && isProotInstalled()

    fun diagnostic(): String = buildString {
        appendLine("nativeLibraryDir: ${libDir()?.absolutePath ?: \"null\"}")
        appendLine("libproot.so: ${prootFile()?.absolutePath ?: \"MISSING\"}")
        appendLine("rootfs: ${LinuxConfig.rootfsDir(context).absolutePath}")
        appendLine("rootfsReady: ${isRootFsReady()}")
    }

    private fun forceExecutable(file: File) {
        try {
            file.setReadable(true, false)
            file.setExecutable(true, false)
            try { Os.chmod(file.absolutePath, 493) } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    private fun copyFile(from: File, to: File) {
        to.parentFile?.mkdirs()
        FileInputStream(from).use { input ->
            FileOutputStream(to).use { output -> input.copyTo(output) }
        }
    }

    fun prepareLinkLibs(): File {
        val dir = linkLibDir()
        val src = tallocFile()
        if (src != null) {
            val soname = File(dir, "libtalloc.so.2")
            if (!soname.exists() || soname.length() != src.length()) copyFile(src, soname)
            val plain = File(dir, "libtalloc.so")
            if (!plain.exists() || plain.length() != src.length()) copyFile(src, plain)
        }
        loaderFile()?.let { srcLoader ->
            val dest = File(dir, "libproot_loader.so")
            if (!dest.exists() || dest.length() != srcLoader.length()) copyFile(srcLoader, dest)
            forceExecutable(dest)
        }
        return dir
    }

    private fun deletePath(file: File) {
        try {
            if (Files.isSymbolicLink(file.toPath()) || file.exists()) file.delete()
        } catch (_: Exception) {
            try { Files.deleteIfExists(file.toPath()) } catch (_: Exception) {}
        }
    }

    fun ensureDns(): String {
        return try {
            val root = LinuxConfig.rootfsDir(context)
            val etc = File(root, "etc")
            if (!etc.isDirectory) return "DNS: /etc em falta"
            val resolv = File(etc, "resolv.conf")
            deletePath(resolv)
            resolv.writeText(
                "# NoskOS PRoot DNS\nnameserver 8.8.8.8\nnameserver 1.1.1.1\nnameserver 8.8.4.4\noptions timeout:2 attempts:3\n",
            )
            "DNS OK"
        } catch (e: Exception) {
            "DNS ERROR: ${e.message}"
        }
    }

    suspend fun setupDns(onProgress: ProgressListener? = null): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (!isRootFsReady()) return@withContext Result.failure(IllegalStateException("RootFS not ready"))
                val msg = ensureDns()
                onProgress?.onProgress(msg)
                Result.success(msg)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun ensureProot(onProgress: ProgressListener? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                LinuxConfig.tmpDir(context).mkdirs()
                val proot = prootFile()
                    ?: return@withContext Result.failure(IllegalStateException("libproot.so not in APK.\n${diagnostic()}"))
                if (!isRootFsReady()) {
                    return@withContext Result.failure(IllegalStateException("RootFS incomplete.\n${diagnostic()}"))
                }
                forceExecutable(proot)
                loaderFile()?.let { forceExecutable(it) }
                prepareLinkLibs()
                ensureDns()
                onProgress?.onProgress("PRoot OK")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun repairProot(onProgress: ProgressListener? = null): Result<Unit> = ensureProot(onProgress)

    suspend fun setupStorage(onProgress: ProgressListener? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (!isRootFsReady()) {
                    return@withContext Result.failure(IllegalStateException("RootFS not ready"))
                }
                LinuxConfig.publicMiniOsDir().mkdirs()
                LinuxConfig.importsDir(context).mkdirs()
                LinuxConfig.downloadDir(context).mkdirs()
                LinuxConfig.runtimeDir(context).mkdirs()
                LinuxConfig.storageMarker(context).writeText("ok")
                ensureDns()
                onProgress?.onProgress("setup-storage: OK")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun repairDpkg(onProgress: ProgressListener? = null): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (!isRootFsReady() || !isProotInstalled()) {
                    return@withContext Result.failure(IllegalStateException("[DPKG] RootFS/PRoot not ready"))
                }
                ensureDns()
                prepareLinkLibs()
                onProgress?.onProgress("[DPKG] dpkg --configure -a")
                val r = exec("DEBIAN_FRONTEND=noninteractive dpkg --configure -a", timeoutSec = 600)
                val body = r.getOrNull()?.stdout.orEmpty() + r.getOrNull()?.stderr.orEmpty()
                LinuxConfig.bootstrapMarker(context).writeText("ok")
                Result.success(body.ifBlank { "dpkg OK" })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun ensureDebianBootstrap(
        force: Boolean = false,
        onProgress: ProgressListener? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        val marker = LinuxConfig.bootstrapMarker(context)
        if (!force && marker.isFile) {
            return@withContext Result.success("[Linux] bootstrap já concluído")
        }
        onProgress?.onProgress("[Linux] bootstrap Debian...")
        repairDpkg(onProgress)
    }

    private fun prootArgv(command: String): List<String> {
        val proot = prootFile() ?: error("libproot.so missing")
        if (!isRootFsReady()) error("RootFS invalid")
        forceExecutable(proot)
        prepareLinkLibs()
        val rootfsFile = LinuxConfig.rootfsDir(context)
        val rootfs = rootfsFile.absolutePath
        val tmp = LinuxConfig.tmpDir(context).absolutePath
        LinuxConfig.tmpDir(context).mkdirs()
        val args = mutableListOf(
            proot.absolutePath, "--link2symlink", "-0",
            "-r", rootfs, "-w", "/root",
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "$tmp:/tmp",
        )
        val external = Environment.getExternalStorageDirectory()
        if (external != null && external.exists()) {
            args += listOf("-b", "${external.absolutePath}:/sdcard")
        }
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloads != null && downloads.exists()) {
            args += listOf("-b", "${downloads.absolutePath}:/sdcard/Download")
        }
        val publicMini = LinuxConfig.publicMiniOsDir()
        if (publicMini.exists() || publicMini.mkdirs()) {
            args += listOf("-b", "${publicMini.absolutePath}:/sdcard/MiniOS")
            args += listOf("-b", "${publicMini.absolutePath}:/minios")
        }
        val shellPath = resolveShellPath(rootfsFile) ?: "/bin/sh"
        args += listOf(shellPath, "-c", command)
        return args
    }

    private fun applyProotEnv(env: MutableMap<String, String>) {
        val nativeLib = libDir()?.absolutePath
        val linkLib = prepareLinkLibs().absolutePath
        env["PROOT_TMP_DIR"] = LinuxConfig.tmpDir(context).absolutePath
        env["HOME"] = "/root"
        env["TERM"] = "xterm-256color"
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        env["LANG"] = "C.UTF-8"
        env["USER"] = "root"
        env["LOGNAME"] = "root"
        env["DEBIAN_FRONTEND"] = "noninteractive"
        val loader = loaderFile() ?: File(linkLibDir(), "libproot_loader.so")
        if (loader.isFile) env["PROOT_LOADER"] = loader.absolutePath
        env["LD_LIBRARY_PATH"] = listOfNotNull(linkLib, nativeLib).distinct().joinToString(":")
    }

    private fun startProotProcess(command: String): Process {
        val argv = prootArgv(command)
        try {
            val pb = ProcessBuilder(argv)
            applyProotEnv(pb.environment())
            pb.directory(context.filesDir)
            pb.redirectErrorStream(false)
            return pb.start()
        } catch (e1: Exception) {
            var last: Exception = e1
            for (linker in listOf("/system/bin/linker64", "/system/bin/linker")) {
                if (!File(linker).exists()) continue
                try {
                    val pb = ProcessBuilder(listOf(linker) + argv)
                    applyProotEnv(pb.environment())
                    pb.directory(context.filesDir)
                    return pb.start()
                } catch (e2: Exception) {
                    last = e2
                }
            }
            throw IllegalStateException(
                "Cannot start proot.\n${diagnostic()}\ndirect: ${e1.message}\nlinker: ${last.message}",
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
                return@withContext Result.failure(IllegalStateException("RootFS not ready.\n${diagnostic()}"))
            }
            if (!isProotInstalled()) {
                return@withContext Result.failure(IllegalStateException("libproot.so missing.\n${diagnostic()}"))
            }
            ensureDns()
            prepareLinkLibs()
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
            if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@withContext Result.failure(IllegalStateException("Command timed out after ${timeoutSec}s"))
            }
            outThread.join(5_000)
            errThread.join(5_000)
            val result = ExecResult(
                exitCode = process.exitValue(),
                stdout = stdout.toString().trimEnd(),
                stderr = stderr.toString().trimEnd(),
            )
            if (result.exitCode != 0 && result.stderr.contains("not found", ignoreCase = true)) {
                return@withContext Result.failure(IllegalStateException(result.stderr + "\n" + diagnostic()))
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun execStreaming(
        command: String,
        timeoutSec: Long = 120,
        onLine: ((String) -> Unit)? = null,
    ): Result<ExecResult> = withContext(Dispatchers.IO) {
        try {
            if (!isRootFsReady()) {
                return@withContext Result.failure(IllegalStateException("RootFS not ready.\n${diagnostic()}"))
            }
            if (!isProotInstalled()) {
                return@withContext Result.failure(IllegalStateException("libproot.so missing.\n${diagnostic()}"))
            }
            ensureDns()
            prepareLinkLibs()
            val process = startProotProcess(command)
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val outThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { r ->
                        var line = r.readLine()
                        while (line != null) {
                            stdout.appendLine(line)
                            onLine?.invoke(line)
                            line = r.readLine()
                        }
                    }
                } catch (_: Exception) {}
            }
            val errThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).use { r ->
                        var line = r.readLine()
                        while (line != null) {
                            stderr.appendLine(line)
                            onLine?.invoke(line)
                            line = r.readLine()
                        }
                    }
                } catch (_: Exception) {}
            }
            outThread.start()
            errThread.start()
            if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@withContext Result.failure(IllegalStateException("Command timed out after ${timeoutSec}s"))
            }
            outThread.join(5_000)
            errThread.join(5_000)
            val result = ExecResult(
                exitCode = process.exitValue(),
                stdout = stdout.toString().trimEnd(),
                stderr = stderr.toString().trimEnd(),
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
