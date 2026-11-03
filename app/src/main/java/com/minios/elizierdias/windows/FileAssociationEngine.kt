/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS / NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.windows

import android.content.Context
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

/**
 * Motor de associação de ficheiros.
 *
 * Decide qual handler (Linux / Windows / Android / Nativo) deve abrir um ficheiro.
 * Nunca assume que um ficheiro desconhecido deve ir para o Wine.
 */
class FileAssociationEngine(
    private val context: Context,
) {

    private val defaultAssociations: List<FileAssociation> = listOf(
        // Documentos
        FileAssociation("application/pdf", listOf("pdf"), "PDF Document", HandlerType.LINUX_APP, "evince"),
        FileAssociation("text/plain", listOf("txt", "log", "md", "csv"), "Text File", HandlerType.LINUX_APP, "nano"),
        FileAssociation("image/jpeg", listOf("jpg", "jpeg"), "JPEG Image", HandlerType.LINUX_APP, "feh"),
        FileAssociation("image/png", listOf("png"), "PNG Image", HandlerType.LINUX_APP, "feh"),
        FileAssociation("image/webp", listOf("webp"), "WebP Image", HandlerType.LINUX_APP, "feh"),
        FileAssociation("image/gif", listOf("gif"), "GIF Image", HandlerType.LINUX_APP, "feh"),

        // Media
        FileAssociation("audio/mpeg", listOf("mp3"), "MP3 Audio", HandlerType.NOSKOS_NATIVE, "media_player"),
        FileAssociation("audio/wav", listOf("wav"), "WAV Audio", HandlerType.NOSKOS_NATIVE, "media_player"),
        FileAssociation("video/mp4", listOf("mp4"), "MP4 Video", HandlerType.NOSKOS_NATIVE, "media_player"),
        FileAssociation("video/webm", listOf("webm"), "WebM Video", HandlerType.NOSKOS_NATIVE, "media_player"),

        // Arquivos
        FileAssociation("application/zip", listOf("zip"), "ZIP Archive", HandlerType.LINUX_APP, "unzip"),
        FileAssociation("application/x-7z-compressed", listOf("7z"), "7z Archive", HandlerType.LINUX_APP, "7z"),
        FileAssociation("application/x-tar", listOf("tar"), "TAR Archive", HandlerType.LINUX_APP, "tar"),

        // Executáveis Windows — SEMPRE exigem confirmação
        FileAssociation("application/x-msdownload", listOf("exe"), "Windows Executable", HandlerType.WINDOWS_WINE),
        FileAssociation("application/x-msi", listOf("msi"), "Windows Installer", HandlerType.WINDOWS_WINE),
        FileAssociation("application/x-bat", listOf("bat", "cmd"), "Windows Batch", HandlerType.WINDOWS_WINE),
        FileAssociation("application/x-ms-screensaver", listOf("scr"), "Windows Screensaver", HandlerType.WINDOWS_WINE),
    )

    /**
     * Identifica o tipo do ficheiro e devolve a associação recomendada.
     * Preferência: extensão + MIME. Em caso de dúvida → UNKNOWN.
     */
    fun resolve(file: File): Pair<FileKind, FileAssociation?> {
        if (!file.exists() || !file.isFile) {
            return FileKind.UNKNOWN to null
        }

        val ext = file.extension.lowercase(Locale.ROOT)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: guessMimeFromExtension(ext)

        val association = defaultAssociations.firstOrNull { assoc ->
            assoc.extensions.any { it.equals(ext, ignoreCase = true) } ||
                assoc.mimeType.equals(mime, ignoreCase = true)
        }

        val kind = when {
            association?.handler == HandlerType.WINDOWS_WINE -> FileKind.EXECUTABLE_WINDOWS
            association?.handler == HandlerType.LINUX_APP && isDocumentLike(association) -> FileKind.DOCUMENT
            association?.handler == HandlerType.NOSKOS_NATIVE -> FileKind.MEDIA
            association?.handler == HandlerType.LINUX_APP && isArchive(association) -> FileKind.ARCHIVE
            else -> FileKind.UNKNOWN
        }

        return kind to association
    }

    fun requiresConfirmation(file: File): Boolean {
        val (kind, _) = resolve(file)
        return kind == FileKind.EXECUTABLE_WINDOWS
    }

    fun isWindowsExecutable(file: File): Boolean {
        val (kind, _) = resolve(file)
        return kind == FileKind.EXECUTABLE_WINDOWS
    }

    private fun isDocumentLike(assoc: FileAssociation): Boolean =
        assoc.mimeType.startsWith("text/") ||
            assoc.mimeType.startsWith("image/") ||
            assoc.mimeType == "application/pdf"

    private fun isArchive(assoc: FileAssociation): Boolean =
        assoc.mimeType.contains("zip") ||
            assoc.mimeType.contains("tar") ||
            assoc.mimeType.contains("7z") ||
            assoc.mimeType.contains("compressed")

    private fun guessMimeFromExtension(ext: String): String =
        when (ext) {
            "exe" -> "application/x-msdownload"
            "msi" -> "application/x-msi"
            "bat", "cmd" -> "application/x-bat"
            "scr" -> "application/x-ms-screensaver"
            "pdf" -> "application/pdf"
            "txt", "log", "md" -> "text/plain"
            else -> "application/octet-stream"
        }
}
