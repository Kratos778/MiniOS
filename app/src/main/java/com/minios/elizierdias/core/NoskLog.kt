package com.minios.elizierdias.core

import android.util.Log

/**
 * Logging central NoskOS — tags estaveis para adb logcat.
 *
 * adb logcat -s NOSKOS_RUNTIME:V NOSKOS_PROCESS:V NOSKOS_VNC:V NOSKOS_RFB:V NOSKOS_SERVICE:V NOSKOS_STORAGE:V
 */
object NoskLog {
    const val RUNTIME = "NOSKOS_RUNTIME"
    const val PROCESS = "NOSKOS_PROCESS"
    const val VNC = "NOSKOS_VNC"
    const val RFB = "NOSKOS_RFB"
    const val SERVICE = "NOSKOS_SERVICE"
    const val STORAGE = "NOSKOS_STORAGE"

    fun i(tag: String, msg: String) = Log.i(tag, msg)
    fun w(tag: String, msg: String) = Log.w(tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
    }
    fun d(tag: String, msg: String) = Log.d(tag, msg)

    fun event(
        tag: String,
        name: String,
        from: String? = null,
        to: String? = null,
        pid: Int? = null,
        exitCode: Int? = null,
        detail: String? = null,
    ) {
        val sb = StringBuilder(name)
        if (from != null) sb.append(" from=").append(from)
        if (to != null) sb.append(" to=").append(to)
        if (pid != null) sb.append(" pid=").append(pid)
        if (exitCode != null) sb.append(" exit=").append(exitCode)
        if (detail != null) sb.append(" ").append(detail)
        Log.i(tag, sb.toString())
    }
}
