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
 * Rato Linux (Viewer RFB) — modo toque absoluto (como ecrã tactil):
 * - Onde tocas = onde o cursor vai (nao modo "trackpad")
 * - Toque = clique esquerdo
 * - Arrastar com dedo = arrastar com botao pressionado
 * - Botao L = forcar botao esquerdo (arrastar janelas)
 * Cursor: seta branca
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
    var cursorX by remember { mutableFloatStateOf(-1f) }
    var cursorY by remember { mutableFloatStateOf(-1f) }
    var cursorVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var imeText by remember { mutableStateOf(" ") }
    val keyboard = LocalSoftwareKeyboardController.current

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
                if (code in 0x20..0xFF) {
                    tapKey(code)
                } else if (code in 0x100..0xFFFD) {
                    tapKey(0x01000000 or code)
                }
            }
        }
    }

    fun openKeyboard() {
        try {
            if (client.state == RfbClient.State.CONNECTED && client.fbWidth > 0) {
                val fx = client.fbWidth / 2
                val fy = (client.fbHeight * 2) / 3
                client.sendPointerEvent(fx, fy, 1)
                client.sendPointerEvent(fx, fy, 0)
            }
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

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF010409))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .pointerInput(active, client.fbWidth, client.fbHeight, lmbHold) {
                    if (!active || client.fbWidth <= 0) return@pointerInput
                    val fw = client.fbWidth.toFloat()
                    val fh = client.fbHeight.toFloat()

                    fun toFb(x: Float, y: Float): Pair<Int, Int> {
                        val vw = size.width.toFloat().coerceAtLeast(1f)
                        val vh = size.height.toFloat().coerceAtLeast(1f)
                        val scale = minOf(vw / fw, vh / fh)
                        val ox = (vw - fw * scale) / 2f
                        val oy = (vh - fh * scale) / 2f
                        val fx = ((x - ox) / scale).toInt()
                            .coerceIn(0, (client.fbWidth - 1).coerceAtLeast(0))
                        val fy = ((y - oy) / scale).toInt()
                            .coerceIn(0, (client.fbHeight - 1).coerceAtLeast(0))
                        return fx to fy
                    }

                    // Toque = clique (absoluto: dedo = cursor)
                    detectTapGestures(
                        onTap = { offset ->
                            cursorX = offset.x
                            cursorY = offset.y
                            cursorVisible = true
                            val (fx, fy) = toFb(offset.x, offset.y)
                            client.sendPointerEvent(fx, fy, 1)
                            if (!lmbHold) {
                                client.sendPointerEvent(fx, fy, 0)
                            }
                        },
                        onDoubleTap = { openKeyboard() },
                        onLongPress = { offset ->
                            // clique direito
                            cursorX = offset.x
                            cursorY = offset.y
                            cursorVisible = true
                            val (fx, fy) = toFb(offset.x, offset.y)
                            client.sendPointerEvent(fx, fy, 4)
                            client.sendPointerEvent(fx, fy, 0)
                        },
                    )
                }
                .pointerInput(active, client.fbWidth, client.fbHeight, lmbHold) {
                    if (!active || client.fbWidth <= 0) return@pointerInput
                    val fw = client.fbWidth.toFloat()
                    val fh = client.fbHeight.toFloat()

                    fun toFb(x: Float, y: Float): Pair<Int, Int> {
                        val vw = size.width.toFloat().coerceAtLeast(1f)
                        val vh = size.height.toFloat().coerceAtLeast(1f)
                        val scale = minOf(vw / fw, vh / fh)
                        val ox = (vw - fw * scale) / 2f
                        val oy = (vh - fh * scale) / 2f
                        val fx = ((x - ox) / scale).toInt()
                            .coerceIn(0, (client.fbWidth - 1).coerceAtLeast(0))
                        val fy = ((y - oy) / scale).toInt()
                            .coerceIn(0, (client.fbHeight - 1).coerceAtLeast(0))
                        return fx to fy
                    }

                    // Arrastar = mover com botao esquerdo pressionado (como rato normal)
                    detectDragGestures(
                        onDragStart = { offset ->
                            cursorX = offset.x
                            cursorY = offset.y
                            cursorVisible = true
                            val (fx, fy) = toFb(offset.x, offset.y)
                            client.sendPointerEvent(fx, fy, 1)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            cursorX = change.position.x
                            cursorY = change.position.y
                            val (fx, fy) = toFb(change.position.x, change.position.y)
                            client.sendPointerEvent(fx, fy, 1)
                        },
                        onDragEnd = {
                            val (fx, fy) = toFb(cursorX, cursorY)
                            client.sendPointerEvent(fx, fy, if (lmbHold) 1 else 0)
                        },
                        onDragCancel = {
                            val (fx, fy) = toFb(cursorX, cursorY)
                            client.sendPointerEvent(fx, fy, if (lmbHold) 1 else 0)
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

            // Cursor branco — ponta no ponto do toque
            if (active && cursorVisible && cursorX >= 0f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = cursorX
                    val cy = cursorY
                    val fill = if (lmbHold) Color(0xFFFFCCCC) else Color(0xFFFFFFFF)
                    val edge = if (lmbHold) Color(0xFFFF4444) else Color(0xFF222222)
                    val path = Path().apply {
                        moveTo(cx, cy)
                        lineTo(cx + 14f, cy + 22f)
                        lineTo(cx + 6f, cy + 20f)
                        lineTo(cx + 10f, cy + 32f)
                        lineTo(cx + 4f, cy + 34f)
                        lineTo(cx, cy + 22f)
                        close()
                    }
                    drawPath(path, color = fill)
                    drawPath(path, color = edge, style = Stroke(width = 1.4f))
                }
            }
        }

        if (active) {
            BasicTextField(
                value = imeText,
                onValueChange = { new ->
                    if (new.length > imeText.length) {
                        new.substring(imeText.length).forEach { sendChar(it) }
                    } else if (new.length < imeText.length) {
                        repeat((imeText.length - new.length).coerceAtMost(32)) {
                            tapKey(0xff08)
                        }
                    }
                    imeText = " "
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(0.dp)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                cursorBrush = SolidColor(Color.Transparent),
            )
        }

        if (active) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                if (showKeys) {
                    SpecialKeysPanel(
                        gamerMode = gamerMode,
                        onKey = { tapKey(it) },
                        onModKey = { mod, key -> tapModKey(mod, key) },
                        onToggleGamer = { gamerMode = !gamerMode },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (lmbHold) "L●" else "L",
                        color = if (lmbHold) Color(0xFFF85149) else Color(0xFFC9D1D9),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(
                                if (lmbHold) Color(0xCC3D1F1F) else Color(0xCC161B22),
                                RoundedCornerShape(4.dp),
                            )
                            .clickable {
                                lmbHold = !lmbHold
                                if (!lmbHold && cursorX >= 0f && client.fbWidth > 0) {
                                    val fw = client.fbWidth.toFloat()
                                    val fh = client.fbHeight.toFloat()
                                    val vw = viewSize.width.toFloat().coerceAtLeast(1f)
                                    val vh = viewSize.height.toFloat().coerceAtLeast(1f)
                                    val scale = minOf(vw / fw, vh / fh)
                                    val ox = (vw - fw * scale) / 2f
                                    val oy = (vh - fh * scale) / 2f
                                    val fx = ((cursorX - ox) / scale).toInt()
                                    val fy = ((cursorY - oy) / scale).toInt()
                                    client.sendPointerEvent(fx, fy, 0)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                    TinyBtn("⌨") { openKeyboard() }
                    TinyBtn("⏎") { tapKey(0xff0d) }
                    TinyBtn("⌫") { tapKey(0xff08) }
                    TinyBtn(if (showKeys) "▾" else "⌨+") { showKeys = !showKeys }
                }
            }
        }
    }
}

@Composable
private fun TinyBtn(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color(0xFFC9D1D9),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(Color(0xCC161B22), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
private fun SpecialKeysPanel(
    gamerMode: Boolean,
    onKey: (Int) -> Unit,
    onModKey: (Int, Int) -> Unit,
    onToggleGamer: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .padding(bottom = 4.dp)
            .background(Color(0xE0161B22), RoundedCornerShape(6.dp))
            .padding(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (gamerMode) "Gamer" else "Teclas",
                color = Color(0xFF58A6FF),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = if (gamerMode) "Normal" else "Gamer",
                color = Color(0xFF3FB950),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onToggleGamer),
            )
        }
        Row(
            modifier = Modifier
                .horizontalScroll(scroll)
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "Close",
                color = Color(0xFFFF7B72),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(Color(0xFF3D1F1F), RoundedCornerShape(3.dp))
                    .clickable { onModKey(0xffe9, 0xffc1) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            val keys = if (gamerMode) {
                listOf(
                    "Tab" to 0xff09, "Esc" to 0xff1b,
                    "↑" to 0xff52, "↓" to 0xff54, "←" to 0xff51, "→" to 0xff53,
                    "Ctrl" to 0xffe3, "Alt" to 0xffe9, "Spc" to 0x20,
                )
            } else {
                listOf(
                    "Tab" to 0xff09, "Esc" to 0xff1b, "⇧" to 0xffe1,
                    "Ctrl" to 0xffe3, "Alt" to 0xffe9,
                    "↑" to 0xff52, "↓" to 0xff54, "←" to 0xff51, "→" to 0xff53,
                    "F1" to 0xffbe, "F2" to 0xffbf, "F3" to 0xffc0, "F4" to 0xffc1,
                    "F5" to 0xffc2, "F6" to 0xffc3, "F7" to 0xffc4, "F8" to 0xffc5,
                    "F9" to 0xffc6, "F10" to 0xffc7, "F11" to 0xffc8, "F12" to 0xffc9,
                )
            }
            keys.forEach { (label, sym) ->
                Text(
                    text = label,
                    color = Color(0xFFE6EDF3),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(Color(0xFF21262D), RoundedCornerShape(3.dp))
                        .clickable { onKey(sym) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}
