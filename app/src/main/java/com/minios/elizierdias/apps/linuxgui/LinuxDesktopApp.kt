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
    var statusMsg by remember { mutableStateOf("A verificar…") }
    var log by remember { mutableStateOf("") }
    var showControl by remember { mutableStateOf(false) }

    fun append(line: String) {
        log = (log + line + "\n").takeLast(4000)
    }

    fun runBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                append("[ERROR] ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        manager.initialize()
        geometry = gui.deviceGeometry()
        val st = gui.status()
        running = st.running
        statusMsg = st.message
        append("[GUI] geometria: $geometry")
        append(st.message)
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
                        append("[GUI] A instalar Dillo/Links2 (browser leve)...")
                        val r = gui.ensureLightBrowser { msg -> append(msg) }
                        if (r.isSuccess) append(r.getOrNull() ?: "OK")
                        else append("[ERROR] ${r.exceptionOrNull()?.message}")
                    }
                },
                onStart = {
                    runBusy {
                        // Sempre hard reset + start (nunca confiar em sessao antiga)
                        append("[GUI] Reset + iniciar VNC $geometry...")
                        gui.stop()
                        val r = gui.start(geometry)
                        if (r.isSuccess) {
                            val st = r.getOrNull()!!
                            running = true
                            geometry = st.geometry
                            statusMsg = st.message
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
            .height(36.dp)
            .background(Color(0xFF161B22))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (running) "●" else "○",
                color = if (running) Color(0xFF3FB950) else Color(0xFF8B949E),
                fontSize = 12.sp,
            )
            Text(
                text = " $port  $geometry",
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
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
                    .height(72.dp)
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
