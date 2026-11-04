/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS / NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.windows

import android.content.Context
import com.minios.elizierdias.core.NoskLog
import com.minios.elizierdias.linux.LinuxRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Gestor principal da camada Windows — Fase 2.
 *
 * - Instalação de Wine (e Box64 se disponível) via apt no Debian/PRoot
 * - Lançamento de EXE/MSI/BAT com WINEPREFIX
 * - Mapeamento de caminhos Android → Debian
 */
class WindowsManager(
    private val context: Context,
) {

    private val prefixManager = PrefixManager()
    private val associationEngine = FileAssociationEngine(context)
    private val processTracker = WindowsProcessTracker()
    private val linuxRuntime = LinuxRuntime(context)

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
                "A camada Windows (Wine) ainda não está instalada. " +
                    "Instale-a em Settings → Windows Compatibility.",
            )
        }
        if (!linuxRuntime.isFullyReady()) {
            return LaunchResult.Failure(
                "Linux (RootFS + PRoot) não está pronto. Corre full-setup no Terminal primeiro.",
            )
        }
        return LaunchResult.ConfirmationRequired(
            path = file.absolutePath,
            sizeBytes = file.length(),
        )
    }

    /**
     * Executa o ficheiro depois da confirmação do utilizador.
     */
    suspend fun launchConfirmed(
        file: File,
        prefixName: String = WindowsConfig.DEFAULT_PREFIX_NAME,
        displayName: String? = null,
    ): LaunchResult {
        if (!_isInstalled.value) {
            return LaunchResult.NeedsInstall("Camada Windows não instalada")
        }
        if (!linuxRuntime.isFullyReady()) {
            return LaunchResult.Failure("Linux não está pronto (RootFS + PRoot)")
        }

        val name = displayName ?: file.nameWithoutExtension
        val guestExe = WindowsConfig.hostPathToGuest(file.absolutePath)
        val guestPrefix = WindowsConfig.guestPrefixPath(prefixName)

        // Garante estrutura do prefixo no host
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
                "WINEPREFIX=$guestPrefix wine cmd /c $(shellQuote(guestExe))"
            "msi" ->
                "WINEPREFIX=$guestPrefix wine msiexec /i $(shellQuote(guestExe))"
            else ->
                "WINEPREFIX=$guestPrefix wine $(shellQuote(guestExe))"
        }

        // Corre em background relativamente longo; captura saída para log
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

            if (exec.exitCode == 0) {
                _statusMessage.value = "Launched: $name"
                NoskLog.i(NoskLog.RUNTIME, "Wine OK: $name")
                LaunchResult.Success(processId)
            } else {
                // Muitos programas gráficos saem com código != 0 quando a sessão acaba;
                // ainda assim registamos sucesso parcial se houver saída útil.
                val snippet = (exec.stdout + "\n" + exec.stderr).take(400)
                NoskLog.w(NoskLog.RUNTIME, "Wine exit ${exec.exitCode}: $snippet")
                processTracker.markFinished(processId)
                if (snippet.contains("not found", ignoreCase = true) ||
                    snippet.contains("No such file", ignoreCase = true)
                ) {
                    LaunchResult.Failure(
                        "Wine não encontrou o ficheiro ou o próprio wine. " +
                            "Verifica se o caminho está em /sdcard e se wine está instalado.",
                        logSnippet = snippet,
                    )
                } else {
                    // Considera lançado; o utilizador pode ter fechado a janela
                    _statusMessage.value = "Wine terminou (exit ${exec.exitCode}): $name"
                    LaunchResult.Success(processId)
                }
            }
        } else {
            processTracker.markFinished(processId)
            val err = result.exceptionOrNull()?.message ?: "erro desconhecido"
            NoskLog.e(NoskLog.RUNTIME, "Wine launch failed: $err")
            LaunchResult.Failure("Falha ao executar: $err")
        }
    }

    /**
     * Instalação real via apt no Debian do NoskOS.
     */
    suspend fun installRuntime(onProgress: (String) -> Unit = {}): Result<Unit> {
        if (!linuxRuntime.isFullyReady()) {
            val msg = "Linux (RootFS + PRoot) não está pronto. Corre 'full-setup' no Terminal primeiro."
            _statusMessage.value = msg
            _installProgress.value = msg
            onProgress(msg)
            return Result.failure(IllegalStateException(msg))
        }

        return try {
            WindowsConfig.ensureDirs(context)
            prefixManager.ensureDefaultPrefix()

            fun prog(msg: String) {
                _installProgress.value = msg
                onProgress(msg)
                NoskLog.i(NoskLog.RUNTIME, "Windows install: $msg")
            }

            prog("[1/5] A atualizar índices apt...")
            linuxRuntime.exec("apt-get update -y", timeoutSec = 300)

            prog("[2/5] A instalar wine...")
            val wineResult = linuxRuntime.exec(
                "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends wine",
                timeoutSec = 900,
            )
            if (wineResult.isFailure) {
                val err = wineResult.exceptionOrNull()?.message ?: "apt wine failed"
                prog("Falha wine: $err")
                // Tenta wine64 / wine32 como fallback de nome de pacote
                prog("[2b/5] A tentar pacotes alternativos (wine64)...")
                linuxRuntime.exec(
                    "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends wine64 wine32 || true",
                    timeoutSec = 600,
                )
            }

            prog("[3/5] A verificar se wine está no PATH...")
            val which = linuxRuntime.exec("which wine || which wine64 || echo MISSING", timeoutSec = 30)
            val winePath = which.getOrNull()?.stdout?.trim().orEmpty()
            if (winePath.isEmpty() || winePath.contains("MISSING")) {
                val msg = "Wine não ficou disponível no PATH após apt. Verifica repositórios Debian."
                prog(msg)
                return Result.failure(IllegalStateException(msg))
            }
            prog("Wine encontrado: $winePath")

            prog("[4/5] A tentar box64 (opcional, para x86_64)...")
            linuxRuntime.exec(
                "DEBIAN_FRONTEND=noninteractive apt-get install -y box64 2>/dev/null || true",
                timeoutSec = 300,
            )

            prog("[5/5] A inicializar prefixo default (wineboot)...")
            val guestPrefix = WindowsConfig.guestPrefixPath()
            linuxRuntime.exec(
                "export WINEPREFIX=$guestPrefix DISPLAY=:1; wineboot -u 2>&1 || wineboot 2>&1 || true",
                timeoutSec = 180,
            )

            // Marcadores
            WindowsConfig.installedMarker(context).parentFile?.mkdirs()
            WindowsConfig.installedMarker(context).writeText("wine:$winePath\n${System.currentTimeMillis()}")
            WindowsConfig.wineMarker(context).writeText(winePath)

            _isInstalled.value = true
            WindowsConfig.setEnabled(true)
            _statusMessage.value = "Windows layer ready — Wine em $winePath"
            prog("═══ Instalação concluída ═══")

            Result.success(Unit)
        } catch (e: Exception) {
            _statusMessage.value = "Falha na instalação: ${e.message}"
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
