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
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Dispatchers
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
            "Multi-linha OK — cola script e toca Run",
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
        return "root@noskos-linux:$short#"
    }

    fun scrollToBottom() {
        scope.launch {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    fun copyAll() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("terminal", fullText))
        Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        linuxManager.initialize()
        if (linuxManager.isReady.value) {
            session = linuxManager.startSession()
            promptCwd = session?.cwd ?: "/root"
        }
    }

    LaunchedEffect(installProgress) {
        val msg = installProgress ?: return@LaunchedEffect
        if (msg.isNotBlank()) {
            lines.add(msg)
            scrollToBottom()
        }
    }

    fun run(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return

        lines.add("${prompt()} $trimmed")
        scrollToBottom()

        val lower = trimmed.lowercase()

        // Built-ins handled before shell
        when (lower) {
            "clear", "cls" -> {
                lines.clear()
                lines.add("NoskOS Linux Terminal")
                return
            }
            "help", "?" -> {
                lines.add("Comandos NoskOS:")
                lines.add("  full-setup / setup-all / wizard — instalar tudo")
                lines.add("  setup-runtime | setup-storage | setup-dns")
                lines.add("  install | reinstall | status | diag")
                lines.add("  clear — limpar ecrã")
                lines.add("  qualquer comando Debian (ls, apt, python3...)")
                lines.add("")
                scrollToBottom()
                return
            }
            "status" -> {
                lines.add(statusMessage)
                lines.add("rootFs: $rootFsStatus")
                lines.add("ready: $isReady")
                lines.add("busy: $busy")
                lines.add("")
                scrollToBottom()
                return
            }
            "diag", "diagnostic" -> {
                scope.launch {
                    val rt = linuxManager.getRuntime()
                    rt.diagnostic().lines().forEach { lines.add(it) }
                    lines.add("")
                    scrollToBottom()
                }
                return
            }
            "full-setup", "setup-all", "wizard" -> {
                if (busy) {
                    lines.add("ocupado...")
                    scrollToBottom()
                    return
                }
                busy = true
                session = null
                scope.launch {
                    try {
                        lines.add("A executar full-setup...")
                        scrollToBottom()
                        val r = linuxManager.fullSetup()
                        if (r.isSuccess) {
                            lines.add("full-setup OK — Linux pronto")
                            session = linuxManager.startSession()
                            promptCwd = session?.cwd ?: "/root"
                        } else {
                            lines.add("full-setup FALHOU: ${r.exceptionOrNull()?.message}")
                        }
                        lines.add("")
                        scrollToBottom()
                    } catch (e: Exception) {
                        lines.add("error: ${e.message}")
                    } finally {
                        busy = false
                    }
                }
                return
            }
            "setup-storage" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return
                }
                busy = true
                scope.launch {
                    try {
                        val r = linuxManager.setupStorage()
                        lines.add(if (r.isSuccess) "setup-storage OK" else "FALHOU: ${r.exceptionOrNull()?.message}")
                        lines.add("")
                        scrollToBottom()
                    } catch (e: Exception) {
                        lines.add("error: ${e.message}")
                    } finally {
                        busy = false
                    }
                }
                return
            }
            "setup-runtime" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return
                }
                busy = true
                scope.launch {
                    try {
                        val r = linuxManager.setupRuntime()
                        lines.add(if (r.isSuccess) "setup-runtime OK" else "FALHOU: ${r.exceptionOrNull()?.message}")
                        lines.add("")
                        scrollToBottom()
                    } catch (e: Exception) {
                        lines.add("error: ${e.message}")
                    } finally {
                        busy = false
                    }
                }
                return
            }
            "setup-dns" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return
                }
                busy = true
                scope.launch {
                    try {
                        val r = linuxManager.setupDns()
                        lines.add(if (r.isSuccess) "setup-dns OK: ${r.getOrNull()}" else "FALHOU: ${r.exceptionOrNull()?.message}")
                        lines.add("")
                        scrollToBottom()
                    } catch (e: Exception) {
                        lines.add("error: ${e.message}")
                    } finally {
                        busy = false
                    }
                }
                return
            }
            "install" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return
                }
                busy = true
                scope.launch {
                    try {
                        val r = linuxManager.installRootFs()
                        lines.add(if (r.isSuccess) "install OK" else "FALHOU: ${r.exceptionOrNull()?.message}")
                        lines.add("")
                        scrollToBottom()
                    } catch (e: Exception) {
                        lines.add("error: ${e.message}")
                    } finally {
                        busy = false
                    }
                }
                return
            }
        }

        // Shell via session
        if (busy) {
            lines.add("ocupado...")
            scrollToBottom()
            return
        }
        val s = session ?: linuxManager.startSession().also { session = it }
        busy = true
        scope.launch {
            try {
                s.execute(trimmed) { line ->
                    scope.launch(Dispatchers.Main) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(8.dp),
    ) {
        SelectionContainer {
            Text(
                text = fullText,
                color = Color(0xFF3FB950),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
            )
        }
        Spacer(modifier = Modifier.padding(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (busy) "[busy]" else prompt(),
                color = if (busy) Color(0xFFD29922) else Color(0xFF3FB950),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 4.dp),
            )
            TextField(
                value = input,
                onValueChange = { input = it },
                enabled = !busy,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                textStyle = TextStyle(
                    color = Color(0xFFE6EDF3),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF161B22),
                    unfocusedContainerColor = Color(0xFF161B22),
                    disabledContainerColor = Color(0xFF0D1117),
                    focusedTextColor = Color(0xFFE6EDF3),
                    unfocusedTextColor = Color(0xFFE6EDF3),
                    disabledTextColor = Color(0xFF484F58),
                    cursorColor = Color(0xFF3FB950),
                    focusedIndicatorColor = Color(0xFF3FB950),
                    unfocusedIndicatorColor = Color(0xFF30363D),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine = true,
                placeholder = {
                    Text(
                        if (busy) "a processar..." else "comando → Run",
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
