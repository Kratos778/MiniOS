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
    val lines = remember { mutableStateListOf<String>() }
    var session by remember { mutableStateOf<LinuxSession?>(null) }
    var promptCwd by remember { mutableStateOf("/root") }

    fun prompt(): String =
        when {
            busy -> "[busy] "
            isReady -> "root@noskos:$promptCwd# "
            else -> "noskos$ "
        }

    fun scrollToBottom() {
        scope.launch {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    fun copyAll() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("terminal", lines.joinToString("\n")))
        Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        linuxManager.initialize()
        if (linuxManager.isReady.value) {
            session = linuxManager.startSession()
            promptCwd = session?.cwd ?: "/root"
        }
        lines.clear()
        lines.add("NoskOS Terminal")
        lines.add("status: $statusMessage")
        lines.add("rootfs: $rootFsStatus")
        lines.add("ready: $isReady")
        lines.add("")
        lines.add("Comandos especiais:")
        lines.add("  full-setup / setup-all / wizard — instalar tudo")
        lines.add("  setup-runtime | setup-storage | setup-dns")
        lines.add("  install | reinstall | status | diag")
        lines.add("  help")
        lines.add("")
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
        if (trimmed.isEmpty() || busy) return
        lines.add(prompt() + trimmed)
        busy = true
        scope.launch(Dispatchers.IO) {
            try {
                when (trimmed.lowercase()) {
                    "help" -> {
                        lines.add("Comandos:")
                        lines.add("  full-setup / setup-all / wizard")
                        lines.add("  setup-runtime | setup-storage | setup-dns")
                        lines.add("  install | reinstall | status | diag")
                        lines.add("  clear")
                        lines.add("  (qualquer outro = shell no Debian se ready)")
                    }
                    "clear" -> {
                        lines.clear()
                        lines.add("NoskOS Terminal")
                    }
                    "status" -> {
                        linuxManager.initialize()
                        lines.add("status: ${linuxManager.statusMessage.value}")
                        lines.add("rootfs: ${linuxManager.rootFsStatus.value}")
                        lines.add("ready: ${linuxManager.isReady.value}")
                    }
                    "diag" -> {
                        val rt = linuxManager.getRuntime()
                        lines.add("diag: proot=${rt.isProotInstalled()} rootfs=${rt.isRootFsReady()}")
                    }
                    "full-setup", "setup-all", "wizard" -> {
                        lines.add("A executar full-setup...")
                        val r = linuxManager.fullSetup()
                        if (r.isSuccess) {
                            lines.add("full-setup OK — Linux pronto")
                            session = linuxManager.startSession()
                            promptCwd = session?.cwd ?: "/root"
                        } else {
                            lines.add("full-setup FALHOU: ${r.exceptionOrNull()?.message}")
                        }
                    }
                    "setup-storage" -> {
                        val r = linuxManager.setupStorage()
                        lines.add(if (r.isSuccess) "setup-storage OK" else "FALHOU: ${r.exceptionOrNull()?.message}")
                    }
                    "setup-runtime" -> {
                        val r = linuxManager.setupRuntime()
                        lines.add(if (r.isSuccess) "setup-runtime OK" else "FALHOU: ${r.exceptionOrNull()?.message}")
                    }
                    "setup-dns" -> {
                        val r = linuxManager.setupDns()
                        lines.add(if (r.isSuccess) "setup-dns OK: ${r.getOrNull()}" else "FALHOU: ${r.exceptionOrNull()?.message}")
                    }
                    "install" -> {
                        val r = linuxManager.installRootFs()
                        lines.add(if (r.isSuccess) "install OK" else "FALHOU: ${r.exceptionOrNull()?.message}")
                    }
                    "reinstall" -> {
                        val r = linuxManager.reinstallRootFs()
                        lines.add(if (r.isSuccess) "reinstall OK" else "FALHOU: ${r.exceptionOrNull()?.message}")
                        if (r.isSuccess) {
                            session = null
                        }
                    }
                    else -> {
                        // Shell via session
                        if (!linuxManager.isReady.value) {
                            lines.add("RootFS nao pronto. Corre: full-setup")
                        } else {
                            val s = session ?: linuxManager.startSession().also { session = it }
                            s.execute(trimmed) { line ->
                                lines.add(line)
                            }
                            promptCwd = s.cwd
                        }
                    }
                }
            } catch (e: Exception) {
                lines.add("EXCECAO: ${e.message}")
            } finally {
                lines.add("")
                busy = false
                scrollToBottom()
            }
        }
    }

    val fullText = lines.joinToString("\n")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(8.dp),
    ) {
        // weight no filho directo do Column — senão o output empurra a barra para fora
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
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
            )
        }
        // Barra de input FIXA em baixo
        Spacer(modifier = Modifier.padding(vertical = 4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
