/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.linux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LinuxSession(
    private val runtime: LinuxRuntime,
) {

    private val _isAlive = MutableStateFlow(true)
    val isAlive: StateFlow<Boolean> = _isAlive.asStateFlow()

    private val _output = MutableStateFlow<List<String>>(emptyList())
    val output: StateFlow<List<String>> = _output.asStateFlow()

    var cwd: String = "/root"
        private set

    private val sessionEnv = mutableMapOf(
        "HOME" to "/root",
        "USER" to "root",
        "LOGNAME" to "root",
        "TERM" to "xterm-256color",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "LANG" to "C.UTF-8",
        "DEBIAN_FRONTEND" to "noninteractive",
    )

    fun envSnapshot(): Map<String, String> = sessionEnv.toMap()

    private fun timeoutFor(command: String): Long {
        val c = command.lowercase()
        val longJob =
            c.contains("apt-get") ||
                c.contains("apt ") ||
                c.startsWith("apt") ||
                c.contains("dpkg") ||
                c.contains("pkg-install") ||
                c.contains("tigervnc") ||
                c.contains("openbox") ||
                c.contains("vncserver") ||
                c.contains("install -y") ||
                c.contains("wget") ||
                c.contains("curl") ||
                c.contains("falkon")
        return if (longJob) 2_400L else 180L
    }

    /**
     * Executa um comando (pode ser multi-linha / heredoc).
     * Scripts multi-linha passam por `bash -s` para preservar <<EOF.
     */
    suspend fun execute(
        command: String,
        onLine: ((String) -> Unit)? = null,
    ): String = withContext(Dispatchers.IO) {
        if (!_isAlive.value) return@withContext ""

        val trimmed = command.trim()
        if (trimmed.isEmpty()) return@withContext ""

        val isMultiLine = trimmed.contains('\n')

        // Builtins só em linha única
        if (!isMultiLine) {
            if (trimmed == "cd" || trimmed.startsWith("cd ")) {
                return@withContext handleCd(trimmed)
            }
            if (trimmed.startsWith("export ")) {
                return@withContext handleExport(trimmed.removePrefix("export ").trim())
            }
            if (trimmed == "pwd") {
                appendOutput(listOf(cwd))
                onLine?.invoke(cwd)
                return@withContext cwd
            }
        }

        val wrapped = buildString {
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
            if (isMultiLine) {
                // bash -s lê o script do stdin embutido — heredocs funcionam
                append("bash -s <<'NOSKOS_EOF'\n")
                append(trimmed)
                append("\nNOSKOS_EOF")
            } else {
                append(trimmed)
            }
            append(" 2>&1")
        }

        val timeout = timeoutFor(trimmed)
        val result = if (onLine != null) {
            runtime.execStreaming(wrapped, timeoutSec = timeout, onLine = onLine)
        } else {
            runtime.exec(wrapped, timeoutSec = timeout)
        }

        val lines = mutableListOf<String>()

        if (result.isFailure) {
            val msg = result.exceptionOrNull()?.message ?: "unknown error"
            lines.add("error: $msg")
            if (msg.contains("timed out", ignoreCase = true)) {
                lines.add("(comando longo cortado — tenta de novo ou repair-dpkg)")
            }
            if (onLine == null) appendOutput(lines)
            else lines.forEach { onLine(it) }
            return@withContext lines.joinToString("\n")
        }

        val exec = result.getOrThrow()
        if (onLine == null) {
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
            return@withContext lines.joinToString("\n")
        }

        if (exec.exitCode != 0) {
            onLine("[exit ${exec.exitCode}]")
        }
        exec.stdout
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
        val normalized = normalizePath(target)

        val check = runtime.exec("test -d ${shellQuote(normalized)} && echo OK", timeoutSec = 30)
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
