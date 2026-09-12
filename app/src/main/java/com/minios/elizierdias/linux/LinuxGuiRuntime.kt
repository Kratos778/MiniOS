/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.linux

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Arranca / para sessão gráfica TigerVNC dentro do Debian PRoot.
 * Cliente RFB nativo no Viewer (sem AVNC).
 */
class LinuxGuiRuntime(
    private val context: Context,
    private val runtime: LinuxRuntime,
) {

    data class GuiStatus(
        val running: Boolean,
        val display: String,
        val port: Int,
        val geometry: String,
        val message: String,
    )

    private val displayNum = 1
    val vncPort: Int get() = 5900 + displayNum
    val displayName: String get() = ":$displayNum"

    fun deviceGeometry(): String {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        var w = metrics.widthPixels
        var h = metrics.heightPixels
        if (h > w) {
            val t = w
            w = h
            h = t
        }
        w = w.coerceIn(800, 1920)
        h = h.coerceIn(480, 1200)
        w -= w % 2
        h -= h % 2
        return "${w}x${h}"
    }

    private fun rootHome(): File =
        File(LinuxConfig.rootfsDir(context), "root")

    private fun vncDir(): File =
        File(rootHome(), ".vnc").also { it.mkdirs() }

    /**
     * Desktop minimal mas utilizável:
     * - fundo sólido (não "vazio preto sem significado")
     * - openbox
     * - xterm grande com dicas
     */
    fun ensureXstartup(): String {
        val dir = vncDir()
        val startup = File(dir, "xstartup")
        val script =
            """
            #!/bin/sh
            unset SESSION_MANAGER
            unset DBUS_SESSION_BUS_ADDRESS
            export DISPLAY=:1
            export HOME=/root
            export USER=root
            [ -r "$$HOME/.Xresources" ] && xrdb "$$HOME/.Xresources"
            mkdir -p /sdcard/MiniOS/Documents /sdcard/MiniOS/Downloads /sdcard/MiniOS/Games 2>/dev/null
            mkdir -p "$$HOME/Documents" "$$HOME/Downloads" "$$HOME/Desktop" 2>/dev/null
            # Fundo visível (Openbox sozinho é preto “vazio”)
            if command -v xsetroot >/dev/null 2>&1; then
              xsetroot -solid "#1a2332"
            fi
            if command -v openbox >/dev/null 2>&1; then
              openbox &
            fi
            sleep 0.3
            # Terminal grande (quase ecrã inteiro)
            if command -v xterm >/dev/null 2>&1; then
              xterm -geometry 200x48+8+8 -fa Monospace -fs 11 -bg black -fg grey \-
                -ls -title "NoskOS Linux" -e bash -lc '
                  clear
                  echo "════════════════════════════════════"
                  echo "  NoskOS · Debian Linux (Openbox)"
                  echo "════════════════════════════════════"
                  echo ""
                  echo " Apps:  apt-get install -y falkon"
                  echo "        apt-get install -y pcmanfm"
                  echo "        apt-get search jogo"
                  echo " Abrir: falkon &"
                  echo "        pcmanfm &"
                  echo " Dados: /sdcard/MiniOS/"
                  echo ""
                  exec bash
                ' &
            fi
            # Manter sessão viva
            wait
            """.trimIndent()
                .replace("$$HOME", 