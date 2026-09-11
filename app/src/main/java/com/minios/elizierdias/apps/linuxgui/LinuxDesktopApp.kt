/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.apps.linuxgui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minios.elizierdias.linux.LinuxGuiRuntime
import com.minios.elizierdias.linux.LinuxManager
import kotlinx.coroutines.launch

/**
 * Janela NoskOS para Linux gráfico.
 *
 * Layout:
 *  ┌─────────────────────────────┐
 *  │  Painel de Controlo         │  ← botões + status + log curto
 *  ├─────────────────────────────┤
 *  │                             │
 *  │         Viewer              │  ← ecrã Linux (RFB) entra aqui
 *  │                             │
 *  └─────────────────────────────┘
 */
@Composable
fun LinuxDesktopApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { LinuxManager(context) }
    val gui = remember(context) {
        LinuxGuiRuntime(context, manager.getRuntime())
    }

    var log by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var geometry by remember { mutableStateOf(gui.deviceGeometry()) }
    var showLog by remember { mutableStateOf(false) }

    fun append(msg: String) {
        log = (log + msg.trimEnd() + "\n").takeLast(4000)
    }

    LaunchedEffect(Unit) {
        geometry = gui.deviceGeometry()
        val st = gui.status()
        running = st.running
        append("[GUI] geometria: $geometry")
        append("[GUI] ${st.message}")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)),
    ) {
        // ── Painel de Controlo ──────────────────────────────────────────
        ControlPanel(
            running = running,
            geometry = geometry,
            port = gui.vncPort,
            busy = busy,
            showLog = showLog,
            log = log,
            onToggleLog = { showLog = !showLog },
            onInstall = {
                if (busy) return@ControlPanel
                busy = true
                scope.launch {
                    append("[GUI] A instalar pacotes VNC...")
                    val r = gui.ensureGuiPackages { msg -> append(msg) }
                    busy = false
                    if (r.isSuccess) append(r.getOrNull() ?: "OK")
                    else append("[ERROR] ${r.exceptionOrNull()?.message}")
                }
            },
            onStart = {
                if (busy) return@ControlPanel
                busy = true
                scope.launch {
                    append("[GUI] A iniciar VNC $geometry...")
                    val r = gui.start(geometry)
                    busy = false
                    if (r.isSuccess) {
                        running = true
                        append(r.getOrNull()?.message ?: "OK")
                        append("[GUI] Sessão pronta · porta ${gui.vncPort}")
                    } else {
                        append("[ERROR] ${r.exceptionOrNull()?.message}")
                    }
                }
            },
            onStop = {
                if (busy) return@ControlPanel
                busy = true
                scope.launch {
                    val r = gui.stop()
                    busy = false
                    running = false
                    append(r.getOrNull() ?: r.exceptionOrNull()?.message ?: "parado")
                }
            },
            onStatus = {
                if (busy) return@ControlPanel
                busy = true
                scope.launch {
                    val st = gui.status()
                    busy = false
                    running = st.running
                    geometry = st.geometry
                    append("[GUI] ${st.message}")
                }
            },
        )

        // ── Viewer ──────────────────────────────────────────────────────
        Viewer(
            running = running,
            port = gui.vncPort,
            geometry = geometry,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

/** Painel de controlo (topo da janela). */
@Composable
private fun ControlPanel(
    running: Boolean,
    geometry: String,
    port: Int,
    busy: Boolean,
    showLog: Boolean,
    log: String,
    onToggleLog: () -> Unit,
    onInstall: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onStatus: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B22))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // Status line
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (running) "● ATIVO  :$port" else "○ parado",
                color = if (running) Color(0xFF3FB950) else Color(0xFF8B949E),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = geometry,
                color = Color(0xFF58A6FF),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Botões
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SmallBtn("Pacotes", Color(0xFF1F6FEB), busy, onInstall)
            SmallBtn("Iniciar", Color(0xFF238636), busy, onStart)
            SmallBtn("Parar", Color(0xFFDA3633), busy, onStop)
            SmallBtn("Status", Color(0xFF30363D), busy, onStatus)
            SmallBtn(if (showLog) "Log ▲" else "Log ▼", Color(0xFF21262D), false, onToggleLog)
        }

        // Log (opcional, compacto)
        if (showLog && log.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = log,
                color = Color(0xFF8B949E),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .verticalScroll(rememberScrollState())
                    .background(Color(0xFF0D1117), RoundedCornerShape(4.dp))
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun SmallBtn(
    label: String,
    color: Color,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp),
    ) {
        Text(label, fontSize = 11.sp)
    }
}

/**
 * Viewer — área onde o framebuffer Linux (RFB) será desenhado.
 * Por agora: placeholder visual + estado da sessão.
 * Próximo passo: decoder RFB → Bitmap → ImageBitmap aqui.
 */
@Composable
private fun Viewer(
    running: Boolean,
    port: Int,
    geometry: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF010409))
            .border(1.dp, Color(0xFF21262D), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Viewer",
                color = Color(0xFF58A6FF),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (running) {
                    "Sessão VNC activa · 127.0.0.1:$port\n$geometry\n\nAguardando decoder RFB nativo…"
                } else {
                    "Ecrã Linux aparece aqui\n\n1. Instala pacotes\n2. Inicia VNC\n3. O desktop Linux entra neste Viewer"
                },
                color = Color(0xFF8B949E),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
        }
    }
}
