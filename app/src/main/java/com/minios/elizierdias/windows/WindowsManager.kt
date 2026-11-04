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

/**
 * Gestor principal da camada Windows — Fase 2.
 *
 * Instalação: se Linux não estiver pronto, corre fullSetup automaticamente.
 * Lançamento: Wine via PRoot com mapeamento de caminhos.
 */
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
            return LaunchResult.Failure(
                "Linux ainda não está pronto. Volta a Settings e instala a camada Windows " +
                    "(faz o setup do Linux automaticamente).",
            )
        }

        val name = displayName ?: file.nameWithoutExtension
        val guestExe = WindowsConfig.hostPathToGuest(file.absolutePath)
        val guestPrefix = WindowsConfig.guestPrefixPath(prefixName)
        val quotedExe = shellQuote(guestExe)

        prefixManager.getPrefix(prefixName)

        val processId = processTracker.register(
            executablePath = file.absolutePath,
            prefixName = prefixName,
            displayName = name,
        )

        NoskLog.i(
            NoskLog.RUNTIME,
            "Windows launch: host=${file.absolutePath} guest=$guestExe prefix=$guestPrefix",
        )

        val ext = file.extension.lowercase()
        val wineCmd = when (ext) {
            "bat", "cmd" ->
                "WINEPREFIX=$guestPrefix wine cmd /c $quotedExe"
            "msi" ->
                "WINEPREFIX=$guestPrefix wine msiexec /i $quotedExe"
            else ->
                "WINEPREFIX=$guestPrefix wine $quotedExe"
        }

        val result = linuxRuntime.exec(
            command = "export DISPLAY=:1; $wineCmd 2>&1",
            timeoutSec = 300,
        )

        return if (result.isSuccess) {
            val exec = result.getOrThrow()
            val logFile = File(WindowsConfig.logsDir(), "${name}_${System.currentTimeMillis()}.log")
            try {
                WindowsConfig.logsDir().mkdirs()
                logFile.writeText(
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
                    "Wine não encontrou o ficheiro ou o binário wine. " +
                        "Garante que o EXE está em /sdcard (Download, etc.).",
                    logSnippet = snippet,
                )
            } else {
                _statusMessage.value = "Launched: $name"
                NoskLog.i(NoskLog.RUNTIME, "Wine finished exit=${exec.exitCode}: $name")
                LaunchResult.Success(processId)
            }
        } else {
            processTracker.markFinished(processId)
            val err = result.exceptionOrNull()?.message ?: "erro desconhecido"
            NoskLog.e(NoskLog.RUNTIME, "Wine launch failed: $err")
            LaunchResult.Failure("Falha ao executar: $err")
        }
    }

    /**
     * Um único botão: prepara Linux se preciso + instala Wine.
     * O utilizador não precisa de abrir o Terminal.
     */
    suspend fun installRuntime(onProgress: (String) -> Unit = {}): Result<Unit> {
        fun prog(msg: String) {
            _installProgress.value = msg
            onProgress(msg)
            NoskLog.i(NoskLog.RUNTIME, "Windows install: $msg")
        }

        return try {
            WindowsConfig.ensureDirs(context)
            prefixManager.ensureDefaultPrefix()

            // —— Auto Linux setup ——
            if (!linuxRuntime.isFullyReady()) {
                prog("[0/6] Linux não está pronto — a correr full-setup automático...")
                _statusMessage.value = "A preparar Linux (automático)..."
                linuxManager.initialize()
                val setup = linuxManager.fullSetup()
                if (setup.isFailure || !linuxRuntime.isFullyReady()) {
                    val err = setup.exceptionOrNull()?.message
                        ?: "full-setup não deixou o Linux pronto"
                    prog("Falha no Linux: $err")
                    _statusMessage.value = "Precisas de permissão de armazenamento e internet para o Linux."
                    return Result.failure(IllegalStateException(err))
                }
                prog("[0/6] Linux OK")
            }

            prog("[1/6] A atualizar índices apt...")
            linuxRuntime.exec("apt-get update -y", timeoutSec = 300)

            prog("[2/6] A instalar wine...")
            val wineResult = linuxRuntime.exec(
                "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends wine",
                timeoutSec = 900,
            )
            if (wineResult.isFailure) {
                prog("[2b/6] Fallback wine64/wine32...")
                linuxRuntime.exec(
                    "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends wine64 wine32 || true",
                    timeoutSec = 600,
                )
            }

            prog("[3/6] A verificar wine no PATH...")
            val which = linuxRuntime.exec(
                "which wine || which wine64 || echo MISSING",
                timeoutSec = 30,
            )
            val winePath = which.getOrNull()?.stdout?.trim().orEmpty()
            if (winePath.isEmpty() || winePath.contains("MISSING")) {
                val msg = "Wine não ficou no PATH. Verifica internet e repositórios Debian."
                prog(msg)
                return Result.failure(IllegalStateException(msg))
            }
            prog("Wine: $winePath")

            prog("[4/6] Box64 opcional...")
            linuxRuntime.exec(
                "DEBIAN_FRONTEND=noninteractive apt-get install -y box64 2>/dev/null || true",
                timeoutSec = 300,
            )

            prog("[5/6] wineboot no prefixo default...")
            val guestPrefix = WindowsConfig.guestPrefixPath()
            linuxRuntime.exec(
                "export WINEPREFIX=$guestPrefix DISPLAY=:1; wineboot -u 2>&1 || wineboot 2>&1 || true",
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
            prog("═══ Concluído ═══")

            Result.success(Unit)
        } catch (e: Exception) {
            _statusMessage.value = "Falha: ${e.message}"
            _installProgress.value = "Erro: ${e.message}"
            NoskLog.e(NoskLog.RUNTIME, "Windows install failed", e)
            Result.failure(e)
        }
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
