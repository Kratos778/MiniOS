/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS / NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.windows

import java.io.File

/**
 * Gere prefixos Wine em /sdcard/MiniOS/wine/prefixes.
 */
class PrefixManager {

    fun listPrefixes(): List<String> {
        val dir = WindowsConfig.prefixesDir()
        if (!dir.exists()) return listOf(WindowsConfig.DEFAULT_PREFIX_NAME)
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: listOf(WindowsConfig.DEFAULT_PREFIX_NAME)
    }

    fun ensureDefaultPrefix(): File {
        val prefix = WindowsConfig.defaultPrefixDir()
        if (!prefix.exists()) {
            prefix.mkdirs()
            File(prefix, "drive_c").mkdirs()
            File(prefix, "dosdevices").mkdirs()
        }
        return prefix
    }

    fun getPrefix(name: String = WindowsConfig.DEFAULT_PREFIX_NAME): File {
        val prefix = WindowsConfig.prefixDir(name)
        if (!prefix.exists()) {
            prefix.mkdirs()
            File(prefix, "drive_c").mkdirs()
            File(prefix, "dosdevices").mkdirs()
        }
        return prefix
    }

    fun createPrefix(name: String): Result<File> {
        if (name.isBlank() || name.contains("..") || name.contains("/")) {
            return Result.failure(IllegalArgumentException("Nome de prefixo inválido"))
        }
        val prefix = WindowsConfig.prefixDir(name)
        if (prefix.exists()) {
            return Result.failure(IllegalStateException("Prefixo já existe: $name"))
        }
        return try {
            prefix.mkdirs()
            File(prefix, "drive_c").mkdirs()
            File(prefix, "dosdevices").mkdirs()
            Result.success(prefix)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deletePrefix(name: String): Result<Unit> {
        if (name == WindowsConfig.DEFAULT_PREFIX_NAME) {
            return Result.failure(IllegalArgumentException("Não é permitido apagar o prefixo default"))
        }
        val prefix = WindowsConfig.prefixDir(name)
        if (!prefix.exists()) {
            return Result.failure(IllegalStateException("Prefixo não encontrado: $name"))
        }
        return try {
            prefix.deleteRecursively()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun prefixExists(name: String): Boolean =
        WindowsConfig.prefixDir(name).isDirectory
}
