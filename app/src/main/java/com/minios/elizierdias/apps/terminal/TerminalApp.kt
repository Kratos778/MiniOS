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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minios.elizierdias.linux.LinuxConfig
import com.minios.elizierdias.linux.LinuxManager
import com.minios.elizierdias.linux.LinuxRootFs
import com.minios.elizierdias.linux.LinuxSession
import kotlinx.coroutines.launch

enum class TerminalMode {
    MINIOS,
    LINUX,
}

@Composable
fun TerminalApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val linuxManager = remember(context) { LinuxManager(context) }
    val statusMessage by linuxManager.statusMessage.collectAsState()
    val rootFsStatus by linuxManager.rootFsStatus.collectAsState()
    val isReady by linuxManager.isReady.collectAsState()

    var mode by remember { mutableStateOf(TerminalMode.LINUX) }
    var input by remember { mutableStateOf("") }
    var linuxSession by remember { mutableStateOf<LinuxSession?>(null) }

    val lines = remember {
        mutableStateListOf(
            "MiniOS Terminal — dual mode",
            "Toque em [Linux] ou [MiniOS] para alternar.",
            "",
        )
    }

    // Ao abrir o Terminal pelo ícone do Desktop: prepara o Linux subsystem
    LaunchedEffect(Unit) {
        linuxManager.initialize()
        val status = linuxManager.getRootFs().status()
        lines.add("── Linux subsystem ──")
        lines.add(statusMessage)
        lines.add("RootFS path: ${status.rootfsPath}")
        lines.add(
            if (status.isInstalled) {
                "RootFS: instalado (${status.distro ?: "?"}) · ${LinuxRootFs.formatSize(status.estimatedSizeBytes)}"
            } else {
                "RootFS: ainda não instalado (diretórios prontos)"
            },
        )
        lines.add("Runtime nativo: não ligado ainda")
        lines.add("")
        lines.add("Modo atual: Linux Shell (preparado)")
        lines.add("Comandos úteis: help, status, clear, uname, whoami")
        lines.add("")
        listState.animateScrollToItem((lines.size - 1).coerceAtLeast(0))
    }

    // Observa output da sessão Linux quando existir
    val sessionOutput = linuxSession?.output?.collectAsState()?.value
    LaunchedEffect(sessionOutput) {
        sessionOutput?.lastOrNull()?.let { last ->
            if (lines.lastOrNull() != last) {
                // já é adicionado no execute; só faz scroll
                listState.animateScrollToItem((lines.size - 1).coerceAtLeast(0))
            }
        }
    }

    fun scrollToBottom() {
        scope.launch {
            listState.animateScrollToItem((lines.size - 1).coerceAtLeast(0))
        }
    }

    fun switchMode(newMode: TerminalMode) {
        mode = newMode
        lines.add("")
        lines.add(
            if (newMode == TerminalMode.LINUX) {
                "── Mudou para Linux Shell ──"
            } else {
                "── Mudou para MiniOS Shell ──"
            },
        )
        lines.add("")
        scrollToBottom()
    }

    fun runMiniOs(cmd: String) {
        lines.add("guest@minios:~$ $cmd")
        val out = when {
            cmd.isBlank() -> ""
            cmd == "help" -> "help | echo <t> | whoami | uname | clear | status | linux"
            cmd == "whoami" -> "guest"
            cmd == "uname" -> "MiniOS Shell 0.1.0"
            cmd == "status" -> statusMessage
            cmd == "linux" -> {
                switchMode(TerminalMode.LINUX)
                ""
            }
            cmd == "clear" -> {
                lines.clear()
                ""
            }
            cmd.startsWith("echo ") -> cmd.removePrefix("echo ")
            else -> "comando nao reconhecido: $cmd (modo MiniOS)"
        }
        if (out.isNotEmpty()) lines.add(out)
        lines.add("")
        scrollToBottom()
    }

    fun runLinux(cmd: String) {
        val prompt = "root@minios-linux:~# "
        lines.add("$prompt$cmd")

        when {
            cmd.isBlank() -> {
                lines.add("")
            }
            cmd == "help" -> {
                lines.add("Linux Shell (integrado ao MiniOS)")
                lines.add("  help     — esta ajuda")
                lines.add("  status   — estado do RootFS / runtime")
                lines.add("  uname    — info do sistema (quando runtime ligado)")
                lines.add("  whoami   — utilizador")
                lines.add("  clear    — limpar ecrã")
                lines.add("  minios   — voltar ao MiniOS Shell")
                lines.add("")
                lines.add("Nota: runtime nativo ainda não ligado.")
                lines.add("Os comandos reais (/bin/bash, apt, python3…) chegarão")
                lines.add("quando o RootFS Debian ARM64 + PRoot estiverem ativos.")
                lines.add("")
            }
            cmd == "status" -> {
                val s = rootFsStatus
                lines.add(statusMessage)
                lines.add("enabled : ${LinuxConfig.enabled}")
                lines.add("ready   : $isReady")
                if (s != null) {
                    lines.add("rootfs  : ${s.rootfsPath}")
                    lines.add("installed: ${s.isInstalled} (${s.distro ?: "—"})")
                    lines.add("size    : ${LinuxRootFs.formatSize(s.estimatedSizeBytes)}")
                }
                lines.add("")
            }
            cmd == "whoami" -> {
                lines.add("root")
                lines.add("")
            }
            cmd == "uname" || cmd == "uname -a" -> {
                lines.add("Linux MiniOS 0.1.0 aarch64 (runtime pending)")
                lines.add("")
            }
            cmd == "clear" -> {
                lines.clear()
            }
            cmd == "minios" -> {
                switchMode(TerminalMode.MINIOS)
            }
            else -> {
                // Tenta sessão real se existir; senão mensagem clara
                val session = linuxSession ?: linuxManager.startSession()
                if (session != null) {
                    linuxSession = session
                    session.execute(cmd)
                    // session.output é observado; também ecoamos aqui para feedback imediato
                    lines.add("[Linux runtime not connected yet – command ignored]")
                    lines.add("")
                } else {
                    lines.add("bash: $cmd: runtime Linux ainda não está ligado")
                    lines.add("Use 'status' para ver o estado do RootFS.")
                    lines.add("")
                }
            }
        }
        scrollToBottom()
    }

    fun run(cmd: String) {
        when (mode) {
            TerminalMode.MINIOS -> runMiniOs(cmd)
            TerminalMode.LINUX -> runLinux(cmd)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF010409))
            .padding(10.dp),
    ) {
        // Barra de modo (Linux / MiniOS)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeChip(
                label = "Linux",
                selected = mode == TerminalMode.LINUX,
                onClick = { switchMode(TerminalMode.LINUX) },
            )
            ModeChip(
                label = "MiniOS",
                selected = mode == TerminalMode.MINIOS,
                onClick = { switchMode(TerminalMode.MINIOS) },
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (isReady) "runtime: on" else "runtime: off",
                color = if (isReady) Color(0xFF3FB950) else Color(0xFF8B949E),
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
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    val cmd = input.trim()
                    input = ""
                    run(cmd)
                },
            ),
            placeholder = {
                Text(
                    text = if (mode == TerminalMode.LINUX) "root@minios-linux:~#" else "guest@minios:~$",
                    color = Color(0xFF484F58),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            },
        )
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) Color(0xFF238636) else Color(0xFF21262D)
    val fg = if (selected) Color.White else Color(0xFFC9D1D9)
    Text(
        text = label,
        color = fg,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
