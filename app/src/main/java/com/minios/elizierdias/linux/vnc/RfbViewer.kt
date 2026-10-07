/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */
package com.minios.elizierdias.linux.vnc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Viewer RFB — mecânica alinhada ao rato do desktop NoskOS (trackpad):
 * - Arrastar = move o cursor SEM clicar (trackpad local)
 * - Toque = clique esquerdo na posicao do evento (absoluto — compativel com VirtualMouse)
 * - Duplo toque = 2x esquerdo
 * - Toque longo = clique direito
 * - Botao L = manter esquerdo (arrastar janelas)
 * - Cursor branco
 */
@Composable
fun RfbViewer(
    active: Boolean,
    host: String = "127.0.0.1",
    port: Int = 5901,
    modifier: Modifier = Modifier,
) {
    val client = remember(host, port) { RfbClient(host, port) }
    var frameId by remember { mutableLongStateOf(0L) }
    var status by remember { mutableStateOf("A ligar…") }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var showKeys by remember { mutableStateOf(false) }
    var gamerMode by remember { mutableStateOf(false) }
    var lmbHold by remember { mutableStateOf(false) }
    var rmbHold by remember { mutableStateOf(false) }
    var cursorX by remember { mutableFloatStateOf(-1f) }
    var cursorY by remember { mutableFloatStateOf(-1f) }
    var cursorVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var imeText by remember { mutableStateOf(" ") }
    val keyboard = LocalSoftwareKeyboardController.current

    fun toFbLocal(x: Float, y: Float, vw: Float, vh: Float): Pair<Int, Int> {
        if (client.fbWidth <= 0) return 0 to 0
        val fw = client.fbWidth.toFloat()
        val fh = client.fbHeight.toFloat()
        val scale = minOf(vw / fw, vh / fh)
        val ox = (vw - fw * scale) / 2f
        val oy = (vh - fh * scale) / 2f
        val fx = ((x - ox) / scale).toInt().coerceIn(0, (client.fbWidth - 1).coerceAtLeast(0))
        val fy = ((y - oy) / scale).toInt().coerceIn(0, (client.fbHeight - 1).coerceAtLeast(0))
        return fx to fy
    }

    fun pointerMask(): Int {
        var m = 0
        if (lmbHold) m = m or 1
        if (rmbHold) m = m or 4
        return m
    }

    fun sendMove(x: Float, y: Float, vw: Float, vh: Float) {
        if (client.state != RfbClient.State.CONNECTED) return
        val (fx, fy) = toFbLocal(x, y, vw, vh)
        client.sendPointerEvent(fx, fy, pointerMask())
    }

    fun sendClick(mask: Int, x: Float, y: Float, vw: Float, vh: Float) {
        if (client.state != RfbClient.State.CONNECTED) return
        val (fx, fy) = toFbLocal(x, y, vw, vh)
        client.sendPointerEvent(fx, fy, mask)
        client.sendPointerEvent(fx, fy, pointerMask())
    }

    fun tapKey(keysym: Int) {
        if (client.state != RfbClient.State.CONNECTED) return
        client.sendKeyEvent(keysym, true)
        client.sendKeyEvent(keysym, false)
    }

    fun tapModKey(mod: Int, key: Int) {
        if (client.state != RfbClient.State.CONNECTED) return
        client.sendKeyEvent(mod, true)
        client.sendKeyEvent(key, true)
        client.sendKeyEvent(key, false)
        client.sendKeyEvent(mod, false)
    }

    fun sendChar(ch: Char) {
        when (ch) {
            '\n' -> tapKey(0xff0d)
            '\t' -> tapKey(0xff09)
            ' ' -> tapKey(0x20)
            else -> {
                val code = ch.code
                if (code in 0x20..0xFF) tapKey(code)
                else if (code in 0x100..0xFFFD) tapKey(0x01000000 or code)
            }
        }
    }

    fun openKeyboard() {
        try {
            if (client.state == RfbClient.State.CONNECTED && client.fbWidth > 0) {
                // Clique na zona tipica do xterm para focar
                val fx = (client.fbWidth * 15) / 100
                val fy = (client.fbHeight * 25) / 100
                client.sendPointerEvent(fx, fy, 0)
                client.sendPointerEvent(fx, fy, 1)
                client.sendPointerEvent(fx, fy, 0)
            }
            focusRequester.requestFocus()
            keyboard?.show()
            focusRequester.requestFocus()
            keyboard?.show()
        } catch (_: Exception) {
        }
    }

    DisposableEffect(client) {
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

    LaunchedEffect(active, status) {
        if (active && client.state == RfbClient.State.CONNECTED) {
            delay(900)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF010409))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .pointerInput(active, client.fbWidth, client.fbHeight, lmbHold, rmbHold) {
                    if (!active || client.fbWidth <= 0) return@pointerInput
                    val vw = size.width.toFloat().coerceAtLeast(1f)
                    val vh = size.height.toFloat().coerceAtLeast(1f)

                    // Trackpad: arrastar move cursor (com L/R se activos)
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (cursorX < 0f) {
                                cursorX = offset.x
                                cursorY = offset.y
                            }
                            cursorVisible = true
                            sendMove(cursorX, cursorY, vw, vh)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (cursorX < 0f) {
                                cursorX = change.position.x
                                cursorY = change.position.y
                            } else {
                                cursorX = (cursorX + dragAmount.x).coerceIn(0f, vw)
                                cursorY = (cursorY + dragAmount.y).coerceIn(0f, vh)
                            }
                            cursorVisible = true
                            sendMove(cursorX, cursorY, vw, vh)
                        },
                        onDragEnd = {
                            sendMove(cursorX.coerceAtLeast(0f), cursorY.coerceAtLeast(0f), vw, vh)
                        },
                        onDragCancel = {
                            sendMove(cursorX.coerceAtLeast(0f), cursorY.coerceAtLeast(0f), vw, vh)
                        },
                    )
                }
                .pointerInput(active, client.fbWidth, client.fbHeight, lmbHold, rmbHold) {
                    if (!active || client.fbWidth <= 0) return@pointerInput
                    val vw = size.width.toFloat().coerceAtLeast(1f)
                    val vh = size.height.toFloat().coerceAtLeast(1f)

                    detectTapGestures(
                        onTap = {
                            // Clique absoluto na posicao do evento (VirtualMouse injecta aqui)
                            val cx = it.x
                            val cy = it.y
                            cursorX = cx
                            cursorY = cy
                            cursorVisible = true
                            sendClick(1, cx, cy, vw, vh)
                        },
                        onDoubleTap = {
                            val cx = it.x
                            val cy = it.y
                            cursorX = cx
                            cursorY = cy
                            cursorVisible = true
                            sendClick(1, cx, cy, vw, vh)
                            sendClick(1, cx, cy, vw, vh)
                        },
                        onLongPress = {
                            val cx = it.x
                            val cy = it.y
                            cursorX = cx
                            cursorY = cy
                            cursorVisible = true
                            // Direito: ativa rmbHold para arrastar janela; toque L solta
                            rmbHold = true
                            sendMove(cx, cy, vw, vh)
                            sendClick(4, cx, cy, vw, vh)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val bmp = client.currentBitmap()
            @Suppress("UNUSED_VARIABLE")
            val tick = frameId
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

            if (active && cursorVisible && cursorX >= 0f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = cursorX
                    val cy = cursorY
                    val path = Path().apply {
                        moveTo(cx, cy)
                        lineTo(cx, cy + 18.dp.toPx())
                        lineTo(cx + 5.dp.toPx(), cy + 14.dp.toPx())
                        lineTo(cx + 10.dp.toPx(), cy + 22.dp.toPx())
                        lineTo(cx + 12.dp.toPx(), cy + 20.dp.toPx())
                        lineTo(cx + 7.dp.toPx(), cy + 12.dp.toPx())
                        lineTo(cx + 14.dp.toPx(), cy + 12.dp.toPx())
                        close()
                    }
                    drawPath(path, Color.Black, style = Stroke(width = 3.dp.toPx()))
                    drawPath(path, Color.White)
                }
            }
        }

        // Bottom controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC0D1117))
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val vw = viewSize.width.toFloat().coerceAtLeast(1f)
            val vh = viewSize.height.toFloat().coerceAtLeast(1f)
            Text(
                text = if (lmbHold) "L●" else "L",
                color = if (lmbHold) Color(0xFF3FB950) else Color(0xFFC9D1D9),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(
                        if (lmbHold) Color(0xFF238636) else Color(0xFF21262D),
                        RoundedCornerShape(4.dp),
                    )
                    .clickable {
                        lmbHold = !lmbHold
                        if (cursorX >= 0f) sendMove(cursorX, cursorY, vw, vh)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                text = if (rmbHold) "R●" else "R",
                color = if (rmbHold) Color(0xFF58A6FF) else Color(0xFFC9D1D9),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(
                        if (rmbHold) Color(0xFF1F6FEB) else Color(0xFF21262D),
                        RoundedCornerShape(4.dp),
                    )
                    .clickable {
                        rmbHold = !rmbHold
                        if (cursorX >= 0f) sendMove(cursorX, cursorY, vw, vh)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                text = "⌨",
                color = Color(0xFFC9D1D9),
                fontSize = 12.sp,
                modifier = Modifier
                    .background(Color(0xFF21262D), RoundedCornerShape(4.dp))
                    .clickable { openKeyboard() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                text = if (showKeys) "keys▾" else "keys",
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(Color(0xFF21262D), RoundedCornerShape(4.dp))
                    .clickable { showKeys = !showKeys }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Text(
                text = status,
                color = Color(0xFF8B949E),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (showKeys) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 40.dp, start = 6.dp)
                    .background(Color(0xEE161B22), RoundedCornerShape(6.dp))
                    .padding(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "Esc" to 0xff1b,
                        "Tab" to 0xff09,
                        "Enter" to 0xff0d,
                        "Bksp" to 0xff08,
                    ).forEach { (label, key) ->
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .background(Color(0xFF21262D), RoundedCornerShape(4.dp))
                                .clickable { tapKey(key) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        // Hidden IME field for keyboard input
        BasicTextField(
            value = imeText,
            onValueChange = { new ->
                if (new.length > imeText.length) {
                    val added = new.substring(imeText.length)
                    added.forEach { sendChar(it) }
                } else if (new.length < imeText.length) {
                    repeat(imeText.length - new.length) { tapKey(0xff08) }
                }
                imeText = " "
            },
            modifier = Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth()
                .padding(0.dp),
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
        )
    }
}
