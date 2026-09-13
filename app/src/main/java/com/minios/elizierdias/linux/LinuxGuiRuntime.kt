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

    private fun ensureOpenboxMenu() {
        val dir = File(rootHome(), ".config/openbox").also { it.mkdirs() }
        File(dir, "menu.xml").writeText(
            """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<openbox_menu>
            |<menu id="root-menu" label="NoskOS">
            |  <item label="Terminal"><action name="Execute"><command>xterm -geometry 200x45 -ls</command></action></item>
            |  <item label="Browser Falkon"><action name="Execute"><command>falkon</command></action></item>
            |  <item label="Files"><action name="Execute"><command>pcmanfm</command></action></item>
            |  <separator/>
            |  <item label="Reconfigure"><action name="Reconfigure"/></item>
            |  <item label="Exit Openbox"><action name="Exit"/></item>
            |</menu>
            |</openbox_menu>
            """.trimMargin(),
        )
        val rc = File(dir, "rc.xml")
        if (!rc.exists()) {
            rc.writeText(
                """
                |<?xml version="1.0" encoding="UTF-8"?>
                |<openbox_config>
                |  <keyboard>
                |    <keybind key="A-F4"><action name="Close"/></keybind>
                |  </keyboard>
                |  <mouse>
                |    <context name="Desktop">
                |      <mousebind button="Right" action="Press">
                |        <action name="ShowMenu"><menu>root-menu</menu></action>
                |      </mousebind>
                |    </context>
                |  </mouse>
                |</openbox_config>
                """.trimMargin(),
            )
        }
    }

    /** Script real no PATH — nao depende de heredoc no Terminal. */
    private fun ensureStartVncScript(geo: String) {
        val bin = File(LinuxConfig.rootfsDir(context), "usr/local/bin").also { it.mkdirs() }
        val f = File(bin, "start-vnc")
        f.writeText(
            """
            |#!/bin/sh
            |export HOME=/root USER=root DISPLAY=:1
            |GEO="${geo}"
            |vncserver -kill :1 2>/dev/null || true
            |rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 /root/.vnc/*.pid 2>/dev/null || true
            |pkill -9 Xtigervnc 2>/dev/null || true
            |pkill -9 Xvnc 2>/dev/null || true
            |sleep 0.5
            |vncserver :1 -geometry "\$GEO" -depth 24 -localhost yes -SecurityTypes None -xstartup /root/.vnc/xstartup
            |vncserver -list
            """.trimMargin(),
        )
        f.setExecutable(true, false)
    }

    fun ensureXstartup(): String {
        val dir = vncDir()
        val startup = File(dir, "xstartup")
        ensureOpenboxMenu()
        val geo = deviceGeometry()
        ensureStartVncScript(geo)
        val script =
            "#!/bin/sh\n" +
                "unset SESSION_MANAGER\n" +
                "unset DBUS_SESSION_BUS_ADDRESS\n" +
                "export DISPLAY=:1 HOME=/root USER=root\n" +
                "mkdir -p /sdcard/MiniOS/Documents /sdcard/MiniOS/Downloads /sdcard/MiniOS/Games 2>/dev/null\n" +
                "mkdir -p /root/Documents /root/Downloads /root/Desktop 2>/dev/null\n" +
                "command -v xsetroot >/dev/null 2>&1 && xsetroot -solid '#1a2332'\n" +
                "command -v openbox >/dev/null 2>&1 && openbox &\n" +
                "sleep 0.5\n" +
                "command -v xterm >/dev/null 2>&1 && " +
                "xterm -geometry 200x45+0+0 -fa Monospace -fs 12 -bg black -fg grey -ls -title NoskOS &\n" +
                "wait\n"
        startup.writeText(script)
        startup.setExecutable(true, false)
        return startup.absolutePath
    }

    fun ensureVncPasswd(): String = File(vncDir(), "passwd").absolutePath

    suspend fun ensurePersistentDirs(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cmd =
                "mkdir -p /sdcard/MiniOS/Documents /sdcard/MiniOS/Downloads /sdcard/MiniOS/Games " +
                    "/root/Documents /root/Downloads /root/Desktop 2>&1; echo OK"
            val r = runtime.exec(cmd, timeoutSec = 20)
            Result.success(r.getOrNull()?.stdout.orEmpty().ifBlank { "OK" })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isVncInstalled(): Boolean = withContext(Dispatchers.IO) {
        val r = runtime.exec(
            "command -v vncserver >/dev/null 2>&1 && echo YES || echo NO",
            timeoutSec = 15,
        )
        r.getOrNull()?.stdout?.trim() == "YES"
    }

    private suspend fun cleanStaleLocks() {
        // Reset duro — “already running” com lista vazia
        runtime.exec(
            "vncserver -kill :1 2>/dev/null; " +
                "pkill -9 -f Xtigervnc 2>/dev/null; " +
                "pkill -9 -f 'Xvnc :1' 2>/dev/null; " +
                "rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 " +
                "/root/.vnc/*.pid /root/.vnc/localhost:1.pid " +
                "/tmp/.X11-unix/X1 2>/dev/null; " +
                "sleep 0.3; echo cleaned",
            timeoutSec = 20,
        )
    }

    suspend fun ensureGuiPackages(onProgress: ((String) -> Unit)? = null): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (!runtime.isRootFsReady() || !runtime.isProotInstalled()) {
                    return@withContext Result.failure(IllegalStateException("RootFS/PRoot nao pronto"))
                }
                onProgress?.invoke("[GUI] A verificar pacotes...")
                if (isVncInstalled()) {
                    ensurePersistentDirs()
                    ensureXstartup()
                    return@withContext Result.success("[GUI] TigerVNC ja instalado")
                }
                runtime.ensureDns()
                val pkgs =
                    "tigervnc-standalone-server tigervnc-common openbox xterm x11-xserver-utils"
                runtime.exec("DEBIAN_FRONTEND=noninteractive apt-get update -y", timeoutSec = 300)
                val install = runtime.exec(
                    "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends $pkgs",
                    timeoutSec = 600,
                )
                ensurePersistentDirs()
                ensureXstartup()
                if (isVncInstalled()) {
                    Result.success("[GUI] Pacotes OK")
                } else {
                    Result.failure(IllegalStateException("Falha GUI: ${install.exceptionOrNull()?.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun ensureLightBrowser(onProgress: ((String) -> Unit)? = null): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                onProgress?.invoke("[GUI] A instalar Falkon...")
                runtime.ensureDns()
                runtime.exec(
                    "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends falkon",
                    timeoutSec = 600,
                )
                val check = runtime.exec(
                    "command -v falkon >/dev/null 2>&1 && echo YES || echo NO",
                    timeoutSec = 10,
                )
                if (check.getOrNull()?.stdout?.trim() == "YES") {
                    Result.success("[GUI] Falkon OK")
                } else {
                    Result.failure(IllegalStateException("Falkon falhou"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun status(): GuiStatus = withContext(Dispatchers.IO) {
        val geo = deviceGeometry()
        if (!runtime.isRootFsReady() || !runtime.isProotInstalled()) {
            return@withContext GuiStatus(false, displayName, vncPort, geo, "RootFS/PRoot nao pronto")
        }
        if (!isVncInstalled()) {
            return@withContext GuiStatus(false, displayName, vncPort, geo, "TigerVNC nao instalado")
        }
        val r = runtime.exec("vncserver -list 2>/dev/null || true", timeoutSec = 15)
        val out = r.getOrNull()?.stdout.orEmpty()
        val hasStale = out.contains("stale", ignoreCase = true)
        val hasDisplay = Regex("""(?m)^\s*1\s+""").containsMatchIn(out) ||
            out.contains(":$displayNum")
        val running = hasDisplay && !hasStale
        GuiStatus(
            running = running,
            display = displayName,
            port = vncPort,
            geometry = geo,
            message = when {
                hasStale || (out.contains("already") && !hasDisplay) ->
                    "VNC stale — toca Iniciar"
                running -> "VNC ativo $displayName ($vncPort)"
                else -> "VNC parado"
            },
        )
    }

    suspend fun start(geometry: String? = null): Result<GuiStatus> =
        withContext(Dispatchers.IO) {
            try {
                if (!runtime.isRootFsReady()) {
                    return@withContext Result.failure(IllegalStateException("RootFS not ready"))
                }
                if (!isVncInstalled()) {
                    return@withContext Result.failure(
                        IllegalStateException("vncserver nao encontrado. Pacotes primeiro."),
                    )
                }
                runtime.ensureDns()
                ensurePersistentDirs()
                ensureXstartup()
                ensureVncPasswd()
                cleanStaleLocks()
                val geo = geometry ?: deviceGeometry()
                ensureStartVncScript(geo)

                val cmd =
                    "export HOME=/root USER=root; " +
                        "vncserver $displayName -geometry $geo -depth 24 " +
                        "-localhost yes -SecurityTypes None " +
                        "-xstartup /root/.vnc/xstartup 2>&1"
                val r = runtime.exec(cmd, timeoutSec = 60)
                val body = buildString {
                    r.getOrNull()?.let {
                        if (it.stdout.isNotBlank()) appendLine(it.stdout)
                        if (it.stderr.isNotBlank()) appendLine(it.stderr)
                    }
                }.trim()

                Thread.sleep(1200)
                val st = status()
                if (st.running || body.contains("New") || body.contains("on port")) {
                    Result.success(
                        st.copy(running = true, geometry = geo, message = "VNC OK $geo\n$body"),
                    )
                } else {
                    // segunda tentativa apos limpeza
                    cleanStaleLocks()
                    val r2 = runtime.exec(cmd, timeoutSec = 60)
                    val body2 = r2.getOrNull()?.stdout.orEmpty()
                    Thread.sleep(1000)
                    val st2 = status()
                    if (st2.running || body2.contains("New") || body2.contains("on port")) {
                        Result.success(st2.copy(running = true, geometry = geo, message = body2))
                    } else {
                        Result.failure(IllegalStateException("Falha VNC:\n$body\n$body2"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun stop(): Result<String> = withContext(Dispatchers.IO) {
        try {
            cleanStaleLocks()
            Result.success("VNC parado")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun runApp(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val st = status()
            if (!st.running) {
                return@withContext Result.failure(IllegalStateException("VNC nao activo — Iniciar primeiro"))
            }
            val safe = command.trim()
            if (safe.isEmpty() || safe.length > 300) {
                return@withContext Result.failure(IllegalStateException("Comando invalido"))
            }
            val cmd =
                "export DISPLAY=:1 HOME=/root USER=root; " +
                    "nohup $safe >/tmp/noskos-app.log 2>&1 & echo PID:\$!; sleep 0.5; " +
                    "tail -5 /tmp/noskos-app.log 2>/dev/null || true"
            val r = runtime.exec(cmd, timeoutSec = 25)
            val body = r.getOrNull()?.stdout.orEmpty() + r.getOrNull()?.stderr.orEmpty()
            Result.success("Lancado: $safe\n$body")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fixDbusStatoverride(): Result<String> = withContext(Dispatchers.IO) {
        val r = runtime.exec(
            "mkdir -p /var/lib/dpkg; touch /var/lib/dpkg/statoverride; " +
                "DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>&1 || true; echo DONE",
            timeoutSec = 120,
        )
        Result.success(r.getOrNull()?.stdout.orEmpty())
    }
}
