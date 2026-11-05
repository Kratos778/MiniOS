/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.apps.linuxgui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minios.elizierdias.linux.LinuxGuiRuntime
import com.minios.elizierdias.linux.LinuxManager
import com.minios.elizierdias.linux.vnc.RfbViewer
import kotlinx.coroutines.launch

@Composable
fun LinuxDesktopApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { LinuxManager(context) }
    val gui = remember(context) { LinuxGuiRuntime(context, manager.getRuntime()) }

    var running by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var geometry by remember { mutableStateOf(gui.deviceGeometry()) }
    var statusMsg by remember { mutableStateOf("A preparar Linux gráfico…") }
    var log by remember { mutableStateOf("") }
    var showControl by remember { mutableStateOf(false) }
    var autoDone by remember { mutableStateOf(false) }

    fun append(line: String) {
        log = (log + line + "\n").takeLast(5000)
    }

    fun runBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                append("[ERROR] ${e.message}")
                statusMsg = "Erro: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    /** Um único fluxo: Linux ready → pacotes VNC → iniciar sessão. */
    suspend fun autoSetup() {
        statusMsg = "A verificar Linux…"
        append("[AUTO] initialize LinuxManager")
        manager.initialize()

        if (!manager.getRuntime().isFullyReady()) {
            statusMsg = "Linux base em falta — a correr full-setup…"
            append("[AUTO] full-setup")
            val setup = manager.fullSetup()
            if (setup.isFailure || !manager.getRuntime().isFullyReady()) {
                val err = setup.exceptionOrNull()?.message ?: "full-setup falhou"
                statusMsg = "Falha no Linux: $err"
                append("[ERROR] $err")
                return
            }
            append("[AUTO] Linux ready")
        }

        geometry = gui.deviceGeometry()
        var st = gui.status()
        append(st.message)

        if (st.running && !st.message.contains("stale", ignoreCase = true)) {
            running = true
            geometry = st.geometry
            statusMsg = st.message
            append("[AUTO] VNC já activo — a reutilizar")
            return
        }

        statusMsg = "A instalar pacotes gráficos (VNC)…"
        append("[AUTO] ensureGuiPackages")
        val pkgs = gui.ensureGuiPackages { msg ->
            append(msg)
            statusMsg = msg.take(80)
        }
        if (pkgs.isFailure) {
            val err = pkgs.exceptionOrNull()?.message ?: "pacotes falharam"
            statusMsg = "Falha pacotes: $err"
            append("[ERROR] $err")
            return
        }
        append(pkgs.getOrNull() ?: "pacotes OK")

        statusMsg = "A iniciar VNC $geometry…"
        append("[AUTO] start VNC")
        gui.stop()
        val start = gui.start(geometry)
        if (start.isSuccess) {
            st = start.getOrNull()!!
            running = true
            geometry = st.geometry
            statusMsg = "Linux gráfico pronto"
            append(st.message)
        } else {
            running = false
            val err = start.exceptionOrNull()?.message ?: "start falhou"
            statusMsg = "VNC falhou: $err"
            append("[ERROR] $err")
        }
    }

    LaunchedEffect(Unit) {
        if (autoDone) return@LaunchedEffect
        autoDone = true
        busy = true
        try {
            autoSetup()
        } catch (e: Exception) {
            append("[ERROR] ${e.message}")
            statusMsg = "Erro: ${e.message}"
        } finally {
            busy = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        ThinBar(
            running = running,
            busy = busy,
            port = gui.vncPort,
            geometry = geometry,
            statusMsg = statusMsg,
            showControl = showControl,
            onToggleControl = { showControl = !showControl },
        )

        if (showControl) {
            ControlPanel(
                busy = busy,
                running = running,
                log = log,
                onInstall = {
                    runBusy {
                        append("[GUI] A instalar pacotes VNC...")
                        val r = gui.ensureGuiPackages { msg -> append(msg) }
                        if (r.isSuccess) append(r.getOrNull() ?: "OK")
                        else append("[ERROR] ${r.exceptionOrNull()?.message}")
                    }
                },
                onInstallBrowser = {
                    runBusy {
                        append("[GUI] A instalar Dillo/Links2...")
                        val r = gui.ensureLightBrowser { msg -> append(msg) }
                        if (r.isSuccess) append(r.getOrNull() ?: "OK")
                        else append("[ERROR] ${r.exceptionOrNull()?.message}")
                    }
                },
                onStart = {
                    runBusy {
                        append("[GUI] Iniciar VNC $geometry...")
                        gui.stop()
                        val r = gui.start(geometry)
                        if (r.isSuccess) {
                            val st = r.getOrNull()!!
                            running = true
                            geometry = st.geometry
                            statusMsg = "Linux gráfico pronto"
                            append(st.message)
                        } else {
                            running = false
                            append("[ERROR] ${r.exceptionOrNull()?.message}")
                        }
                    }
                },
                onStop = {
                    runBusy {
                        gui.stop()
                        running = false
                        statusMsg = "VNC parado"
                        append("VNC parado")
                    }
                },
                onRefresh = {
                    runBusy {
                        val st = gui.status()
                        running = st.running
                        geometry = st.geometry
                        statusMsg = st.message
                        append(st.message)
                    }
                },
                onAuto = {
                    runBusy { autoSetup() }
                },
                onLaunchTerm = {
                    runBusy {
                        val r = gui.runApp("xterm -geometry 160x40 -ls -title 'NoskOS Terminal'")
                        if (r.isSuccess) append(r.getOrNull() ?: "OK")
                        else append("[ERROR] ${r.exceptionOrNull()?.message}")
                    }
                },
                onLaunchBrowser = {
                    runBusy {
                        val r = gui.runApp(gui.lightBrowserCommand())
                        if (r.isSuccess) append(r.getOrNull() ?: "OK")
                        else append("[ERROR] ${r.exceptionOrNull()?.message}")
                    }
                },
            )
        }

        if (!running && !busy) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    "Linux gráfico parado ou falhou a arrancar.",
                    color = Color(0xFFC9D1D9),
                    fontSize = 13.sp,
                )
                Text(
                    statusMsg,
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Toca em ⚙ Controlo → Auto (ou Iniciar).",
                    color = Color(0xFF58A6FF),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clickable { showControl = true },
                )
            }
        }

        RfbViewer(
            active = running,
            host = "127.0.0.1",
            port = gui.vncPort,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun ThinBar(
    running: Boolean,
    busy: Boolean,
    port: Int,
    geometry: String,
    statusMsg: String,
    showControl: Boolean,
    onToggleControl: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color(0xFF161B22))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = when {
                    busy -> "…"
                    running -> "●"
                    else -> "○"
                },
                color = when {
                    busy -> Color(0xFFD29922)
                    running -> Color(0xFF3FB950)
                    else -> Color(0xFF8B949E)
                },
                fontSize = 12.sp,
            )
            Text(
                text = " $port  $geometry  ${statusMsg.take(40)}",
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            text = if (showControl) "▾ Controlo" else "⚙ Controlo",
            color = Color(0xFF58A6FF),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable(onClick = onToggleControl),
        )
    }
}

@Composable
private fun ControlPanel(
    busy: Boolean,
    running: Boolean,
    log: String,
    onInstall: () -> Unit,
    onInstallBrowser: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onAuto: () -> Unit,
    onLaunchTerm: () -> Unit,
    onLaunchBrowser: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1117))
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SmallBtn("Auto", Color(0xFF238636), busy, onAuto)
            SmallBtn("Pacotes", Color(0xFF1F6FEB), busy, onInstall)
            SmallBtn("+Dillo", Color(0xFF6E40C9), busy, onInstallBrowser)
            SmallBtn("Iniciar", Color(0xFF238636), busy, onStart)
            SmallBtn("Parar", Color(0xFFDA3633), busy, onStop)
            SmallBtn("Refresh", Color(0xFF484F58), busy, onRefresh)
            SmallBtn("xterm", Color(0xFF1F6FEB), busy || !running, onLaunchTerm)
            SmallBtn("Browser", Color(0xFF1F6FEB), busy || !running, onLaunchBrowser)
        }
        if (log.isNotBlank()) {
            Text(
                text = log,
                color = Color(0xFF8B949E),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .padding(top = 6.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun SmallBtn(
    label: String,
    color: Color,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (disabled) Color(0xFF484F58) else Color.White,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(
                if (disabled) Color(0xFF21262D) else color,
                RoundedCornerShape(4.dp),
            )
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
