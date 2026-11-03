/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS / NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.windows

import android.content.Context
import com.minios.elizierdias.core.NoskLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Gestor principal da camada Windows.
 *
 * Responsabilidades:
 * - Estado da instalação (Wine + Box64)
 * - Lançamento de EXE / MSI / BAT / CMD
 * - Coordenação entre PrefixManager, FileAssociationEngine e ProcessTracker
 *
 * A implementação real da execução (chamar wine através do PRoot/LinuxRuntime)
 * será ligada na Fase 2. Nesta base apenas a arquitetura e os contratos estão prontos.
 */
class WindowsManager(
    private val context: Context,
) {

    private val prefixManager = PrefixManager(context)
    private val associationEngine = FileAssociationEngine(context)
    private val processTracker = WindowsProcessTracker()

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
            "Windows layer ready"
        } else {
            "Windows layer not installed — install on demand"
        }
        prefixManager.ensureDefaultPrefix()
    }

    fun getAssociationEngine(): FileAssociationEngine = associationEngine
    fun getPrefixManager(): PrefixManager = prefixManager
    fun getProcessTracker(): WindowsProcessTracker = processTracker

    /**
     * Ponto de entrada para abrir um ficheiro.
     * Devolve ConfirmationRequired para EXE/MSI etc.
     */
    fun prepareLaunch(file: File): LaunchResult {
        if (!file.exists() || !file.isFile) {
            return LaunchResult.Failure("Ficheiro não encontrado")
        }

        val (kind, association) = associationEngine.resolve(file)

        if (kind != FileKind.EXECUTABLE_WINDOWS) {
            return LaunchResult.Failure("Este ficheiro não é um executável Windows")
        }

        if (!_isInstalled.value) {
            return LaunchResult.NeedsInstall(
                "A camada Windows (Wine) ainda não está instalada. " +
                    "Instale-a em Settings → Windows Compatibility."
            )
        }

        // Sempre pedir confirmação na primeira execução de um EXE
        return LaunchResult.ConfirmationRequired(
            path = file.absolutePath,
            sizeBytes = file.length(),
        )
    }

    /**
     * Executa o ficheiro depois da confirmação do utilizador.
     * Nesta fase apenas regista o processo e devolve sucesso simulado.
     * A ligação real ao Wine/LinuxRuntime será feita na Fase 2.
     */
    suspend fun launchConfirmed(
        file: File,
        prefixName: String = WindowsConfig.DEFAULT_PREFIX_NAME,
        displayName: String? = null,
    ): LaunchResult {
        if (!_isInstalled.value) {
            return LaunchResult.NeedsInstall("Camada Windows não instalada")
        }

        val name = displayName ?: file.nameWithoutExtension
        val processId = processTracker.register(
            executablePath = file.absolutePath,
            prefixName = prefixName,
            displayName = name,
        )

        NoskLog.i("WindowsManager", "Launch requested: ${file.absolutePath} (prefix=$prefixName)")

        // TODO Fase 2: chamar Wine via LinuxRuntime / PRoot
        // Exemplo conceptual:
        //   WINEPREFIX=<prefix> wine <exe>
        // ou com Box64 se for x86_64

        _statusMessage.value = "Launched: $name"
        return LaunchResult.Success(processId)
    }

    /**
     * Instalação sob demanda da camada Windows.
     * Nesta base apenas cria a estrutura e marca como instalada.
     * A descarga real de Wine/Box64 será implementada na Fase 2.
     */
    suspend fun installRuntime(onProgress: (String) -> Unit = {}): Result<Unit> {
        return try {
            _installProgress.value = "A preparar estrutura..."
            onProgress("A preparar estrutura...")
            WindowsConfig.ensureDirs(context)
            prefixManager.ensureDefaultPrefix()

            _installProgress.value = "A marcar runtime (placeholder)..."
            onProgress("A marcar runtime (placeholder)...")

            // Placeholder: na Fase 2 aqui será feita a descarga/instalação real
            WindowsConfig.installedMarker(context).writeText("installed_placeholder_${System.currentTimeMillis()}")
            WindowsConfig.wineMarker(context).writeText("wine_placeholder")
            WindowsConfig.box64Marker(context).writeText("box64_placeholder")

            _isInstalled.value = true
            WindowsConfig.setEnabled(true)
            _statusMessage.value = "Windows layer installed (placeholder — Fase 2 liga Wine real)"
            _installProgress.value = "Concluído (placeholder)"
            onProgress("Concluído (placeholder)")

            Result.success(Unit)
        } catch (e: Exception) {
            _statusMessage.value = "Falha na instalação: ${e.message}"
            Result.failure(e)
        }
    }

    fun shutdown() {
        processTracker.listRunning().forEach {
            processTracker.markFinished(it.processId)
        }
        _statusMessage.value = "Windows layer stopped"
    }
}
