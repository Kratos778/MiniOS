package com.minios.elizierdias.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.minios.elizierdias.MainActivity
import com.minios.elizierdias.R
import com.minios.elizierdias.linux.ProcessTable
import com.minios.elizierdias.linux.RuntimeState
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground Service — ponto de entrada do Runtime Core.
 *
 * Fase actual: FGS fiavel + ProcessTable partilhada + estado Runtime.
 * Proximas fases: LinuxManager / VNC Supervisor vivem aqui (plano §8–9).
 */
class NoskKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NoskLog.event(NoskLog.SERVICE, "onCreate")
        processTable = ProcessTable()
        runtimeState.set(RuntimeState.STOPPED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NoskLog.event(NoskLog.SERVICE, "onStartCommand", detail = "flags=$flags startId=$startId")
        try {
            val notif = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIF_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIF_ID, notif)
            }
            NoskLog.event(NoskLog.SERVICE, "startForeground_ok")
            if (runtimeState.get() == RuntimeState.STOPPED) {
                runtimeState.set(RuntimeState.RUNNING)
                NoskLog.event(
                    NoskLog.RUNTIME,
                    "state",
                    from = RuntimeState.STOPPED.name,
                    to = RuntimeState.RUNNING.name,
                    detail = "service_up",
                )
            }
        } catch (e: Exception) {
            NoskLog.e(NoskLog.SERVICE, "FGS START FAILED: ${e.message}", e)
            runtimeState.set(RuntimeState.ERROR)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        NoskLog.event(NoskLog.SERVICE, "onTaskRemoved")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        NoskLog.event(
            NoskLog.SERVICE,
            "onDestroy",
            from = runtimeState.get().name,
            to = RuntimeState.STOPPED.name,
            detail = processTable?.snapshot()?.take(200),
        )
        runtimeState.set(RuntimeState.STOPPED)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channelId = "noskos_keepalive"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "NoskOS Runtime",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Mantem o Runtime Core activo"
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val state = runtimeState.get().name
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NoskOS Runtime")
            .setContentText("Estado: $state")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 7701

        /** ProcessTable do Service (null se Service nao arrancou). */
        @Volatile
        var processTable: ProcessTable? = null
            private set

        val runtimeState = AtomicReference(RuntimeState.STOPPED)

        fun start(context: Context) {
            val i = Intent(context, NoskKeepAliveService::class.java)
            try {
                NoskLog.event(NoskLog.SERVICE, "start_request")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            } catch (e: Exception) {
                NoskLog.e(NoskLog.SERVICE, "FGS START FAILED (start): ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            try {
                NoskLog.event(NoskLog.SERVICE, "stop_request")
                context.stopService(Intent(context, NoskKeepAliveService::class.java))
            } catch (e: Exception) {
                NoskLog.e(NoskLog.SERVICE, "stop failed: ${e.message}", e)
            }
        }
    }
}
