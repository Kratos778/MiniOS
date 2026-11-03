/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS / NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.windows

import android.content.Context
import java.io.File

/**
 * Caminhos e constantes da camada Windows (Wine + Box64).
 *
 * A camada é **opcional** e instalada sob demanda.
 * Não faz parte do APK base.
 */
object WindowsConfig {

    const val WINDOWS_DIR_NAME = "windows_runtime"
    const val PREFIXES_DIR_NAME = "prefixes"
    const val RUNTIME_DIR_NAME = "runtime"
    const val LOGS_DIR_NAME = "logs"
    const val CONFIGS_DIR_NAME = "configs"

    const val DEFAULT_PREFIX_NAME = "default"
    const val INSTALLED_MARKER = ".noskos_windows_installed"
    const val WINE_MARKER = ".noskos_wine_ok"
    const val BOX64_MARKER = ".noskos_box64_ok"

    /** Estado global da camada (persistido em SharedPreferences se necessário) */
    var enabled: Boolean = false
        private set

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun windowsDir(context: Context): File =
        File(context.filesDir, WINDOWS_DIR_NAME)

    fun prefixesDir(context: Context): File =
        File(windowsDir(context), PREFIXES_DIR_NAME)

    fun runtimeDir(context: Context): File =
        File(windowsDir(context), RUNTIME_DIR_NAME)

    fun logsDir(context: Context): File =
        File(windowsDir(context), LOGS_DIR_NAME)

    fun configsDir(context: Context): File =
        File(windowsDir(context), CONFIGS_DIR_NAME)

    fun defaultPrefixDir(context: Context): File =
        File(prefixesDir(context), DEFAULT_PREFIX_NAME)

    fun prefixDir(context: Context, name: String): File =
        File(prefixesDir(context), name)

    fun installedMarker(context: Context): File =
        File(windowsDir(context), INSTALLED_MARKER)

    fun wineMarker(context: Context): File =
        File(runtimeDir(context), WINE_MARKER)

    fun box64Marker(context: Context): File =
        File(runtimeDir(context), BOX64_MARKER)

    fun appProfilesFile(context: Context): File =
        File(configsDir(context), "app_profiles.json")

    fun associationsFile(context: Context): File =
        File(configsDir(context), "file_associations.json")

    /** Garante a estrutura de pastas base */
    fun ensureDirs(context: Context) {
        windowsDir(context).mkdirs()
        prefixesDir(context).mkdirs()
        runtimeDir(context).mkdirs()
        logsDir(context).mkdirs()
        configsDir(context).mkdirs()
        defaultPrefixDir(context).mkdirs()
    }

    fun isInstalled(context: Context): Boolean =
        installedMarker(context).isFile
}
