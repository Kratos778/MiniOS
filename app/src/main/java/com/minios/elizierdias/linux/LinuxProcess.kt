/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */
package com.minios.elizierdias.linux

import com.minios.elizierdias.core.NoskLog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Handle real de um processo iniciado via LinuxRuntime / ProcessBuilder.
 * Substitui o stub anterior (plano §10).
 */
class LinuxProcessHandle(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val command: String,
    private val process: Process?,
    val startTimeMs: Long = System.currentTimeMillis(),
) {
    private val stateRef = AtomicReference(ProcessState.CREATED)
    private val exitRef = AtomicInteger(Int.MIN_VALUE)
    private val stdoutBuf = StringBuilder()
    private val stderrBuf = StringBuilder()

    var state: ProcessState
        get() = stateRef.get()
        private set(v) { stateRef.set(v) }

    val exitCode: Int?
        get() {
            val e = exitRef.get()
            return if (e == Int.MIN_VALUE) null else e
        }

    val pid: Int
        get() = try {
            process?.pid()?.toInt() ?: -1
        } catch (_: Throwable) {
            -1
        }

    val stdoutSnapshot: String
        get() = synchronized(stdoutBuf) { stdoutBuf.toString() }

    val stderrSnapshot: String
        get() = synchronized(stderrBuf) { stderrBuf.toString() }

    fun markStarting() {
        state = ProcessState.STARTING
    }

    fun markRunning() {
        state = ProcessState.RUNNING
        NoskLog.event(NoskLog.PROCESS, "running", to = "RUNNING", pid = pid, detail = "role=$role")
    }

    fun markExited(code: Int) {
        exitRef.set(code)
        state = ProcessState.EXITED
    }

    fun isAlive(): Boolean {
        val p = process ?: return false
        return try {
            p.isAlive
        } catch (_: Exception) {
            false
        }
    }

    fun attachStreamReaders() {
        val p = process ?: return
        Thread({
            try {
                BufferedReader(InputStreamReader(p.inputStream)).use { r ->
                    var line = r.readLine()
                    while (line != null) {
                        synchronized(stdoutBuf) {
                            if (stdoutBuf.length < 64_000) stdoutBuf.appendLine(line)
                        }
                        line = r.readLine()
                    }
                }
            } catch (_: Exception) {
            }
        }, "noskos-out-$role").apply { isDaemon = true; start() }

        Thread({
            try {
                BufferedReader(InputStreamReader(p.errorStream)).use { r ->
                    var line = r.readLine()
                    while (line != null) {
                        synchronized(stderrBuf) {
                            if (stderrBuf.length < 64_000) stderrBuf.appendLine(line)
                        }
                        line = r.readLine()
                    }
                }
            } catch (_: Exception) {
            }
        }, "noskos-err-$role").apply { isDaemon = true; start() }
    }

    fun waitFor(timeoutSec: Long): Int {
        val p = process ?: return -1
        val finished = p.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            destroyForcibly()
            markExited(-9)
            return -9
        }
        val code = p.exitValue()
        markExited(code)
        return code
    }

    fun destroyForcibly() {
        try {
            process?.destroyForcibly()
        } catch (_: Exception) {
        }
        if (state == ProcessState.RUNNING || state == ProcessState.STARTING) {
            state = ProcessState.KILLED
            exitRef.compareAndSet(Int.MIN_VALUE, -9)
        }
        NoskLog.event(NoskLog.PROCESS, "killed", pid = pid, detail = "role=$role")
    }
}

/**
 * Factory helpers — processos de longa duracao devem ser registados na ProcessTable.
 */
object LinuxProcessFactory {
    fun fromJavaProcess(
        role: String,
        command: String,
        process: Process,
        table: ProcessTable? = null,
    ): LinuxProcessHandle {
        val h = LinuxProcessHandle(
            role = role,
            command = command,
            process = process,
        )
        h.markStarting()
        h.attachStreamReaders()
        h.markRunning()
        table?.put(h)
        return h
    }
}
