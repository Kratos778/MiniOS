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

    /** Menu do botao direito Openbox — Terminal / Browser / Fechar. */
    private fun ensureOpenboxMenu() {
        val dir = File(rootHome(), ".config/openbox").also { it.mkdirs() }
        val menu = File(dir, "menu.xml")
        menu.writeText(
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
                |  <theme><name>Clearlooks</name></theme>
                |  <keyboard>
                |    <keybind key="A-F4"><action name="Close"/></keybind>
                |    <keybind key="A-Tab"><action name="NextWindow"/></keybind>
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

    fun ensureXstartup(): String {
        val dir = vncDir()
        val startup = File(dir, "xstartup")
        ensureOpenboxMenu()
        val script =
            "#!/bin/sh\n" +
                "unset SESSION_MANAGER\n" +
                "unset DBUS_SESSION_BUS_ADDRESS\n" +
                "export DISPLAY=:1\n" +
                "export HOME=/root\n" +
                "export USER=root\n" +
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

    fun ensureVncPasswd(): String {
        val passwd = File(vncDir(), "passwd")
        return passwd.absolutePath
    }

    suspend fun ensurePersistentDirs(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cmd =
                "mkdir -p /sdcard/MiniOS/Documents /sdcard/MiniOS/Downloads /sdcard/MiniOS/Games " +
                    "/root/Documents /root/Downloads /root/Desktop 2>&1; " +
                    "ln -sfn /sdcard/MiniOS/Documents /root/Documents/MiniOS 2>/dev/null || true; " +
                    "ln -sfn /sdcard/MiniOS/Downloads /root/Downloads/MiniOS 2>/dev/null || true; " +
                    "ln -sfn /sdcard/MiniOS/Games /root/Desktop/Games 2>/dev/null || true; " +
                    "echo OK"
            val r = runtime.exec(cmd, timeoutSec = 20)
            val body = r.getOrNull()?.stdout.orEmpty() + r.getOrNull()?.stderr.orEmpty()
            Result.success(body.ifBlank { "Pastas persistentes OK" })
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
        runtime.exec(
            "rm -f /tmp/.X${displayNum}-lock /tmp/.X11-unix/X${displayNum} " +
                "/root/.vnc/*.pid /root/.vnc/localhost:${displayNum}.pid 2>/dev/null || true",
            timeoutSec = 10,
        )
        runtime.exec("vncserver -kill $displayName 2>/dev/null || true", timeoutSec = 15)
    }

    suspend fun ensureGuiPackages(onProgress: ((String) -> Unit)? = null): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (!runtime.isRootFsReady() || !runtime.isProotInstalled()) {
                    return@withContext Result.failure(
                        IllegalStateException("RootFS/PRoot nao pronto"),
                    )
                }
                onProgress?.invoke("[GUI] A verificar pacotes...")
                if (isVncInstalled()) {
                    onProgress?.invoke("[GUI] TigerVNC ja instalado")
                    ensurePersistentDirs()
                    return@withContext Result.success("[GUI] TigerVNC ja instalado")
                }

                runtime.ensureDns()
                onProgress?.invoke("[GUI] apt update + instalar tigervnc openbox xterm...")

                val pkgs =
                    "tigervnc-standalone-server tigervnc-common openbox xterm x11-xserver-utils"
                runtime.exec(
                    "DEBIAN_FRONTEND=noninteractive apt-get update -y",
                    timeoutSec = 300,
                )
                val install = runtime.exec(
                    "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends $pkgs",
                    timeoutSec = 600,
                )

                val body = buildString {
                    install.getOrNull()?.let {
                        if (it.stdout.isNotBlank()) appendLine(it.stdout.takeLast(800))
                        if (it.stderr.isNotBlank()) appendLine(it.stderr.takeLast(400))
                    }
                    install.exceptionOrNull()?.let { appendLine(it.message) }
                }.trim()

                ensurePersistentDirs()

                if (isVncInstalled()) {
                    onProgress?.invoke("[GUI] OK")
                    Result.success("[GUI] Pacotes OK\n$body")
                } else {
                    Result.failure(IllegalStateException("Falha GUI.\n$body"))
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
                val r = runtime.exec(
                    "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends falkon",
                    timeoutSec = 600,
                )
                val body = buildString {
                    r.getOrNull()?.let {
                        if (it.stdout.isNotBlank()) appendLine(it.stdout.takeLast(800))
                        if (it.stderr.isNotBlank()) appendLine(it.stderr.takeLast(400))
                    }
                }.trim()
                val check = runtime.exec(
                    "command -v falkon >/dev/null 2>&1 && echo YES || echo NO",
                    timeoutSec = 10,
                )
                if (check.getOrNull()?.stdout?.trim() == "YES") {
                    Result.success("[GUI] Falkon OK\n$body")
                } else {
                    Result.failure(IllegalStateException("Falkon falhou.\n$body"))
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
        val hasDisplay = out.contains(":$displayNum") || out.lines().any {
            it.trim().startsWith("$displayNum") || it.contains(" $displayNum\t")
        }
        val running = hasDisplay && !hasStale
        GuiStatus(
            running = running,
            display = displayName,
            port = vncPort,
            geometry = geo,
            message = when {
                hasStale -> "VNC stale — Iniciar"
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

                val cmd = buildString {
                    append("export HOME=/root USER=root; ")
                    append("vncserver $displayName ")
                    append("-geometry $geo -depth 24 ")
                    append("-localhost yes ")
                    append("-SecurityTypes None ")
                    append("-xstartup /root/.vnc/xstartup ")
                    append("2>&1")
                }
                val r = runtime.exec(cmd, timeoutSec = 60)
                val body = buildString {
                    r.getOrNull()?.let {
                        if (it.stdout.isNotBlank()) appendLine(it.stdout)
                        if (it.stderr.isNotBlank()) appendLine(it.stderr)
                    }
                }.trim()

                Thread.sleep(1000)

                val st = status()
                if (st.running || body.contains("New") || body.contains("on port")) {
                    Result.success(
                        st.copy(running = true, geometry = geo, message = "VNC $displayName $geo\n$body"),
                    )
                } else {
                    Result.failure(IllegalStateException("Falha VNC:\n$body"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun stop(): Result<String> = withContext(Dispatchers.IO) {
        try {
            cleanStaleLocks()
            val r = runtime.exec("vncserver -kill $displayName 2>&1 || true", timeoutSec = 20)
            val body = r.getOrNull()?.stdout.orEmpty() + r.getOrNull()?.stderr.orEmpty()
            Result.success(body.ifBlank { "VNC parado" })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun runApp(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val st = status()
            if (!st.running) {
                return@withContext Result.failure(IllegalStateException("VNC nao activo"))
            }
            val safe = command.trim()
            if (safe.isEmpty() || safe.length > 300) {
                return@withContext Result.failure(IllegalStateException("Comando invalido"))
            }
            val cmd =
                "export DISPLAY=$displayName HOME=/root USER=root; " +
                    "nohup $safe >/tmp/noskos-app.log 2>&1 & echo PID:$!"
            val r = runtime.exec(cmd, timeoutSec = 20)
            val body = buildString {
                r.getOrNull()?.let {
                    if (it.stdout.isNotBlank()) appendLine(it.stdout)
                    if (it.stderr.isNotBlank()) appendLine(it.stderr)
                }
            }.trim()
            Result.success("Lancado: $safe\n$body")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fixDbusStatoverride(): Result<String> = withContext(Dispatchers.IO) {
        val cmd =
            "mkdir -p /var/lib/dpkg; touch /var/lib/dpkg/statoverride 2>/dev/null || true; " +
                "DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>&1 || true; echo DONE"
        val r = runtime.exec(cmd, timeoutSec = 120)
        Result.success(r.getOrNull()?.stdout.orEmpty())
    }
}
