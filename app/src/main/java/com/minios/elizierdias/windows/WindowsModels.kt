/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS / NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.windows

import androidx.compose.runtime.Immutable

/** Tipo de ficheiro reconhecido pelo motor de associação */
enum class FileKind {
    DOCUMENT,       // PDF, TXT, imagens, etc.
    ARCHIVE,        // ZIP, 7Z, TAR...
    MEDIA,          // Áudio / Vídeo
    EXECUTABLE_WINDOWS, // EXE, MSI, BAT, CMD, SCR
    EXECUTABLE_LINUX,   // ELF / scripts Linux
    UNKNOWN
}

/** Handler responsável por abrir o ficheiro */
enum class HandlerType {
    LINUX_APP,
    WINDOWS_WINE,
    ANDROID_EXTERNAL,
    NOSKOS_NATIVE,
    NONE
}

@Immutable
data class FileAssociation(
    val mimeType: String,
    val extensions: List<String>,
    val displayName: String,
    val handler: HandlerType,
    val preferredAppId: String? = null,   // id da app NoskOS ou nome do binário Linux
    val isDefault: Boolean = true,
)

@Immutable
data class WindowsAppProfile(
    val id: String,
    val displayName: String,
    val executablePath: String,           // caminho absoluto ou relativo ao prefixo
    val workingDirectory: String? = null,
    val prefixName: String = WindowsConfig.DEFAULT_PREFIX_NAME,
    val arguments: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val architecture: WindowsArch = WindowsArch.UNKNOWN,
    val graphicsBackend: GraphicsBackend = GraphicsBackend.AUTO,
    val resolution: String? = null,       // ex: "1280x720"
    val notes: String? = null,
    val compatibilityStatus: CompatibilityStatus = CompatibilityStatus.UNTESTED,
)

enum class WindowsArch {
    X86,
    X86_64,
    ARM64,
    UNKNOWN
}

enum class GraphicsBackend {
    AUTO,
    WINED3D,
    DXVK,
    VKD3D,
    D8VK
}

enum class CompatibilityStatus {
    UNTESTED,
    STARTS,
    WORKS_WITH_LIMITATIONS,
    WORKS,
    INCOMPATIBLE
}

@Immutable
data class WindowsProcessInfo(
    val processId: String,
    val executablePath: String,
    val prefixName: String,
    val startedAt: Long,
    val displayName: String,
    val isRunning: Boolean = true,
)

/** Resultado de uma tentativa de execução */
sealed class LaunchResult {
    data class Success(val processId: String) : LaunchResult()
    data class NeedsInstall(val message: String) : LaunchResult()
    data class ConfirmationRequired(val path: String, val sizeBytes: Long) : LaunchResult()
    data class Failure(val message: String, val logSnippet: String? = null) : LaunchResult()
}
