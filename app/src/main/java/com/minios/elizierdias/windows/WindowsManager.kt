/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS / NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.windows

import android.content.Context
import com.minios.elizierdias.core.NoskLog
import com.minios.elizierdias.linux.LinuxManager
import com.minios.elizierdias.linux.LinuxRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class WindowsManager(
    private val context: Context,
) {

    private val prefixManager = PrefixManager()
    private val associationEngine = FileAssociationEngine(context)
    private val processTracker = WindowsProcessTracker()
    private val linuxRuntime = LinuxRuntime(context)
    private val linuxManager = LinuxManager(context)

    private val _isInstalled = MutableStateFlow(false)
    val isInstalled: StateFlow<Boolean> = _isInstalled.asStateFlow()

    private val _statusMessage = MutableStateFlow("Windows layer not installed")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _installProgress = MutableStateFlow<String?>(null)
    val installProgress: StateFlow<String?> = _installProgress.asStateFlow()

    val activeProcesses: StateFlow<List<WindowsProcessInfo>> = processTracker.activeProcesses

    fun initialize() {
        WindowsConfig.ensureDirs(context)
        val installed = WindowsConfig.isInstalled(context)
        _isInstalled.value = installed
        WindowsConfig.setEnabled(installed)
        _statusMessage.value = if (installed) {
            "Windows layer ready (Wine)"
        } else {
            "Windows layer not installed — install on demand"
        }
        prefixManager.ensureDefaultPrefix()
    }

    fun getAssociationEngine(): FileAssociationEngine = associationEngine
    fun getPrefixManager(): PrefixManager = prefixManager
    fun getProcessTracker(): WindowsProcessTracker = processTracker

    fun prepareLaunch(file: File): LaunchResult {
        if (!file.exists() || !file.isFile) {
            return LaunchResult.Failure("Ficheiro não encontrado")
        }
        val (kind, _) = associationEngine.resolve(file)
        if (kind != FileKind.EXECUTABLE_WINDOWS) {
            return LaunchResult.Failure("Este ficheiro não é um executável Windows")
        }
        if (!_isInstalled.value) {
            return LaunchResult.NeedsInstall(
                "A camada Windows ainda não está instalada. " +
                    "Usa o botão em Settings → Windows Compatibility.",
            )
        }
        return LaunchResult.ConfirmationRequired(
            path = file.absolutePath,
            sizeBytes = file.length(),
        )
    }

    suspend fun launchConfirmed(
        file: File,
        prefixName: String = WindowsConfig.DEFAULT_PREFIX_NAME,
        displayName: String? = null,
    ): LaunchResult {
        if (!_isInstalled.value) {
            return LaunchResult.NeedsInstall("Camada Windows não instalada")
        }
        if (!linuxRuntime.isFullyReady()) {
            return LaunchResult.Failure("Linux ainda não está pronto.")
        }

        val name = displayName ?: file.nameWithoutExtension
        val guestExe = WindowsConfig.hostPathToGuest(file.absolutePath)
        val guestPrefix = WindowsConfig.guestPrefixPath(prefixName)
        val quotedExe = shellQuote(guestExe)
        val wineBin = resolveWineBinary()

        prefixManager.getPrefix(prefixName)

        val processId = processTracker.register(
            executablePath = file.absolutePath,
            prefixName = prefixName,
            displayName = name,
        )

        NoskLog.i(
            NoskLog.RUNTIME,
            "Windows launch: bin=$wineBin host=${file.absolutePath} guest=$guestExe",
        )

        val ext = file.extension.lowercase()
        val wineCmd = when (ext) {
            "bat", "cmd" ->
                "WINEPREFIX=$guestPrefix $wineBin cmd /c $quotedExe"
            "msi" ->
                "WINEPREFIX=$guestPrefix $wineBin msiexec /i $quotedExe"
            else ->
                "WINEPREFIX=$guestPrefix $wineBin $quotedExe"
        }

        val result = linuxRuntime.exec(
            command = "export DISPLAY=:1; $wineCmd 2>&1",
            timeoutSec = 300,
        )

        return if (result.isSuccess) {
            val exec = result.getOrThrow()
            try {
                WindowsConfig.logsDir().mkdirs()
                File(WindowsConfig.logsDir(), "${name}_${System.currentTimeMillis()}.log")
                    .writeText(
                        "exit=${exec.exitCode}\n--- stdout ---\n${exec.stdout}\n--- stderr ---\n${exec.stderr}\n",
                    )
            } catch (_: Exception) {
            }

            val snippet = (exec.stdout + "\n" + exec.stderr).take(400)
            if (exec.exitCode != 0 &&
                (snippet.contains("not found", ignoreCase = true) ||
                    snippet.contains("No such file", ignoreCase = true))
            ) {
                processTracker.markFinished(processId)
                LaunchResult.Failure(
                    "Wine não encontrou o ficheiro ou o binário. " +
                        "Usa EXE em /sdcard e confirma que wine/wine64 está instalado.",
                    logSnippet = snippet,
                )
            } else {
                _statusMessage.value = "Launched: $name"
                LaunchResult.Success(processId)
            }
        } else {
            processTracker.markFinished(processId)
            val err = result.exceptionOrNull()?.message ?: "erro desconhecido"
            LaunchResult.Failure("Falha ao executar: $err")
        }
    }

    /**
     * Um botão: Linux se preciso + Wine com progresso em linhas.
     */
    suspend fun installRuntime(onProgress: (String) -> Unit = {}): Result<Unit> {
        fun prog(msg: String) {
            val line = msg.trim().take(200)
            if (line.isEmpty()) return
            _installProgress.value = line
            onProgress(line)
            NoskLog.i(NoskLog.RUNTIME, "Windows install: $line")
        }

        return try {
            WindowsConfig.ensureDirs(context)
            prefixManager.ensureDefaultPrefix()

            if (!linuxRuntime.isFullyReady()) {
                prog("[0/6] Linux não pronto — full-setup automático...")
                _statusMessage.value = "A preparar Linux..."
                linuxManager.initialize()
                val setup = linuxManager.fullSetup()
                if (setup.isFailure || !linuxRuntime.isFullyReady()) {
                    val err = setup.exceptionOrNull()?.message ?: "full-setup falhou"
                    prog("Falha Linux: $err")
                    return Result.failure(IllegalStateException(err))
                }
                prog("[0/6] Linux OK")
            } else {
                prog("[0/6] Linux já ready")
            }

            prog("[1/6] apt-get update...")
            linuxRuntime.execStreaming(
                "apt-get update -y 2>&1",
                timeoutSec = 300,
            ) { line -> prog("[1/6] $line") }

            prog("[2/6] A instalar wine (pode demorar)...")
            val wineInstall = linuxRuntime.execStreaming(
                "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends wine wine64 2>&1",
                timeoutSec = 1200,
            ) { line ->
                // Mostra linhas úteis, evita spam total
                if (line.contains("Get:") ||
                    line.contains("Unpacking") ||
                    line.contains("Setting up") ||
                    line.contains("Processing") ||
                    line.contains("Error") ||
                    line.contains("E:")
                ) {
                    prog("[2/6] $line")
                }
            }

            if (wineInstall.isFailure) {
                prog("[2b/6] Fallback só wine64...")
                linuxRuntime.execStreaming(
                    "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends wine64 2>&1",
                    timeoutSec = 900,
                ) { line -> prog("[2b/6] $line") }
            }

            prog("[3/6] A verificar binário wine/wine64...")
            val which = linuxRuntime.exec(
                "(command -v wine || command -v wine64 || ls /usr/bin/wine* 2>/dev/null || echo MISSING)",
                timeoutSec = 30,
            )
            val winePath = which.getOrNull()?.stdout?.trim().orEmpty()
                .lineSequence().firstOrNull { it.isNotBlank() && !it.contains("MISSING") }
                .orEmpty()

            if (winePath.isEmpty() || winePath.contains("MISSING")) {
                val diag = linuxRuntime.exec(
                    "dpkg -l 'wine*' 2>/dev/null | tail -n 20; echo ---; ls -la /usr/bin/wine* 2>/dev/null || true",
                    timeoutSec = 30,
                ).getOrNull()?.stdout.orEmpty()
                prog("Wine NÃO ficou instalado. diag: ${diag.take(180)}")
                return Result.failure(
                    IllegalStateException(
                        "Wine não ficou no PATH. Corre no Terminal: apt-get update && apt-get install -y wine64",
                    ),
                )
            }
            prog("[3/6] Encontrado: $winePath")

            prog("[4/6] Box64 opcional...")
            linuxRuntime.exec(
                "DEBIAN_FRONTEND=noninteractive apt-get install -y box64 2>/dev/null || true",
                timeoutSec = 300,
            )

            prog("[5/6] wineboot no prefixo...")
            val guestPrefix = WindowsConfig.guestPrefixPath()
            val bin = if (winePath.contains("wine64")) "wine64" else "wine"
            linuxRuntime.exec(
                "export WINEPREFIX=$guestPrefix DISPLAY=:1; $bin wineboot -u 2>&1 || $bin wineboot 2>&1 || true",
                timeoutSec = 180,
            )

            prog("[6/6] A gravar marcadores...")
            WindowsConfig.installedMarker(context).parentFile?.mkdirs()
            WindowsConfig.installedMarker(context)
                .writeText("wine:$winePath\n${System.currentTimeMillis()}")
            WindowsConfig.wineMarker(context).writeText(winePath)

            _isInstalled.value = true
            WindowsConfig.setEnabled(true)
            _statusMessage.value = "Windows layer ready — $winePath"
            prog("═══ Concluído: $winePath ═══")

            Result.success(Unit)
        } catch (e: Exception) {
            _statusMessage.value = "Falha: ${e.message}"
            _installProgress.value = "Erro: ${e.message}"
            NoskLog.e(NoskLog.RUNTIME, "Windows install failed", e)
            Result.failure(e)
        }
    }

    private suspend fun resolveWineBinary(): String {
        val r = linuxRuntime.exec(
            "command -v wine || command -v wine64 || echo wine64",
            timeoutSec = 15,
        )
        return r.getOrNull()?.stdout?.trim()?.lineSequence()?.firstOrNull()
            ?: "wine64"
    }

    fun shutdown() {
        processTracker.listRunning().forEach {
            processTracker.markFinished(it.processId)
        }
        _statusMessage.value = "Windows layer stopped"
    }

    private fun shellQuote(s: String): String {
        if (s.isEmpty()) return "''"
        if (s.all { it.isLetterOrDigit() || it in "/._-:@+=," }) return s
        return "'" + s.replace("'", "'\\''") + "'"
    }
}
