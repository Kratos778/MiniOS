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
 * Geometria = resolução do ecrã do dispositivo (telefone/tablet).
 *
 * Fluxo recomendado:
 * 1. ensureGuiPackages()  → instala tigervnc + openbox + xterm se faltarem
 * 2. start()              → sobe Xvnc + openbox
 * 3. Cliente VNC nativo (próximo passo) mostra o ecrã dentro de uma janela NoskOS
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

    /** Resolução do ecrã físico (landscape NoskOS). */
    fun deviceGeometry(): String {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        var w = metrics.widthPixels
        var h = metrics.heightPixels
        // NoskOS está em landscape — usar o maior como largura
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

    /** xstartup mínimo e leve: openbox se existir, senão só xterm. */
    fun ensureXstartup(): String {
        val dir = vncDir()
        val startup = File(dir, "xstartup")
        val homeRef = "${'$'}HOME"
        val script =
            "#!/bin/sh\n" +
                "unset SESSION_MANAGER\n" +
                "unset DBUS_SESSION_BUS_ADDRESS\n" +
                "[ -r $homeRef/.Xresources ] && xrdb $homeRef/.Xresources\n" +
                "export DISPLAY=$displayName\n" +
                "if command -v openbox >/dev/null 2>&1; then\n" +
                "  openbox &\n" +
                "fi\n" +
                "exec xterm -geometry 100x30 -ls -title \"NoskOS Linux\"\n"
        startup.writeText(script)
        startup.setExecutable(true, false)
        return startup.absolutePath
    }

    fun ensureVncPasswd(): String {
        val passwd = File(vncDir(), "passwd")
        return passwd.absolutePath
    }

    /** Verifica se o binário vncserver existe no rootfs. */
    suspend fun isVncInstalled(): Boolean = withContext(Dispatchers.IO) {
        val r = runtime.exec("command -v vncserver >/dev/null 2>&1 && echo YES || echo NO", timeoutSec = 15)
        r.getOrNull()?.stdout?.trim() == "YES"
    }

    /**
     * Instala os pacotes mínimos para GUI leve (ideal para ~2GB RAM).
     * tigervnc-standalone-server + openbox + xterm
     */
    suspend fun ensureGuiPackages(onProgress: ((String) -> Unit)? = null): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (!runtime.isRootFsReady() || !runtime.isProotInstalled()) {
                    return@withContext Result.failure(
                        IllegalStateException("RootFS/PRoot não pronto"),
                    )
                }
                onProgress?.invoke("[GUI] A verificar pacotes VNC...")
                if (isVncInstalled()) {
                    val msg = "[GUI] TigerVNC já instalado"
                    onProgress?.invoke(msg)
                    return@withContext Result.success(msg)
                }

                runtime.ensureDns()
                onProgress?.invoke("[GUI] A instalar tigervnc + openbox + xterm (pode demorar)...")

                val pkgs = "tigervnc-standalone-server tigervnc-common openbox xterm"
                val update = runtime.exec(
                    "DEBIAN_FRONTEND=noninteractive apt-get update -y",
                    timeoutSec = 300,
                )
                val install = runtime.exec(
                    "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends $pkgs",
                    timeoutSec = 600,
                )

                val body = buildString {
                    update.getOrNull()?.let {
                        if (it.stdout.isNotBlank()) appendLine(it.stdout.takeLast(800))
                        if (it.stderr.isNotBlank()) appendLine(it.stderr.takeLast(400))
                    }
                    install.getOrNull()?.let {
                        if (it.stdout.isNotBlank()) appendLine(it.stdout.takeLast(1200))
                        if (it.stderr.isNotBlank()) appendLine(it.stderr.takeLast(600))
                    }
                    install.exceptionOrNull()?.let { appendLine(it.message) }
                }.trim()

                val ok = isVncInstalled()
                if (ok) {
                    val msg = "[GUI] Pacotes instalados com sucesso.\n$body"
                    onProgress?.invoke("[GUI] OK — TigerVNC pronto")
                    Result.success(msg)
                } else {
                    Result.failure(
                        IllegalStateException(
                            "Falha ao instalar pacotes GUI.\n$body\n\n" +
                                "Tenta no Terminal:\n" +
                                "apt-get update && apt-get install -y tigervnc-standalone-server openbox xterm",
                        ),
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun status(): GuiStatus = withContext(Dispatchers.IO) {
        val geo = deviceGeometry()
        if (!runtime.isRootFsReady() || !runtime.isProotInstalled()) {
            return@withContext GuiStatus(
                false, displayName, vncPort, geo,
                "RootFS/PRoot não pronto",
            )
        }
        if (!isVncInstalled()) {
            return@withContext GuiStatus(
                false, displayName, vncPort, geo,
                "TigerVNC não instalado — usa \"Instalar pacotes\" ou Terminal",
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
            message = if (running) {
                "VNC ativo em $displayName (porta $vncPort)"
            } else {
                "VNC parado"
            },
        )
    }

    /**
     * Inicia Xvnc + openbox/xterm.
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
                if (!isVncInstalled()) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "vncserver não encontrado.\n" +
                                "Instala os pacotes primeiro (botão ou Terminal):\n" +
                                "apt-get install -y tigervnc-standalone-server openbox xterm",
                        ),
                    )
                }

                runtime.ensureDns()
                ensureXstartup()
                ensureVncPasswd()
                val geo = geometry ?: deviceGeometry()

                // Matar sessão anterior no mesmo display
                runtime.exec("vncserver -kill $displayName 2>/dev/null || true", timeoutSec = 20)

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
                    r.exceptionOrNull()?.let { appendLine(it.message) }
                }.trim()

                // Pequena pausa para o processo estabilizar
                Thread.sleep(800)

                val st = status()
                if (st.running || body.contains("New") || body.contains("desktop is") || body.contains("started")) {
                    Result.success(
                        st.copy(
                            running = true,
                            geometry = geo,
                            message = "VNC $displayName $geo\n$body",
                        ),
                    )
                } else {
                    Result.failure(
                        IllegalStateException(
                            "Falha ao iniciar VNC:\n$body\n\n" +
                                "Dicas:\n" +
                                "• Verifica logs: cat /root/.vnc/*.log\n" +
                                "• Garante que openbox e xterm estão instalados",
                        ),
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
        val cmd =
            "mkdir -p /var/lib/dpkg; " +
                "touch /var/lib/dpkg/statoverride 2>/dev/null || true; " +
                "DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>&1 || true; " +
                "echo DONE"
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
