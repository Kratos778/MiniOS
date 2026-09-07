/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.apps.linuxgui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minios.elizierdias.linux.LinuxGuiRuntime
import com.minios.elizierdias.linux.LinuxManager
import kotlinx.coroutines.launch

/**
 * Janela MiniOS para a sessão gráfica Linux (TigerVNC).
 * Geometria = ecrã do telefone/tablet.
 * O ecrã RFB (pixels do Linux) entra no próximo passo (cliente VNC nativo).
 */
@Composable
fun LinuxDesktopApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { LinuxManager(context) }
    val gui = remember(context) {
        LinuxGuiRuntime(context, manager.getRuntime())
    }

    var log by remember { mutableStateOf("Linux GUI · TigerVNC\n") }
    var busy by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var geometry by remember { mutableStateOf(gui.deviceGeometry()) }

    fun append(msg: String) {
        log = log + msg.trimEnd() + "\n"
    }

    LaunchedEffect(Unit) {
        geometry = gui.deviceGeometry()
        val st = gui.status()
        running = st.running
        append("[GUI] geometria ecrã: $geometry")
        append("[GUI] ${st.message}")
        append("")
        append("1) Iniciar sessão VNC")
        append("2) Cliente VNC nativo (próximo update) mostra o ecrã Linux aqui")
        append("3) No Terminal podes testar: vncserver -list")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(12.dp),
    ) {
        Text(
            text = if (running) "Linux Desktop · ATIVO :${gui.vncPort}" else "Linux Desktop · parado",
            color = if (running) Color(0xFF3FB950) else Color(0xFF8B949E),
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "Geometria: $geometry (ecrã deste dispositivo)",
            color = Color(0xFF58A6FF),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    scope.launch {
                        append("[GUI] A iniciar VNC $geometry...")
                        val r = gui.start(geometry)
                        busy = false
                        if (r.isSuccess) {
                            running = true
                            append(r.getOrNull()?.message ?: "OK")
                            append("[GUI] Sessão pronta. Porta ${gui.vncPort}")
                        } else {
                            append("[ERROR] ${r.exceptionOrNull()?.message}")
                        }
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
            ) {
                Text("Iniciar VNC", fontSize = 12.sp)
            }
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    scope.launch {
                        val r = gui.stop()
                        busy = false
                        running = false
                        append(r.getOrNull() ?: r.exceptionOrNull()?.message ?: "stop")
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633)),
            ) {
                Text("Parar", fontSize = 12.sp)
            }
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    scope.launch {
                        val st = gui.status()
                        busy = false
                        running = st.running
                        geometry = st.geometry
                        append("[GUI] ${st.message}")
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
            ) {
                Text("Status", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = log,
            color = Color(0xFFC9D1D9),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        )
    }
}
