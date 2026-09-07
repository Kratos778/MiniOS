/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
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
 * Geometria = resolução do ecrã do dispositivo (telefone/tablet).
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

    /** Resolução do ecrã físico (landscape MiniOS). */
    fun deviceGeometry(): String {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        var w = metrics.widthPixels
        var h = metrics.heightPixels
        // MiniOS está em landscape — usar o maior como largura
        if (h > w) {
            val t = w
            w = h
            h = t
        }
        // Limitar para não esmagar o telemóvel (VNC + RAM)
        w = w.coerceIn(800, 1920)
        h = h.coerceIn(480, 1200)
        // Múltiplo de 2 (X11)
        w -= w % 2
        h -= h % 2
        return "${w}x${h}"
    }

    private fun rootHome(): File =
        File(LinuxConfig.rootfsDir(context), "root")

    private fun vncDir(): File =
        File(rootHome(), ".vnc").also { it.mkdirs() }

    /** xstartup mínimo: openbox se existir, senão só xterm. */
    fun ensureXstartup(): String {
        val dir = vncDir()
        val startup = File(dir, "xstartup")
        val script = """#!/bin/sh
unset SESSION_MANAGER
unset DBUS_SESSION_BUS_ADDRESS
[ -r \$HOME/.Xresources ] && xrdb \$HOME/.Xresources
export DISPLAY=$displayName
if command -v openbox >/dev/null 2>&1; then
  openbox &
fi
exec xterm -geometry 100x30 -ls -title "MiniOS Linux"
""".trimIndent()
        startup.writeText(script)
        startup.setExecutable(true, false)
        return startup.absolutePath
    }

    /** Password VNC vazia / SecurityTypes None para localhost. */
    fun ensureVncPasswd(): String {
        // TigerVNC: ficheiro passwd; com -SecurityTypes None não é obrigatório
        val passwd = File(vncDir(), "passwd")
        return passwd.absolutePath
    }

    suspend fun status(): GuiStatus = withContext(Dispatchers.IO) {
        val geo = deviceGeometry()
        if (!runtime.isRootFsReady() || !runtime.isProotInstalled()) {
            return@withContext GuiStatus(
                false, displayName, vncPort, geo,
                "RootFS/PRoot não pronto",
            )
        }
        val r = runtime.exec(
            "vncserver -list 2>/dev/null || true",
            timeoutSec = 15,
        )
        val out = r.getOrNull()?.stdout.orEmpty()
        val running = out.contains(":$displayNum") || out.contains("590$displayNum")
        GuiStatus(
            running = running,
            display = displayName,
            port = vncPort,
            geometry = geo,
            message = if (running) "VNC ativo em $displayName (porta $vncPort)" else "VNC parado",
        )
    }

    /**
     * Inicia Xvnc + xterm (e openbox se instalado).
     * @param geometry override; null = ecrã do dispositivo
     */
    suspend fun start(geometry: String? = null): Result<GuiStatus> =
        withContext(Dispatchers.IO) {
            try {
                if (!runtime.isRootFsReady()) {
                    return@withContext Result.failure(
                        IllegalStateException("RootFS not ready"),
                    )
                }
                runtime.ensureDns()
                ensureXstartup()
                ensureVncPasswd()
                val geo = geometry ?: deviceGeometry()

                // Matar sessão anterior no mesmo display
                runtime.exec("vncserver -kill $displayName 2>/dev/null || true", timeoutSec = 20)

                // SecurityTypes None = só localhost, sem password (OK dentro do APK)
                val cmd = buildString {
                    append("export HOME=/root USER=root; ")
                    append("vncserver $displayName ")
                    append("-geometry $geo -depth 24 ")
                    append("-localhost yes ")
                    append("-SecurityTypes None ")
                    append("-xstartup /root/.vnc/xstartup ")
                    append("2>&1")
                }
                val r = runtime.exec(cmd, timeoutSec = 45)
                val body = buildString {
                    r.getOrNull()?.let {
                        if (it.stdout.isNotBlank()) appendLine(it.stdout)
                        if (it.stderr.isNotBlank()) appendLine(it.stderr)
                    }
                    r.exceptionOrNull()?.let { appendLine(it.message) }
                }.trim()

                val st = status()
                if (st.running || body.contains("New") || body.contains("desktop is")) {
                    Result.success(
                        st.copy(
                            running = true,
                            geometry = geo,
                            message = "VNC $displayName $geo\n$body",
                        ),
                    )
                } else {
                    Result.failure(
                        IllegalStateException("Falha ao iniciar VNC:\n$body"),
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun stop(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val r = runtime.exec(
                "vncserver -kill $displayName 2>&1 || true",
                timeoutSec = 20,
            )
            val body = r.getOrNull()?.stdout.orEmpty() +
                r.getOrNull()?.stderr.orEmpty()
            Result.success(body.ifBlank { "VNC $displayName parado" })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Workaround dbus postinst (statoverride Operation not permitted no PRoot).
     */
    suspend fun fixDbusStatoverride(): Result<String> = withContext(Dispatchers.IO) {
        val cmd = """
            mkdir -p /var/lib/dpkg
            touch /var/lib/dpkg/statoverride 2>/dev/null || true
            DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>&1 || true
            echo DONE
        """.trimIndent()
        val r = runtime.exec(cmd, timeoutSec = 120)
        val body = buildString {
            r.getOrNull()?.let {
                appendLine(it.stdout)
                appendLine(it.stderr)
            }
        }.trim()
        Result.success(body)
    }
}
