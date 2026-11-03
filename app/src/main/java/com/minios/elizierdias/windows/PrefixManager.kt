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
 * Gere prefixos Wine.
 *
 * Um prefixo é uma pasta que simula um ambiente Windows (drive_c, registo, etc.).
 * O prefixo "default" é usado por defeito.
 */
class PrefixManager(
    private val context: Context,
) {

    fun listPrefixes(): List<String> {
        val dir = WindowsConfig.prefixesDir(context)
        if (!dir.exists()) return listOf(WindowsConfig.DEFAULT_PREFIX_NAME)
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: listOf(WindowsConfig.DEFAULT_PREFIX_NAME)
    }

    fun ensureDefaultPrefix(): File {
        WindowsConfig.ensureDirs(context)
        val prefix = WindowsConfig.defaultPrefixDir(context)
        if (!prefix.exists()) {
            prefix.mkdirs()
            // Estrutura mínima esperada pelo Wine (criada na primeira execução real do wineboot)
            File(prefix, "drive_c").mkdirs()
            File(prefix, "dosdevices").mkdirs()
        }
        return prefix
    }

    fun getPrefix(name: String = WindowsConfig.DEFAULT_PREFIX_NAME): File {
        val prefix = WindowsConfig.prefixDir(context, name)
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
        val prefix = WindowsConfig.prefixDir(context, name)
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
        val prefix = WindowsConfig.prefixDir(context, name)
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
        WindowsConfig.prefixDir(context, name).isDirectory
}
