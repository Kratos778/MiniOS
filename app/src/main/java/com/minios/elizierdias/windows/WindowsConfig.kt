/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS / NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.windows

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Caminhos e constantes da camada Windows (Wine + Box64).
 *
 * Prefixos ficam em /sdcard/MiniOS/wine para serem visíveis dentro do
 * Debian via os binds de armazenamento já existentes.
 */
object WindowsConfig {

    const val PUBLIC_WINE_DIR = "wine"
    const val PREFIXES_DIR_NAME = "prefixes"
    const val LOGS_DIR_NAME = "logs"
    const val CONFIGS_DIR_NAME = "configs"

    const val DEFAULT_PREFIX_NAME = "default"
    const val INSTALLED_MARKER = ".noskos_windows_installed"
    const val WINE_MARKER = ".noskos_wine_ok"

    /** Caminho guest (dentro do Debian) para os prefixos */
    const val GUEST_WINE_ROOT = "/sdcard/MiniOS/wine"

    var enabled: Boolean = false
        private set

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /** /sdcard/MiniOS/wine */
    fun wineRootDir(): File {
        val base = File(Environment.getExternalStorageDirectory(), "MiniOS")
        return File(base, PUBLIC_WINE_DIR)
    }

    fun prefixesDir(): File =
        File(wineRootDir(), PREFIXES_DIR_NAME)

    fun logsDir(): File =
        File(wineRootDir(), LOGS_DIR_NAME)

    fun configsDir(): File =
        File(wineRootDir(), CONFIGS_DIR_NAME)

    fun defaultPrefixDir(): File =
        File(prefixesDir(), DEFAULT_PREFIX_NAME)

    fun prefixDir(name: String): File =
        File(prefixesDir(), name)

    /** Marcador no storage privado da app (não depende do sdcard) */
    fun installedMarker(context: Context): File =
        File(context.filesDir, "windows_runtime/$INSTALLED_MARKER")

    fun wineMarker(context: Context): File =
        File(context.filesDir, "windows_runtime/$WINE_MARKER")

    fun ensureDirs(context: Context) {
        try {
            wineRootDir().mkdirs()
            prefixesDir().mkdirs()
            logsDir().mkdirs()
            configsDir().mkdirs()
            defaultPrefixDir().mkdirs()
            File(context.filesDir, "windows_runtime").mkdirs()
        } catch (_: Exception) {
        }
    }

    fun isInstalled(context: Context): Boolean =
        installedMarker(context).isFile

    /**
     * Converte um caminho Android host para o caminho visto dentro do Debian.
     * Exemplos:
     *   /storage/emulated/0/Download/app.exe → /sdcard/Download/app.exe
     *   /sdcard/Download/app.exe → /sdcard/Download/app.exe
     */
    fun hostPathToGuest(hostPath: String): String {
        val p = hostPath.replace('\\', '/')
        return when {
            p.startsWith("/storage/emulated/0") ->
                p.replaceFirst("/storage/emulated/0", "/sdcard")
            p.startsWith("/sdcard") -> p
            p.startsWith("/data/data") || p.startsWith("/data/user") ->
                // ficheiros privados da app — não estão montados por defeito
                p
            else -> p
        }
    }

    fun guestPrefixPath(name: String = DEFAULT_PREFIX_NAME): String =
        "$GUEST_WINE_ROOT/prefixes/$name"
}
