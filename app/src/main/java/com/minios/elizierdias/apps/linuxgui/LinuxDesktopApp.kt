/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.apps.linuxgui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
 * Layout optimizado para espaço:
 *  - VNC parado  → painel de controlo expandido
 *  - VNC activo  → barra fina + Viewer ocupa quase tudo
 *  - Botão ⚙ abre/fecha o painel completo
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
    // Painel expandido por defeito só quando parado
    var panelExpanded by remember { mutableStateOf(true) }

    fun append(msg: String) {
        log = (log + msg.trimEnd() + "\n").takeLast(4000)
    }

    LaunchedEffect(Unit) {
        geometry = gui.deviceGeometry()
        val st = gui.status()
        running = st.running
        panelExpanded = !st.running
        append("[GUI] geometria: $geometry")
        append("[GUI] ${st.message}")
    }

    // Quando arranca com sucesso, colapsa o painel automaticamente
    LaunchedEffect(running) {
        if (running) panelExpanded = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)),
    ) {
        // ── Barra fina sempre visível ───────────────────────────────────
        ThinBar(
            running = running,
            port = gui.vncPort,
            geometry = geometry,
            expanded = panelExpanded,
            onToggle = { panelExpanded = !panelExpanded },
        )

        // ── Painel completo (só quando expandido) ───────────────────────
        if (panelExpanded) {
            ControlPanel(
                busy = busy,
                log = log,
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
                        panelExpanded = true
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
        }

        // ── Viewer (ocupa o resto) ──────────────────────────────────────
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

/** Barra fina no topo — sempre visível, ~28dp. */
@Composable
private fun ThinBar(
    running: Boolean,
    port: Int,
    geometry: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Color(0xFF161B22))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (running) "● $port" else "○ parado",
                color = if (running) Color(0xFF3FB950) else Color(0xFF8B949E),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = geometry,
                color = Color(0xFF58A6FF),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = if (expanded) "▲ Controlo" else "⚙ Controlo",
            color = Color(0xFFC9D1D9),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable(onClick = onToggle),
        )
    }
}

/** Painel de controlo completo (aparece só quando expandido). */
@Composable
private fun ControlPanel(
    busy: Boolean,
    log: String,
    onInstall: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onStatus: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B22))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SmallBtn("Pacotes", Color(0xFF1F6FEB), busy, onInstall)
            SmallBtn("Iniciar", Color(0xFF238636), busy, onStart)
            SmallBtn("Parar", Color(0xFFDA3633), busy, onStop)
            SmallBtn("Status", Color(0xFF30363D), busy, onStatus)
        }

        if (log.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = log,
                color = Color(0xFF8B949E),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp,
            vertical = 4.dp,
        ),
        modifier = Modifier.height(30.dp),
    ) {
        Text(label, fontSize = 11.sp)
    }
}

/**
 * Viewer — área principal do ecrã Linux.
 * Quando VNC está activo ocupa quase toda a janela.
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
            .padding(if (running) 0.dp else 6.dp)
            .then(
                if (!running) {
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF21262D), RoundedCornerShape(6.dp))
                } else {
                    Modifier
                },
            )
            .background(Color(0xFF010409)),
        contentAlignment = Alignment.Center,
    ) {
        if (!running) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(12.dp),
            ) {
                Text(
                    text = "Viewer",
                    color = Color(0xFF58A6FF),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Ecrã Linux aparece aqui\n\n1. ⚙ Controlo → Pacotes\n2. Iniciar VNC\n3. O desktop entra neste Viewer",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                )
            }
        } else {
            // Placeholder enquanto não há decoder RFB
            Text(
                text = "VNC :$port · $geometry\n\nDecoder RFB em breve…",
                color = Color(0xFF484F58),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
        }
    }
}
