/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */
package com.minios.elizierdias.linux.vnc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Viewer RFB embutido na janela Linux do NoskOS.
 * Liga a 127.0.0.1:port quando [active] e mostra o framebuffer.
 */
@Composable
fun RfbViewer(
    active: Boolean,
    host: String = "127.0.0.1",
    port: Int = 5901,
    modifier: Modifier = Modifier,
) {
    val client = remember { RfbClient(host, port) }
    var frameId by remember { mutableLongStateOf(0L) }
    var status by remember { mutableStateOf("A ligar…") }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(Unit) {
        client.onFrame = { frameId = client.frameId() }
        client.onState = { st, msg ->
            status = when (st) {
                RfbClient.State.CONNECTING -> "A ligar a $host:$port…"
                RfbClient.State.CONNECTED -> "Ligado · ${client.fbWidth}x${client.fbHeight}"
                RfbClient.State.ERROR -> "Erro: ${msg ?: "?"}"
                RfbClient.State.DISCONNECTED -> "Desligado"
            }
        }
        onDispose {
            client.onFrame = null
            client.onState = null
            client.disconnect()
        }
    }

    LaunchedEffect(active, port) {
        if (active) {
            // Pequeno delay para o vncserver acabar de abrir a porta
            delay(600)
            client.connect()
            // Reconnect loop leve
            while (active) {
                delay(2000)
                if (client.state == RfbClient.State.ERROR ||
                    client.state == RfbClient.State.DISCONNECTED
                ) {
                    status = "A reconectar…"
                    client.connect()
                }
            }
        } else {
            client.disconnect()
            status = "VNC parado"
        }
    }

    // Poll frame id for recomposition if callback missed
    LaunchedEffect(active) {
        while (active) {
            delay(50)
            val id = client.frameId()
            if (id != frameId) frameId = id
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF010409))
            .onSizeChanged { viewSize = it }
            .pointerInput(active, client.fbWidth, client.fbHeight, viewSize) {
                if (!active || client.fbWidth <= 0) return@pointerInput
                val fw = client.fbWidth.toFloat()
                val fh = client.fbHeight.toFloat()
                fun toFb(x: Float, y: Float): Pair<Int, Int> {
                    val vw = size.width.toFloat().coerceAtLeast(1f)
                    val vh = size.height.toFloat().coerceAtLeast(1f)
                    // Fit center (ContentScale.Fit)
                    val scale = minOf(vw / fw, vh / fh)
                    val dw = fw * scale
                    val dh = fh * scale
                    val ox = (vw - dw) / 2f
                    val oy = (vh - dh) / 2f
                    val fx = ((x - ox) / scale).toInt()
                    val fy = ((y - oy) / scale).toInt()
                    return fx to fy
                }
                detectTapGestures(
                    onPress = { offset ->
                        val (fx, fy) = toFb(offset.x, offset.y)
                        client.sendPointerEvent(fx, fy, 1) // left down
                        try {
                            awaitRelease()
                        } finally {
                            client.sendPointerEvent(fx, fy, 0)
                        }
                    },
                )
            }
            .pointerInput(active, client.fbWidth, client.fbHeight) {
                if (!active || client.fbWidth <= 0) return@pointerInput
                val fw = client.fbWidth.toFloat()
                val fh = client.fbHeight.toFloat()
                detectDragGestures(
                    onDragStart = { offset ->
                        val vw = size.width.toFloat().coerceAtLeast(1f)
                        val vh = size.height.toFloat().coerceAtLeast(1f)
                        val scale = minOf(vw / fw, vh / fh)
                        val dw = fw * scale
                        val dh = fh * scale
                        val ox = (vw - dw) / 2f
                        val oy = (vh - dh) / 2f
                        val fx = ((offset.x - ox) / scale).toInt()
                        val fy = ((offset.y - oy) / scale).toInt()
                        client.sendPointerEvent(fx, fy, 1)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val vw = size.width.toFloat().coerceAtLeast(1f)
                        val vh = size.height.toFloat().coerceAtLeast(1f)
                        val scale = minOf(vw / fw, vh / fh)
                        val dw = fw * scale
                        val dh = fh * scale
                        val ox = (vw - dw) / 2f
                        val oy = (vh - dh) / 2f
                        val fx = ((change.position.x - ox) / scale).toInt()
                        val fy = ((change.position.y - oy) / scale).toInt()
                        client.sendPointerEvent(fx, fy, 1)
                    },
                    onDragEnd = {
                        client.sendPointerEvent(0, 0, 0)
                    },
                    onDragCancel = {
                        client.sendPointerEvent(0, 0, 0)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = client.currentBitmap()
        // force read frameId for recomposition
        val _f = frameId
        if (active && bmp != null && !bmp.isRecycled && client.state == RfbClient.State.CONNECTED) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Linux desktop",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = if (active) status else "Viewer\nInicia o VNC para ver o Linux",
                color = Color(0xFF8B949E),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
        }
    }
}
