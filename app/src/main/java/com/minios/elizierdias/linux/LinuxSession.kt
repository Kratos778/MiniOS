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
 * Linux shell session. Commands are executed through [LinuxRuntime] (PRoot).
 */
class LinuxSession(
    private val runtime: LinuxRuntime,
) {

    private val _isAlive = MutableStateFlow(true)
    val isAlive: StateFlow<Boolean> = _isAlive.asStateFlow()

    private val _output = MutableStateFlow<List<String>>(emptyList())
    val output: StateFlow<List<String>> = _output.asStateFlow()

    suspend fun execute(command: String): String = withContext(Dispatchers.IO) {
        if (!_isAlive.value) return@withContext ""

        val result = runtime.exec(command)
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

    private fun appendOutput(newLines: List<String>) {
        _output.value = _output.value + newLines
    }

    fun clearOutput() {
        _output.value = emptyList()
    }

    fun close() {
        _isAlive.value = false
    }
}
