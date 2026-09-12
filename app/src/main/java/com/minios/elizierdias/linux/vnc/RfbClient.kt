/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 *
 * Minimal RFB 3.8 client for TigerVNC / Xtigervnc.
 * Security: None (type 1). Encoding: Raw. Pixel: 32bpp LE RGB.
 */
package com.minios.elizierdias.linux.vnc

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Cliente RFB leve para 127.0.0.1:5901 (TigerVNC com -SecurityTypes None).
 *
 * Limitacoes MVP:
 * - So encoding Raw (0)
 * - So security None
 * - Sem Tight/ZRLE/Hextile (pode adicionar depois)
 */
class RfbClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 5901,
) {
    enum class State { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    @Volatile var state: State = State.DISCONNECTED
        private set
    @Volatile var lastError: String? = null
        private set
    @Volatile var fbWidth: Int = 0
        private set
    @Volatile var fbHeight: Int = 0
        private set
    @Volatile var desktopName: String = ""
        private set

    private val bitmapRef = AtomicReference<Bitmap?>(null)
    private val frameVersion = AtomicReference(0L)

    fun currentBitmap(): Bitmap? = bitmapRef.get()
    fun frameId(): Long = frameVersion.get()

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private val running = AtomicBoolean(false)
    private var readerThread: Thread? = null

    var onFrame: (() -> Unit)? = null
    var onState: ((State, String?) -> Unit)? = null

    fun connect(timeoutMs: Int = 5000) {
        if (running.get()) return
        disconnect()
        state = State.CONNECTING
        lastError = null
        onState?.invoke(state, null)
        thread(name = "rfb-connect", isDaemon = true) {
            try {
                val s = Socket()
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), timeoutMs)
                s.soTimeout = 0
                socket = s
                input = DataInputStream(BufferedInputStream(s.getInputStream(), 64 * 1024))
                output = DataOutputStream(BufferedOutputStream(s.getOutputStream(), 8 * 1024))
                handshake()
                running.set(true)
                state = State.CONNECTED
                onState?.invoke(state, desktopName)
                startReader()
                requestFramebufferUpdate(incremental = false)
            } catch (e: Exception) {
                lastError = e.message ?: e.toString()
                state = State.ERROR
                onState?.invoke(state, lastError)
                disconnect()
            }
        }
    }

    fun disconnect() {
        running.set(false)
        try { readerThread?.interrupt() } catch (_: Exception) {}
        readerThread = null
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null
        output = null
        socket = null
        if (state != State.ERROR) {
            state = State.DISCONNECTED
            onState?.invoke(state, null)
        }
    }

    private fun handshake() {
        val inp = input ?: throw IOException("no input")
        val out = output ?: throw IOException("no output")

        // ProtocolVersion
        val serverVer = ByteArray(12)
        inp.readFully(serverVer)
        val verStr = String(serverVer, Charsets.US_ASCII)
        // Prefer 3.8
        out.write("RFB 003.008\n".toByteArray(Charsets.US_ASCII))
        out.flush()

        // Security types (3.7+)
        val nTypes = inp.readUnsignedByte()
        if (nTypes == 0) {
            val reasonLen = inp.readInt()
            val reason = ByteArray(reasonLen.coerceAtLeast(0).coerceAtMost(4096))
            if (reason.isNotEmpty()) inp.readFully(reason)
            throw IOException("RFB rejected: " + String(reason, Charsets.UTF_8))
        }
        val types = IntArray(nTypes) { inp.readUnsignedByte() }
        if (!types.contains(1)) {
            throw IOException("Server does not offer Security None (types=${types.toList()}). Start VNC with -SecurityTypes None")
        }
        out.writeByte(1) // None
        out.flush()

        // SecurityResult (3.8)
        val result = inp.readInt()
        if (result != 0) {
            val reasonLen = inp.readInt()
            val reason = ByteArray(reasonLen.coerceAtLeast(0).coerceAtMost(4096))
            if (reason.isNotEmpty()) inp.readFully(reason)
            throw IOException("Auth failed: " + String(reason, Charsets.UTF_8))
        }

        // ClientInit — shared
        out.writeByte(1)
        out.flush()

        // ServerInit
        fbWidth = inp.readUnsignedShort()
        fbHeight = inp.readUnsignedShort()
        // skip server pixel format (16 bytes) — we will set our own
        inp.skipBytes(16)
        val nameLen = inp.readInt().coerceAtLeast(0).coerceAtMost(1024)
        val nameBytes = ByteArray(nameLen)
        if (nameLen > 0) inp.readFully(nameBytes)
        desktopName = String(nameBytes, Charsets.UTF_8)

        if (fbWidth <= 0 || fbHeight <= 0 || fbWidth > 4096 || fbHeight > 4096) {
            throw IOException("Invalid framebuffer ${fbWidth}x${fbHeight}")
        }

        val bmp = Bitmap.createBitmap(fbWidth, fbHeight, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF000000.toInt())
        bitmapRef.set(bmp)

        setPixelFormat32()
        setEncodingsRaw()
    }

    private fun setPixelFormat32() {
        val out = output ?: return
        // SetPixelFormat (type 0)
        out.writeByte(0)
        out.writeByte(0)
        out.writeByte(0)
        out.writeByte(0)
        // bits-per-pixel, depth, big-endian, true-colour
        out.writeByte(32)
        out.writeByte(24)
        out.writeByte(0) // little endian
        out.writeByte(1) // true colour
        out.writeShort(255) // red-max
        out.writeShort(255) // green-max
        out.writeShort(255) // blue-max
        out.writeByte(16) // red-shift
        out.writeByte(8)  // green-shift
        out.writeByte(0)  // blue-shift
        out.writeByte(0)
        out.writeByte(0)
        out.writeByte(0)
        out.flush()
    }

    private fun setEncodingsRaw() {
        val out = output ?: return
        // SetEncodings (type 2)
        out.writeByte(2)
        out.writeByte(0)
        out.writeShort(1) // number of encodings
        out.writeInt(0) // Raw
        out.flush()
    }

    fun requestFramebufferUpdate(incremental: Boolean) {
        val out = output ?: return
        val w = fbWidth
        val h = fbHeight
        if (w <= 0 || h <= 0) return
        synchronized(out) {
            try {
                out.writeByte(3) // FramebufferUpdateRequest
                out.writeByte(if (incremental) 1 else 0)
                out.writeShort(0)
                out.writeShort(0)
                out.writeShort(w)
                out.writeShort(h)
                out.flush()
            } catch (_: Exception) {
            }
        }
    }

    fun sendPointerEvent(x: Int, y: Int, buttonMask: Int) {
        val out = output ?: return
        if (state != State.CONNECTED) return
        val cx = x.coerceIn(0, (fbWidth - 1).coerceAtLeast(0))
        val cy = y.coerceIn(0, (fbHeight - 1).coerceAtLeast(0))
        synchronized(out) {
            try {
                out.writeByte(5) // PointerEvent
                out.writeByte(buttonMask and 0xFF)
                out.writeShort(cx)
                out.writeShort(cy)
                out.flush()
            } catch (_: Exception) {
            }
        }
    }

    /** keysym: X11 keysym (e.g. 0x61 'a', 0xff0d Return) */
    fun sendKeyEvent(keysym: Int, down: Boolean) {
        val out = output ?: return
        if (state != State.CONNECTED) return
        synchronized(out) {
            try {
                out.writeByte(4) // KeyEvent
                out.writeByte(if (down) 1 else 0)
                out.writeByte(0)
                out.writeByte(0)
                out.writeInt(keysym)
                out.flush()
            } catch (_: Exception) {
            }
        }
    }

    private fun startReader() {
        readerThread = thread(name = "rfb-reader", isDaemon = true) {
            val inp = input ?: return@thread
            try {
                while (running.get()) {
                    val type = inp.readUnsignedByte()
                    when (type) {
                        0 -> handleFramebufferUpdate(inp)
                        1 -> { // SetColourMapEntries
                            inp.readUnsignedByte()
                            inp.readUnsignedShort()
                            val n = inp.readUnsignedShort()
                            inp.skipBytes(n * 6)
                        }
                        2 -> { // Bell — ignore
                        }
                        3 -> { // ServerCutText
                            inp.skipBytes(3)
                            val len = inp.readInt().coerceAtLeast(0).coerceAtMost(1_000_000)
                            inp.skipBytes(len)
                        }
                        else -> {
                            // Unknown — stop to avoid desync
                            throw IOException("Unknown RFB server message type $type")
                        }
                    }
                }
            } catch (e: Exception) {
                if (running.get()) {
                    lastError = e.message
                    state = State.ERROR
                    onState?.invoke(state, lastError)
                }
            } finally {
                running.set(false)
            }
        }
    }

    private fun handleFramebufferUpdate(inp: DataInputStream) {
        inp.readUnsignedByte() // padding
        val nRects = inp.readUnsignedShort()
        val bmp = bitmapRef.get() ?: return
        val canvas = AndroidCanvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = false }

        for (i in 0 until nRects) {
            val x = inp.readUnsignedShort()
            val y = inp.readUnsignedShort()
            val w = inp.readUnsignedShort()
            val h = inp.readUnsignedShort()
            val encoding = inp.readInt()
            when (encoding) {
                0 -> readRawRect(inp, bmp, x, y, w, h)
                -239 /* Cursor pseudo */ -> {
                    // cursor image + mask — skip for MVP
                    val pixels = w * h * 4
                    val mask = ((w + 7) / 8) * h
                    inp.skipBytes(pixels + mask)
                }
                -223 /* DesktopSize */ -> {
                    // new size already in x,y as width/height in some servers;
                    // RFB: x=width y=height for DesktopSize pseudo-encoding
                    if (w > 0 && h > 0 && (w != fbWidth || h != fbHeight)) {
                        fbWidth = w
                        fbHeight = h
                        val nb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        nb.eraseColor(0xFF000000.toInt())
                        bitmapRef.set(nb)
                        try { bmp.recycle() } catch (_: Exception) {}
                    }
                }
                else -> throw IOException("Unsupported encoding $encoding (only Raw in MVP)")
            }
        }
        frameVersion.updateAndGet { it + 1 }
        onFrame?.invoke()
        // continuous incremental updates
        requestFramebufferUpdate(incremental = true)
    }

    private fun readRawRect(
        inp: DataInputStream,
        bmp: Bitmap,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        if (w <= 0 || h <= 0) return
        val rowBytes = w * 4
        val row = ByteArray(rowBytes)
        val pixels = IntArray(w)
        for (rowIdx in 0 until h) {
            inp.readFully(row)
            var bi = 0
            for (col in 0 until w) {
                val b = row[bi].toInt() and 0xFF
                val g = row[bi + 1].toInt() and 0xFF
                val r = row[bi + 2].toInt() and 0xFF
                // skip alpha/pad byte
                bi += 4
                pixels[col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            val destY = y + rowIdx
            if (destY in 0 until bmp.height && x + w <= bmp.width) {
                bmp.setPixels(pixels, 0, w, x, destY, w, 1)
            }
        }
    }
}
