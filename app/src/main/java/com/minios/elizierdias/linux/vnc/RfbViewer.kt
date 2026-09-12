/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */
package com.minios.elizierdias.linux.vnc

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Viewer RFB: toque = rato; teclado do telefone via campo de texto invisível → RFB keysyms.
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
    val focusRequester = remember { FocusRequester() }
    var imeText by remember { mutableStateOf("") }

    fun sendChar(ch: Char) {
        if (client.state != RfbClient.State.CONNECTED) return
        when (ch) {
            '\n' -> {
                client.sendKeyEvent(0xff0d, true)
                client.sendKeyEvent(0xff0d, false)
            }
            '\b' -> {
                client.sendKeyEvent(0xff08, true)
                client.sendKeyEvent(0xff08, false)
            }
            else -> {
                val code = ch.code
                if (code in 0x20..0xFF) {
                    client.sendKeyEvent(code, true)
                    client.sendKeyEvent(code, false)
                }
            }
        }
    }

    fun sendBackspace() {
        client.sendKeyEvent(0xff08, true)
        client.sendKeyEvent(0xff08, false)
    }

    fun sendEnter() {
        client.sendKeyEvent(0xff0d, true)
        client.sendKeyEvent(0xff0d, false)
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
                .onSizeChanged { viewSize = it }
                .pointerInput(active, client.fbWidth, client.fbHeight, viewSize) {
                    if (!active || client.fbWidth <= 0) return@pointerInput
                    val fw = client.fbWidth.toFloat()
                    val fh = client.fbHeight.toFloat()
                    fun toFb(x: Float, y: Float): Pair<Int, Int> {
                        val vw = size.width.toFloat().coerceAtLeast(1f)
                        val vh = size.height.toFloat().coerceAtLeast(1f)
                        val scale = minOf(vw / fw, vh / fh)
                        val ox = (vw - fw * scale) / 2f
                        val oy = (vh - fh * scale) / 2f
                        return ((x - ox) / scale).toInt() to ((y - oy) / scale).toInt()
                    }
                    detectTapGestures(
                        onPress = { offset ->
                            val (fx, fy) = toFb(offset.x, offset.y)
                            client.sendPointerEvent(fx, fy, 1)
                            try {
                                awaitRelease()
                            } finally {
                                client.sendPointerEvent(fx, fy, 0)
                            }
                        },
                        onDoubleTap = {
                            try {
                                focusRequester.requestFocus()
                            } catch (_: Exception) {
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
                            val ox = (vw - fw * scale) / 2f
                            val oy = (vh - fh * scale) / 2f
                            client.sendPointerEvent(
                                ((offset.x - ox) / scale).toInt(),
                                ((offset.y - oy) / scale).toInt(),
                                1,
                            )
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val vw = size.width.toFloat().coerceAtLeast(1f)
                            val vh = size.height.toFloat().coerceAtLeast(1f)
                            val scale = minOf(vw / fw, vh / fh)
                            val ox = (vw - fw * scale) / 2f
                            val oy = (vh - fh * scale) / 2f
                            client.sendPointerEvent(
                                ((change.position.x - ox) / scale).toInt(),
                                ((change.position.y - oy) / scale).toInt(),
                                1,
                            )
                        },
                        onDragEnd = { client.sendPointerEvent(0, 0, 0) },
                        onDragCancel = { client.sendPointerEvent(0, 0, 0) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val bmp = client.currentBitmap()
            @Suppress("UNUSED_VARIABLE")
            val frameTick = frameId
            if (active && bmp != null && !bmp.isRecycled &&
                client.state == RfbClient.State.CONNECTED
            ) {
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

        // Campo invisível para o teclado do telefone (IME)
        if (active) {
            BasicTextField(
                value = imeText,
                onValueChange = { new ->
                    when {
                        new.length > imeText.length -> {
                            val added = new.substring(imeText.length)
                            added.forEach { sendChar(it) }
                            imeText = ""
                        }
                        new.length < imeText.length -> {
                            // backspace(s)
                            repeat(imeText.length - new.length) { sendBackspace() }
                            imeText = ""
                        }
                        else -> imeText = ""
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .focusRequester(focusRequester)
                    .focusable(),
                textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                cursorBrush = SolidColor(Color.Transparent),
                singleLine = false,
            )
        }

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
                        .clickable {
                            try {
                                focusRequester.requestFocus()
                            } catch (_: Exception) {
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Text(
                    text = " ⏎ ",
                    color = Color(0xFF3FB950),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(Color(0xCC161B22))
                        .clickable { sendEnter() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}
