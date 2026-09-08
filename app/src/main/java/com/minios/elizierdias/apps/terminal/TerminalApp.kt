/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
            "MiniOS Linux Terminal",
            "Debian ARM64 via PRoot (sem root)",
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
        return "root@minios-linux:$short#"
    }

    fun scrollToBottom() {
        scope.launch {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    fun copyAll() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("MiniOS Terminal", fullText))
        Toast.makeText(context, "Terminal copiado (${lines.size} linhas)", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        linuxManager.initialize()
        val status = linuxManager.getRootFs().status()
        val rt = linuxManager.getRuntime()
        val publicMode = LinuxConfig.isUsingPublicStorage(context)
        lines.add("── estado ──")
        lines.add(statusMessage)
        lines.add("rootfs : ${status.rootfsPath}")
        lines.add(
            if (publicMode) "storage: PUBLIC /sdcard/MiniOS (persiste ao desinstalar)"
            else "storage: PRIVATE (apagado ao desinstalar) — dá permissão de ficheiros",
        )
        lines.add(
            "installed: ${status.isInstalled} " +
                "(${status.distro ?: "—"}) " +
                LinuxRootFs.formatSize(status.estimatedSizeBytes),
        )
        lines.add("proot  : ${if (rt.isProotInstalled()) "ok (jniLibs)" else "MISSING — reinstall APK"}")
        lines.add("mounts : ${if (rt.isStorageReady()) "ok" else "corre setup-storage"}")
        lines.add("")
        if (!status.isInstalled) {
            lines.add("1) install")
            lines.add("2) setup-runtime")
            lines.add("3) setup-storage")
            lines.add("4) setup-dns")
        } else if (!rt.isProotInstalled()) {
            lines.add("libproot.so em falta — reinstala o APK")
        } else {
            lines.add("Linux pronto. uname -a | apt update | ls /sdcard/MiniOS")
            lines.add("Segura texto ou toca Copiar")
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
            "repair-dpkg", "repairdpkg", "fix-dpkg", "dpkg-configure" ->
                "repair-dpkg"
            else -> cmd.trim()
        }
    }

    fun runBuiltin(raw: String): Boolean {
        val cmd = normalizeBuiltin(raw)
        when (cmd) {
            "help" -> {
                lines.add("Setup:")
                lines.add("  install | reinstall-rootfs | setup-runtime | setup-storage")
                lines.add("  setup-dns | repair-dpkg | repair-proot | status | clear")
                lines.add("  pkg-install <nome> | pkg-update")
                lines.add("Pastas: /sdcard/MiniOS  /sdcard/Download  /minios")
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
                    lines.add("ready   : $isReady")
                    lines.add("cwd     : ${session?.cwd ?: promptCwd}")
                    if (s != null) {
                        lines.add("rootfs  : ${s.rootfsPath}")
                        lines.add("installed: ${s.isInstalled}")
                    }
                    lines.add(
                        if (LinuxConfig.isUsingPublicStorage(context))
                            "storage : PUBLIC /sdcard/MiniOS"
                        else
                            "storage : PRIVATE (wiped on uninstall)",
                    )
                    rt.diagnostic().lines().forEach { lines.add(it) }
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
                    lines.add("RootFS já instalado. Para regenerar: reinstall-rootfs")
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
            "reinstall-rootfs" -> {
                if (busy) {
                    lines.add("ocupado...")
                    return true
                }
                busy = true
                session = null
                scope.launch {
                    lines.add("A apagar RootFS antigo e reinstalar...")
                    scrollToBottom()
                    val r = linuxManager.reinstallRootFs()
                    busy = false
                    lines.add(
                        if (r.isSuccess) "✓ reinstall-rootfs OK — agora: setup-runtime"
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
                        lines.add("✓ PRoot OK + DNS")
                        session = linuxManager.startSession()
                        promptCwd = session?.cwd ?: "/root"
                        lines.add("Sessão Linux ativa.")
                    } else {
                        lines.add("✗ ${r.exceptionOrNull()?.message}")
                    }
                    lines.add("")
                    scrollToBottom()
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
                    val r = linuxManager.setupDns()
                    busy = false
                    if (r.isSuccess) {
                        r.getOrNull()?.lines()?.forEach { lines.add(it) }
                        lines.add("✓ setup-dns — tenta: apt update")
                    } else {
                        lines.add("✗ ${r.exceptionOrNull()?.message}")
                    }
                    lines.add("")
                    scrollToBottom()
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
                    lines.add("[DPKG] a reparar...")
                    scrollToBottom()
                    val r = linuxManager.repairDpkg()
                    busy = false
                    if (r.isSuccess) {
                        r.getOrNull()?.lines()?.forEach { lines.add(it) }
                        lines.add("✓ repair-dpkg OK")
                    } else {
                        lines.add("✗ ${r.exceptionOrNull()?.message}")
                    }
                    lines.add("")
                    scrollToBottom()
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
                    val r = linuxManager.getPackageManager().update { msg ->
                        lines.add(msg)
                        scrollToBottom()
                    }
                    busy = false
                    lines.add(r.message)
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
                        if (r.isSuccess) "✓ repair OK"
                        else "✗ ${r.exceptionOrNull()?.message}",
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
                        if (r.isSuccess) "✓ storage OK — ls /sdcard/MiniOS"
                        else "✗ ${r.exceptionOrNull()?.message}",
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
        lines.add("${prompt()} $cmd")
        if (cmd.isBlank()) {
            lines.add("")
            scrollToBottom()
            return
        }
        val lower = cmd.lowercase()
        if (lower.startsWith("pkg-install ") || lower == "pkg-install") {
            val pkg = cmd.removePrefix("pkg-install").removePrefix("PKG-INSTALL").trim()
            if (pkg.isEmpty()) {
                lines.add("[INSTALL] uso: pkg-install <pacote>")
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
                val r = linuxManager.getPackageManager().install(pkg) { msg ->
                    lines.add(msg)
                    scrollToBottom()
                }
                busy = false
                lines.add(r.message)
                lines.add("")
                scrollToBottom()
            }
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
            promptCwd = s.cwd
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
                text = "Copiar",
                color = Color(0xFF58A6FF),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable { copyAll() }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
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

        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Text(
                text = fullText,
                color = Color(0xFF3FB950),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
            )
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
                    text = prompt(),
                    color = Color(0xFF484F58),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            },
        )
    }
}
