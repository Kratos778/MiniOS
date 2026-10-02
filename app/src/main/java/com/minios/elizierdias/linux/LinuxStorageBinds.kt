/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */
package com.minios.elizierdias.linux

import android.os.Environment
import java.io.File

/**
 * Montagens do armazenamento do telemóvel dentro do Debian (PRoot -b host:guest).
 * Precisa da permissão de acesso a todos os ficheiros no Android 11+.
 */
object LinuxStorageBinds {
    fun list(): List<Pair<String, String>> {
        val binds = mutableListOf<Pair<String, String>>()
        fun add(host: File?, guest: String) {
            if (host == null) return
            try {
                if (!host.exists()) host.mkdirs()
            } catch (_: Exception) {
            }
            if (host.exists()) binds += host.absolutePath to guest
        }
        add(Environment.getExternalStorageDirectory(), "/sdcard")
        add(File("/storage/emulated/0"), "/storage/emulated/0")
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "/sdcard/Download")
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "/sdcard/Documents")
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "/sdcard/Pictures")
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "/sdcard/DCIM")
        add(LinuxConfig.publicMiniOsDir(), "/sdcard/MiniOS")
        add(LinuxConfig.publicMiniOsDir(), "/minios")
        return binds
    }

    fun status(markerReady: Boolean): String = buildString {
        appendLine("Armazenamento unificado (Android -> Debian):")
        val binds = list()
        if (binds.isEmpty()) {
            appendLine("  nenhum caminho host (concede Acesso a todos os ficheiros)")
        } else {
            binds.forEach { (host, guest) -> appendLine("  $guest  <-  $host") }
        }
        appendLine("marker: " + if (markerReady) "ok" else "corre setup-storage")
    }

    fun prootArgs(): List<String> {
        val args = mutableListOf<String>()
        list().forEach { (host, guest) ->
            args += "-b"
            args += "$host:$guest"
        }
        return args
    }
}
