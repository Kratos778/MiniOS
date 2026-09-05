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

package com.minios.elizierdias.apps.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minios.elizierdias.linux.LinuxConfig
import com.minios.elizierdias.linux.LinuxManager
import com.minios.elizierdias.linux.LinuxRootFs
import com.minios.elizierdias.linux.LinuxSession
import kotlinx.coroutines.launch

@Composable
fun TerminalApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val linuxManager = remember(context) { LinuxManager(context) }
    val statusMessage by linuxManager.statusMessage.collectAsState()
    val rootFsStatus by linuxManager.rootFsStatus.collectAsState()
    val isReady by linuxManager.isReady.collectAsState()
    val installProgress by linuxManager.installProgress.collectAsState()

    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<LinuxSession?>(null) }

    val lines = remember {
        mutableStateListOf(
            "MiniOS Linux Terminal",
            "Debian ARM64 via PRoot (sem root)",
            "",
        )
    }

    fun scrollToBottom() {
        scope.launch {
            listState.animateScrollToItem((lines.size - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(Unit) {
        linuxManager.initialize()
        val status = linuxManager.getRootFs().status()
        val rt = linuxManager.getRuntime()
        lines.add("── estado ──")
        lines.add(statusMessage)
        lines.add("rootfs : ${status.rootfsPath}")
        lines.add(
            "installed: ${status.isInstalled} " +
                "(${status.distro ?: "—"}) " +
                LinuxRootFs.formatSize(status.estimatedSizeBytes),
        )
        lines.add("proot  : ${if (rt.isProotInstalled()) "ok" else "em falta"}")
        lines.add("storage: ${if (rt.isStorageReady()) "ok" else "corre setup-storage"}")
        lines.add("")
        if (!status.isInstalled) {
            lines.add("1) install")
            lines.add("2) setup-runtime")
            lines.add("3) setup-storage")
        } else if (!rt.isProotInstalled()) {
            lines.add("Próximo: setup-runtime")
        } else {
            lines.add("Linux pronto. Se deres Permission denied → repair-proot")
            lines.add("Exemplos: uname -a | ls / | whoami")
            session = linuxManager.startSession()
        }
        lines.add("")
        scrollToBottom()
    }

    LaunchedEffect(installProgress) {
        val msg = installProgress ?: return@LaunchedEffect
        if (lines.lastOrNull() != msg) {
            lines.add(msg)
            scrollToBottom()
        }
    }

    fun normalizeBuiltin(cmd: String): String {
        val c = cmd.trim().lowercase()
        return when (c) {
            "setup-storege", "setup-stroage", "setupstorage", "setup_storage" ->
                "setup-storage"
            "setup-runtime", "setupruntime", "setup_runtime" ->
                "setup-runtime"
            "repair-proot", "repairproot", "fix-proot" ->
                "repair-proot"
            else -> cmd.trim()
        }
    }

    fun runBuiltin(raw: String): Boolean {
        val cmd = normalizeBuiltin(raw)
        when (cmd) {
            "help" -> {
                lines.add("Comandos MiniOS Linux:")
                lines.add("  help           — ajuda")
                lines.add("  status         — estado RootFS/PRoot/storage")
                lines.add("  install        — Debian ARM64")
                lines.add("  setup-runtime  — descarregar PRoot")
                lines.add("  setup-storage  — /sdcard, Download…")
                lines.add("  repair-proot   — corrigir Permission denied")
                lines.add("  clear          — limpar")
                lines.add("")
                lines.add("Depois: uname -a | ls / | ls /sdcard")
                lines.add("")
                return true
            }
            "clear" -> {
                lines.clear()
                return true
            }
            "status" -> {
                scope.launch {
                    linuxManager.refreshRootFsStatus()
                    val s = linuxManager.rootFsStatus.value
                    val rt = linuxManager.getRuntime()
                    lines.add(statusMessage)
                    lines.add("enabled : ${LinuxConfig.enabled}")
                    lines.add("ready   : $isReady")
                    if (s != null) {
                        lines.add("rootfs  : ${s.rootfsPath}")
                        lines.add("installed: ${s.isInstalled} (${s.distro ?: "—"})")
                        lines.add("size    : ${LinuxRootFs.formatSize(s.estimatedSizeBytes)}")
                    }
                    lines.add("proot   : ${if (rt.isProotInstalled()) "ok" else "no"}")
                    lines.add("storage : ${if (rt.isStorageReady()) "ok" else "no"}")
                    lines.add("proot path: ${rt.resolveProot()?.absolutePath ?: "—"}")
                    lines.add("")
                    scrollToBottom()
                }
                return true
            }
            "install" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return true
                }
                if (rootFsStatus?.isInstalled == true) {
                    lines.add("RootFS já instalado.")
                    lines.add("")
                    return true
                }
                busy = true
                scope.launch {
                    val r = linuxManager.installRootFs()
                    busy = false
                    lines.add(
                        if (r.isSuccess) "✓ install OK — agora: setup-runtime"
                        else "✗ ${r.exceptionOrNull()?.message}",
                    )
                    lines.add("")
                    scrollToBottom()
                }
                return true
            }
            "setup-runtime" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return true
                }
                busy = true
                scope.launch {
                    val r = linuxManager.setupRuntime()
                    busy = false
                    if (r.isSuccess) {
                        lines.add("✓ PRoot OK")
                        session = linuxManager.startSession()
                        lines.add("Sessão Linux ativa.")
                    } else {
                        lines.add("✗ ${r.exceptionOrNull()?.message}")
                    }
                    lines.add("")
                    scrollToBottom()
                }
                return true
            }
            "repair-proot" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return true
                }
                busy = true
                scope.launch {
                    val r = linuxManager.repairProot()
                    busy = false
                    lines.add(
                        if (r.isSuccess) {
                            "✓ repair-proot OK — tenta: uname -a"
                        } else {
                            "✗ ${r.exceptionOrNull()?.message}"
                        },
                    )
                    lines.add("")
                    scrollToBottom()
                }
                return true
            }
            "setup-storage" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return true
                }
                busy = true
                scope.launch {
                    val r = linuxManager.setupStorage()
                    busy = false
                    lines.add(
                        if (r.isSuccess) {
                            "✓ storage OK — depois do proot: ls /sdcard"
                        } else {
                            "✗ ${r.exceptionOrNull()?.message}"
                        },
                    )
                    lines.add("")
                    scrollToBottom()
                }
                return true
            }
        }
        return false
    }

    fun run(cmd: String) {
        lines.add("root@minios-linux:~# $cmd")
        if (cmd.isBlank()) {
            lines.add("")
            scrollToBottom()
            return
        }
        if (runBuiltin(cmd)) {
            scrollToBottom()
            return
        }

        val s = session ?: linuxManager.startSession().also { session = it }
        busy = true
        scope.launch {
            val out = s.execute(cmd)
            busy = false
            if (out.isNotBlank()) {
                out.lines().forEach { lines.add(it) }
            }
            lines.add("")
            scrollToBottom()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF010409))
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Linux",
                color = Color(0xFF3FB950),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = when {
                    busy -> "busy"
                    isReady -> "ready"
                    rootFsStatus?.isInstalled == true -> "rootfs ok"
                    else -> "setup needed"
                },
                color = when {
                    busy -> Color(0xFFD29922)
                    isReady -> Color(0xFF3FB950)
                    else -> Color(0xFF8B949E)
                },
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
        ) {
            items(lines) { line ->
                Text(
                    text = line,
                    color = Color(0xFF3FB950),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            }
        }

        TextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = Color(0xFFC9D1D9),
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0D1117),
                unfocusedContainerColor = Color(0xFF0D1117),
                focusedIndicatorColor = Color(0xFF238636),
                unfocusedIndicatorColor = Color(0xFF30363D),
                disabledContainerColor = Color(0xFF0D1117),
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (busy) return@KeyboardActions
                    val cmd = input.trim()
                    input = ""
                    run(cmd)
                },
            ),
            placeholder = {
                Text(
                    text = "root@minios-linux:~#",
                    color = Color(0xFF484F58),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            },
        )
    }
}
