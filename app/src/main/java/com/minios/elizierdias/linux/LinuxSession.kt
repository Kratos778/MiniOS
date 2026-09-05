/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
 *
 * This file is part of MiniOS.
 * Licensed under the MIT License. See LICENSE for details.
 *
 * All rights reserved under applicable law (Republic of Angola & international treaties),
 * subject to the terms of the MIT License.
 */

package com.minios.elizierdias.linux

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents an active Linux shell / session inside the MiniOS Linux runtime.
 *
 * Future: will hold file descriptors, PTY, environment variables and the
 * underlying LinuxProcess that runs /bin/bash (or /bin/sh).
 */
class LinuxSession {

    private val _isAlive = MutableStateFlow(true)
    val isAlive: StateFlow<Boolean> = _isAlive.asStateFlow()

    private val _output = MutableStateFlow<List<String>>(emptyList())
    val output: StateFlow<List<String>> = _output.asStateFlow()

    /**
     * Execute a command in the current session.
     * Skeleton implementation – just records the command.
     */
    fun execute(command: String) {
        if (!_isAlive.value) return
        val newLines = _output.value.toMutableList()
        newLines.add("$ $command")
        newLines.add("[Linux runtime not connected yet – command ignored]")
        _output.value = newLines
    }

    fun clearOutput() {
        _output.value = emptyList()
    }

    fun close() {
        _isAlive.value = false
        // TODO: kill underlying process / close PTY
    }
}
