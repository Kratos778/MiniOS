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
        cm.setPrimaryClip(ClipData.newPlainText("NoskOS Terminal", fullText))
        Toast.makeText(context, "Terminal copiado (${lines.size} linhas)", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        linuxManager.initialize()
        val status = linuxManager.getRootFs().status()
        val rt = linuxManager.getRuntime()
        val publicDl = LinuxConfig.isUsingPublicDownloads(context)
        lines.add("── estado ──")
        lines.add(statusMessage)
        lines.add("rootfs : ${status.rootfsPath}")
        lines.add("RootFS: storage privado (precisa de symlinks)")
        lines.add(
            if (publicDl)
                "downloads: /sdcard/MiniOS/downloads (persiste)"
            else
                "downloads: privado",
        )
        lines.add("imports : /sdcard/MiniOS/imports")
        lines.add(
            "installed: ${status.isInstalled} " +
                "(${status.distro ?: "—"}) " +
                LinuxRootFs.formatSize(status.estimatedSizeBytes),
        )
        lines.add("proot  : ${if (rt.isProotInstalled()) "ok (jniLibs)" else "MISSING"}")
        lines.add("mounts : ${if (rt.isStorageReady()) "ok" else "corre setup-storage"}")
        lines.add("")
        if (!status.isInstalled) {
            lines.add("Para instalar tudo de uma vez:  full-setup")
            lines.add("(ou passo a passo: install → setup-runtime → setup-storage → setup-dns)")
        } else if (!rt.isProotInstalled()) {
            lines.add("libproot.so em falta — reinstala o APK")
        } else {
            lines.add("Linux pronto. uname -a | apt update | start-vnc")
            session = linuxManager.startSession()
            promptCwd = session?.cwd ?: "/root"
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

    LaunchedEffect(lines.size) {
        scrollToBottom()
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
            "reinstall-rootfs", "reinstall", "reinstall_rootfs", "wipe-install" ->
                "reinstall-rootfs"
            "setup-dns", "setupdns", "fix-dns", "dns" ->
                "setup-dns"
            "full-setup", "fullsetup", "setup-all", "setupall", "wizard" ->
                "full-setup"
            "repair-dpkg", "repairdpkg", "fix-dpkg", "dpkg-configure" ->
                "repair-dpkg"
            "start-vnc", "startvnc", "vnc-start" ->
                "start-vnc"
            else -> cmd.trim()
        }
    }

    fun runBuiltin(raw: String): Boolean {
        val cmd = normalizeBuiltin(raw)
        when (cmd) {
            "help" -> {
                lines.add("Setup: full-setup | install | reinstall-rootfs | setup-runtime")
                lines.add("  setup-storage | setup-dns | repair-dpkg | status | clear | pkg-install")
                lines.add("VNC: start-vnc")
                lines.add("Multi-linha: cola o script e toca Run")
                lines.add("Pastas: /sdcard/MiniOS  /sdcard/Download")
                lines.add("")
                return true
            }
            "clear" -> {
                lines.clear()
                return true
            }
            "start-vnc" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return true
                }
                busy = true
                val script =
                    """
                    export HOME=/root USER=root
                    mkdir -p /root/.vnc
                    vncserver -kill :1 2>/dev/null || true
                    pkill -9 Xtigervnc 2>/dev/null || true
                    rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 /root/.vnc/*.pid 2>/dev/null || true
                    printf '%s\n' '#!/bin/sh' 'unset SESSION_MANAGER' 'unset DBUS_SESSION_BUS_ADDRESS' 'export DISPLAY=:1 HOME=/root USER=root' 'xsetroot -solid #1a2332 2>/dev/null' 'openbox &' 'sleep 0.4' 'xterm -geometry 200x45+0+0 -fa Monospace -fs 12 -bg black -fg grey -ls -title NoskOS &' 'wait' > /root/.vnc/xstartup
                    chmod +x /root/.vnc/xstartup
                    vncserver :1 -geometry 1640x720 -depth 24 -localhost yes -SecurityTypes None -xstartup /root/.vnc/xstartup
                    echo "---"
                    vncserver -list
                    """.trimIndent()
                val s = session ?: linuxManager.startSession().also { session = it }
                scope.launch {
                    try {
                        lines.add("${prompt()} start-vnc")
                        s.execute(script) { line ->
                            scope.launch(Dispatchers.Main) {
                                lines.add(line)
                                scrollToBottom()
                            }
                        }
                    } catch (e: Exception) {
                        lines.add("error: ${e.message}")
                    } finally {
                        busy = false
                        lines.add("")
                        scrollToBottom()
                    }
                }
                return true
            }
            "status" -> {
                scope.launch {
                    linuxManager.refreshRootFsStatus()
                    val s = linuxManager.rootFsStatus.value
                    val rt = linuxManager.getRuntime()
                    lines.add(statusMessage)
                    lines.add("ready   : $isReady")
                    lines.add("cwd     : ${session?.cwd ?: promptCwd}")
                    if (s != null) {
                        lines.add("rootfs  : ${s.rootfsPath}")
                        lines.add("installed: ${s.isInstalled}")
                    }
                    lines.add(
                        "downloads: ${
                            if (LinuxConfig.isUsingPublicDownloads(context))
                                "/sdcard/MiniOS/downloads"
                            else
                                "private"
                        }",
                    )
                    rt.diagnostic().lines().forEach { lines.add(it) }
                    lines.add("")
                    scrollToBottom()
                }
                return true
            }
            "full-setup" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return true
                }
                busy = true
                session = null
                scope.launch {
                    try {
                        lines.add("A executar full-setup (RootFS + PRoot + DNS + Storage + Bootstrap)...")
                        scrollToBottom()
                        val r = linuxManager.fullSetup()
                        if (r.isSuccess) {
                            lines.add("full-setup OK — Linux pronto")
                            session = linuxManager.startSession()
                            promptCwd = session?.cwd ?: "/root"
                        } else {
                            lines.add("${r.exceptionOrNull()?.message}")
                        }
                    } finally {
                        busy = false
                        lines.add("")
                        scrollToBottom()
                    }
                }
                return true
            }
            "install" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return true
                }
                if (rootFsStatus?.isInstalled == true) {
                    lines.add("RootFS ja instalado. Para regenerar: reinstall-rootfs")
                    lines.add("")
                    return true
                }
                busy = true
                scope.launch {
                    try {
                        val r = linuxManager.installRootFs()
                        lines.add(
                            if (r.isSuccess) "install OK — agora: setup-runtime"
                            else "${r.exceptionOrNull()?.message}",
                        )
                    } finally {
                        busy = false
                        lines.add("")
                        scrollToBottom()
                    }
                }
                return true
            }
            "reinstall-rootfs" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return true
                }
                busy = true
                session = null
                scope.launch {
                    try {
                        lines.add("A apagar RootFS e reinstalar...")
                        scrollToBottom()
                        val r = linuxManager.reinstallRootFs()
                        lines.add(
                            if (r.isSuccess) "reinstall-rootfs OK — setup-runtime"
                            else "${r.exceptionOrNull()?.message}",
                        )
                    } finally {
                        busy = false
                        lines.add("")
                        scrollToBottom()
                    }
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
                    try {
                        val r = linuxManager.setupRuntime()
                        if (r.isSuccess) {
                            lines.add("PRoot OK")
                            session = linuxManager.startSession()
                            promptCwd = session?.cwd ?: "/root"
                        } else {
                            lines.add("${r.exceptionOrNull()?.message}")
                        }
                    } finally {
                        busy = false
                        lines.add("")
                        scrollToBottom()
                    }
                }
                return true
            }
            "setup-dns" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return true
                }
                busy = true
                scope.launch {
                    try {
                        val r = linuxManager.setupDns()
                        if (r.isSuccess) {
                            r.getOrNull()?.lines()?.forEach { lines.add(it) }
                            lines.add("setup-dns OK")
                        } else {
                            lines.add("${r.exceptionOrNull()?.message}")
                        }
                    } finally {
                        busy = false
                        lines.add("")
                        scrollToBottom()
                    }
                }
                return true
            }
            "repair-dpkg" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return true
                }
                busy = true
                scope.launch {
                    try {
                        val r = linuxManager.repairDpkg()
                        if (r.isSuccess) {
                            r.getOrNull()?.lines()?.forEach { lines.add(it) }
                            lines.add("repair-dpkg OK")
                        } else {
                            lines.add("${r.exceptionOrNull()?.message}")
                        }
                    } finally {
                        busy = false
                        lines.add("")
                        scrollToBottom()
                    }
                }
                return true
            }
            "pkg-update" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return true
                }
                busy = true
                scope.launch {
                    try {
                        val r = linuxManager.getPackageManager().update { msg ->
                            lines.add(msg)
                            scrollToBottom()
                        }
                        lines.add(r.message)
                    } finally {
                        busy = false
                        lines.add("")
                        scrollToBottom()
                    }
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
                    try {
                        val r = linuxManager.repairProot()
                        lines.add(
                            if (r.isSuccess) "repair OK"
                            else "${r.exceptionOrNull()?.message}",
                        )
                    } finally {
                        busy = false
                        lines.add("")
                        scrollToBottom()
                    }
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
                    try {
                        val r = linuxManager.setupStorage()
                        lines.add(
                            if (r.isSuccess) "storage OK — ls /sdcard/MiniOS"
                            else "${r.exceptionOrNull()?.message}",
                        )
                    } finally {
                        busy = false
                        lines.add("")
                        scrollToBottom()
                    }
                }
                return true
            }
        }
        return false
    }

    fun run(cmd: String) {
        val display = if (cmd.contains('\n')) {
            cmd.lineSequence().firstOrNull()?.trim().orEmpty() + " …(${cmd.lines().size} linhas)"
        } else {
            cmd
        }
        lines.add("${prompt()} $display")
        if (cmd.isBlank()) {
            lines.add("")
            scrollToBottom()
            return
        }
        if (!cmd.contains('\n')) {
            val lower = cmd.lowercase()
            if (lower.startsWith("pkg-install ") || lower == "pkg-install") {
                val pkg = cmd.removePrefix("pkg-install").removePrefix("PKG-INSTALL").trim()
                if (pkg.isEmpty()) {
                    lines.add("uso: pkg-install <pacote>")
                    lines.add("")
                    scrollToBottom()
                    return
                }
                if (busy) {
                    lines.add("ocupado...")
                    scrollToBottom()
                    return
                }
                busy = true
                scope.launch {
                    try {
                        val r = linuxManager.getPackageManager().install(pkg) { msg ->
                            lines.add(msg)
                            scrollToBottom()
                        }
                        lines.add(r.message)
                    } finally {
                        busy = false
                        lines.add("")
                        scrollToBottom()
                    }
                }
                return
            }
            if (runBuiltin(cmd)) {
                scrollToBottom()
                return
            }
        }

        val s = session ?: linuxManager.startSession().also { session = it }
        if (busy) {
            lines.add("ocupado...")
            scrollToBottom()
            return
        }
        busy = true
        scope.launch {
            try {
                s.execute(cmd) { line ->
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
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = Color(0xFFE6EDF3),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF161B22),
                    unfocusedContainerColor = Color(0xFF161B22),
                    focusedTextColor = Color(0xFFE6EDF3),
                    unfocusedTextColor = Color(0xFFE6EDF3),
                    cursorColor = Color(0xFF3FB950),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine = false,
                maxLines = 4,
                placeholder = {
                    Text(
                        "comando (ou full-setup) → Run",
                        color = Color(0xFF484F58),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                },
            )
            Text(
                text = "Run",
                color = Color(0xFF58A6FF),
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
