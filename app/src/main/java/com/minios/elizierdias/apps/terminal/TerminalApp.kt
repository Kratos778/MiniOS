/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.apps.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minios.elizierdias.linux.LinuxManager
import com.minios.elizierdias.linux.LinuxSession
import kotlinx.coroutines.launch

@Composable
fun TerminalApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val linuxManager = remember(context) { LinuxManager(context) }
    val statusMessage by linuxManager.statusMessage.collectAsState()
    val rootFsStatus by linuxManager.rootFsStatus.collectAsState()
    val isReady by linuxManager.isReady.collectAsState()
    val installProgress by linuxManager.installProgress.collectAsState()

    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<LinuxSession?>(null) }
    var promptCwd by remember { mutableStateOf("/root") }

    val lines = remember {
        mutableStateListOf(
            "NoskOS Linux Terminal",
            "Debian ARM64 via PRoot (sem root)",
            "Escreve um comando e toca Run (ou Enter)",
            "",
        )
    }

    val fullText = remember(lines.size, lines.lastOrNull()) {
        lines.joinToString("\n")
    }

    fun prompt(): String {
        val short = when {
            promptCwd == "/root" || promptCwd == "~" -> "~"
            promptCwd.startsWith("/root/") -> "~" + promptCwd.removePrefix("/root")
            else -> promptCwd
        }
        return "root@noskos:$short# "
    }

    fun scrollToBottom() {
        scope.launch {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    fun copyAll() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("terminal", fullText))
        lines.add("[copiado para o clipboard]")
        scrollToBottom()
    }

    fun append(msg: String) {
        lines.add(msg)
        scrollToBottom()
    }

    LaunchedEffect(installProgress) {
        val msg = installProgress ?: return@LaunchedEffect
        if (msg.isNotBlank()) {
            lines.add(msg)
            scrollToBottom()
        }
    }

    fun runBuiltin(cmd: String): Boolean {
        val lower = cmd.trim().lowercase()
        when {
            lower == "clear" || lower == "cls" -> {
                lines.clear()
                return true
            }
            lower == "help" || lower == "?" -> {
                append("Comandos NoskOS:")
                append("  full-setup | setup-all | wizard  — instalar Debian + runtime")
                append("  setup-storage                   — montar /sdcard no Debian")
                append("  status                          — estado do Linux")
                append("  clear                           — limpar ecrã")
                append("  qualquer comando shell Debian (ls, apt, python3...)")
                append("")
                return true
            }
            lower == "status" -> {
                append(statusMessage)
                append("rootFs: $rootFsStatus")
                append("ready: $isReady")
                append("")
                return true
            }
            lower == "full-setup" || lower == "setup-all" || lower == "wizard" -> {
                if (busy) {
                    append("ocupado...")
                    return true
                }
                busy = true
                scope.launch {
                    try {
                        append("=== full-setup ===")
                        val r = linuxManager.fullSetup()
                        if (r.isSuccess) {
                            append("=== full-setup CONCLUIDO ===")
                            session = linuxManager.startSession()
                        } else {
                            append("error: ${r.exceptionOrNull()?.message}")
                        }
                        append("")
                    } catch (e: Exception) {
                        append("error: ${e.message}")
                    } finally {
                        busy = false
                    }
                }
                return true
            }
            lower == "setup-storage" -> {
                if (busy) {
                    append("ocupado...")
                    return true
                }
                busy = true
                scope.launch {
                    try {
                        val r = linuxManager.setupStorage()
                        if (r.isSuccess) append("setup-storage OK")
                        else append("error: ${r.exceptionOrNull()?.message}")
                        append("")
                    } catch (e: Exception) {
                        append("error: ${e.message}")
                    } finally {
                        busy = false
                    }
                }
                return true
            }
        }
        return false
    }

    fun run(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return
        append(prompt() + trimmed)
        if (runBuiltin(trimmed)) return

        val s = session ?: linuxManager.startSession().also { session = it }
        if (busy) {
            lines.add("ocupado...")
            scrollToBottom()
            return
        }
        busy = true
        scope.launch {
            try {
                s.execute(trimmed) { line ->
                    scope.launch {
                        lines.add(line)
                        scrollToBottom()
                    }
                }
            } catch (e: Exception) {
                lines.add("error: ${e.message}")
            } finally {
                busy = false
                promptCwd = s.cwd
                lines.add("")
                scrollToBottom()
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(8.dp),
    ) {
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Text(
                text = fullText,
                color = Color(0xFF3FB950),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextField(
                value = input,
                onValueChange = { if (!busy) input = it },
                enabled = true,
                readOnly = busy,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = Color(0xFFE6EDF3),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF161B22),
                    unfocusedContainerColor = Color(0xFF161B22),
                    disabledContainerColor = Color(0xFF161B22),
                    focusedTextColor = Color(0xFFE6EDF3),
                    unfocusedTextColor = Color(0xFFE6EDF3),
                    disabledTextColor = Color(0xFF8B949E),
                    cursorColor = Color(0xFF3FB950),
                    focusedIndicatorColor = Color(0xFF3FB950),
                    unfocusedIndicatorColor = Color(0xFF30363D),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        if (!busy && input.isNotBlank()) {
                            val cmd = input
                            input = ""
                            run(cmd)
                        }
                    },
                ),
                singleLine = true,
                placeholder = {
                    Text(
                        "escreve aqui e toca Run (ou Enter)",
                        color = Color(0xFF484F58),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                },
            )
            Text(
                text = if (busy) "..." else "Run",
                color = if (busy) Color(0xFF484F58) else Color(0xFF58A6FF),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .clickable(enabled = !busy) {
                        val cmd = input
                        if (cmd.isBlank()) return@clickable
                        input = ""
                        run(cmd)
                    },
            )
            Text(
                text = "Copy",
                color = Color(0xFFD29922),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clickable { copyAll() },
            )
        }
    }
}
