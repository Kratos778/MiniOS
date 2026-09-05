/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 * This file is part of MiniOS.
 * See LICENSE and COPYRIGHT.md for full terms.
 *
 * Unauthorized copying, modification, distribution or reuse of this file,
 * via any medium, is strictly prohibited without prior written permission.
 */

package com.minios.elizierdias.linux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Sessão de shell Linux no Terminal.
 *
 * Ainda não é PTY interativo completo: cada comando corre via proot+bash -c,
 * mas mantém cwd e variáveis de ambiente entre comandos (como um shell simples).
 */
class LinuxSession(
    private val runtime: LinuxRuntime,
) {

    private val _isAlive = MutableStateFlow(true)
    val isAlive: StateFlow<Boolean> = _isAlive.asStateFlow()

    private val _output = MutableStateFlow<List<String>>(emptyList())
    val output: StateFlow<List<String>> = _output.asStateFlow()

    /** Working directory persistente na sessão */
    var cwd: String = "/root"
        private set

    /** Env vars da sessão (export) */
    private val sessionEnv = mutableMapOf(
        "HOME" to "/root",
        "USER" to "root",
        "LOGNAME" to "root",
        "TERM" to "xterm-256color",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "LANG" to "C.UTF-8",
    )

    fun envSnapshot(): Map<String, String> = sessionEnv.toMap()

    /**
     * Executa um comando na sessão (cwd + env persistentes).
     */
    suspend fun execute(command: String): String = withContext(Dispatchers.IO) {
        if (!_isAlive.value) return@withContext ""

        val trimmed = command.trim()
        if (trimmed.isEmpty()) return@withContext ""

        // cd isolado
        if (trimmed == "cd" || trimmed.startsWith("cd ")) {
            return@withContext handleCd(trimmed)
        }

        // export VAR=value
        if (trimmed.startsWith("export ")) {
            return@withContext handleExport(trimmed.removePrefix("export ").trim())
        }

        // pwd local (rápido)
        if (trimmed == "pwd") {
            appendOutput(listOf(cwd))
            return@withContext cwd
        }

        // Comando real via PRoot, com cwd e env da sessão
        val wrapped = buildString {
            // exports
            sessionEnv.forEach { (k, v) ->
                append("export ")
                append(shellQuote(k))
                append("=")
                append(shellQuote(v))
                append("; ")
            }
            append("cd ")
            append(shellQuote(cwd))
            append(" && ")
            append(trimmed)
        }

        val result = runtime.exec(wrapped)
        val lines = mutableListOf<String>()

        if (result.isFailure) {
            val msg = result.exceptionOrNull()?.message ?: "unknown error"
            lines.add("error: $msg")
            appendOutput(lines)
            return@withContext lines.joinToString("\n")
        }

        val exec = result.getOrThrow()
        if (exec.stdout.isNotBlank()) {
            exec.stdout.lines().forEach { lines.add(it) }
        }
        if (exec.stderr.isNotBlank()) {
            exec.stderr.lines().forEach { lines.add(it) }
        }
        if (exec.exitCode != 0 && lines.isEmpty()) {
            lines.add("[exit ${exec.exitCode}]")
        }
        appendOutput(lines)
        lines.joinToString("\n")
    }

    private suspend fun handleCd(cmd: String): String {
        val arg = cmd.removePrefix("cd").trim().ifEmpty { "/root" }
        val target = when {
            arg == "~" || arg.startsWith("~/") -> {
                val rest = arg.removePrefix("~").removePrefix("/")
                if (rest.isEmpty()) "/root" else "/root/$rest"
            }
            arg.startsWith("/") -> arg
            else -> {
                val base = if (cwd.endsWith("/")) cwd.dropLast(1) else cwd
                "$base/$arg"
            }
        }
        // Normaliza // e .
        val normalized = normalizePath(target)

        // Verifica se o diretório existe dentro do RootFS via proot
        val check = runtime.exec("test -d ${shellQuote(normalized)} && echo OK")
        val ok = check.getOrNull()?.stdout?.contains("OK") == true
        if (!ok) {
            val msg = "cd: $normalized: No such file or directory"
            appendOutput(listOf(msg))
            return msg
        }
        cwd = normalized
        return ""
    }

    private fun handleExport(body: String): String {
        val eq = body.indexOf('=')
        if (eq <= 0) {
            val msg = "export: usage: export NAME=value"
            appendOutput(listOf(msg))
            return msg
        }
        val name = body.substring(0, eq).trim()
        var value = body.substring(eq + 1).trim()
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))
        ) {
            value = value.substring(1, value.length - 1)
        }
        if (!name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
            val msg = "export: invalid name: $name"
            appendOutput(listOf(msg))
            return msg
        }
        sessionEnv[name] = value
        return ""
    }

    private fun normalizePath(path: String): String {
        val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
        val stack = mutableListOf<String>()
        for (p in parts) {
            if (p == "..") {
                if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            } else {
                stack.add(p)
            }
        }
        return "/" + stack.joinToString("/")
    }

    private fun shellQuote(s: String): String {
        if (s.isEmpty()) return "''"
        if (s.all { it.isLetterOrDigit() || it in "/._-:@+=," }) return s
        return "'" + s.replace("'", "'\\''") + "'"
    }

    private fun appendOutput(newLines: List<String>) {
        if (newLines.isEmpty()) return
        _output.value = _output.value + newLines
    }

    fun clearOutput() {
        _output.value = emptyList()
    }

    fun close() {
        _isAlive.value = false
    }
}
