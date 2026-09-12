/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */
package com.minios.elizierdias.linux.vnc

import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Viewer RFB embutido na janela Linux do NoskOS.
 * Toque → rato; teclado Android → KeyEvent RFB.
 */
@Composable
fun RfbViewer(
    active: Boolean,
    host: String = "127.0.0.1",
    port: Int = 5901,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val client = remember { RfbClient(host, port) }
    var frameId by remember { mutableLongStateOf(0L) }
    var status by remember { mutableStateOf("A ligar…") }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val focusRequester = remember { FocusRequester() }

    fun showKeyboard() {
        try {
            focusRequester.requestFocus()
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        } catch (_: Exception) {
        }
    }

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
            delay(600)
            client.connect()
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

    LaunchedEffect(active) {
        while (active) {
            delay(50)
            val id = client.frameId()
            if (id != frameId) frameId = id
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF010409))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { ke ->
                    if (!active || client.state != RfbClient.State.CONNECTED) return@onPreviewKeyEvent false
                    val native = ke.nativeKeyEvent ?: return@onPreviewKeyEvent false
                    val sym = RfbKeymap.keysym(native)
                    if (sym == 0) return@onPreviewKeyEvent false
                    when (ke.type) {
                        KeyEventType.KeyDown -> {
                            client.sendKeyEvent(sym, true)
                            true
                        }
                        KeyEventType.KeyUp -> {
                            client.sendKeyEvent(sym, false)
                            true
                        }
                        else -> false
                    }
                }
                .onSizeChanged { viewSize = it }
                .pointerInput(active, client.fbWidth, client.fbHeight, viewSize) {
                    if (!active || client.fbWidth <= 0) return@pointerInput
                    val fw = client.fbWidth.toFloat()
                    val fh = client.fbHeight.toFloat()
                    fun toFb(x: Float, y: Float): Pair<Int, Int> {
                        val vw = size.width.toFloat().coerceAtLeast(1f)
                        val vh = size.height.toFloat().coerceAtLeast(1f)
                        val scale = minOf(vw / fw, vh / fh)
                        val dw = fw * scale
                        val dh = fh * scale
                        val ox = (vw - dw) / 2f
                        val oy = (vh - dh) / 2f
                        return ((x - ox) / scale).toInt() to ((y - oy) / scale).toInt()
                    }
                    detectTapGestures(
                        onPress = { offset ->
                            focusRequester.requestFocus()
                            val (fx, fy) = toFb(offset.x, offset.y)
                            client.sendPointerEvent(fx, fy, 1)
                            try {
                                awaitRelease()
                            } finally {
                                client.sendPointerEvent(fx, fy, 0)
                            }
                        },
                        onDoubleTap = {
                            showKeyboard()
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
                            val ox = (vw - fw * scale) / 2f
                            val oy = (vh - fh * scale) / 2f
                            val fx = ((offset.x - ox) / scale).toInt()
                            val fy = ((offset.y - oy) / scale).toInt()
                            client.sendPointerEvent(fx, fy, 1)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val vw = size.width.toFloat().coerceAtLeast(1f)
                            val vh = size.height.toFloat().coerceAtLeast(1f)
                            val scale = minOf(vw / fw, vh / fh)
                            val ox = (vw - fw * scale) / 2f
                            val oy = (vh - fh * scale) / 2f
                            val fx = ((change.position.x - ox) / scale).toInt()
                            val fy = ((change.position.y - oy) / scale).toInt()
                            client.sendPointerEvent(fx, fy, 1)
                        },
                        onDragEnd = { client.sendPointerEvent(0, 0, 0) },
                        onDragCancel = { client.sendPointerEvent(0, 0, 0) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val bmp = client.currentBitmap()
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

        // Barra mínima: teclado
        if (active) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            ) {
                Text(
                    text = "⌨ Teclado",
                    color = Color(0xFF58A6FF),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(Color(0xCC161B22))
                        .clickable { showKeyboard() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
