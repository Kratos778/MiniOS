/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
 *
 * This file is part of MiniOS.
 * Licensed under the MIT License. See LICENSE for details.
 *
 * All rights reserved under applicable law (Republic of Angola & international treaties),
 * subject to the terms of the MIT License.
 */

package com.minios.elizierdias.linux

/**
 * Registry of Linux applications that can be launched from the MiniOS desktop.
 *
 * Later these will be discovered from .desktop files inside the RootFS
 * or registered manually (VS Code, Chromium, etc.).
 *
 * They will appear alongside the native MiniOS apps in the Start Menu / Desktop.
 */
object LinuxAppRegistry {

    data class LinuxApp(
        val id: String,
        val name: String,
        val executable: String,
        val iconName: String? = null,
        val categories: List<String> = emptyList(),
        val terminal: Boolean = false,
    )

    private val apps = mutableListOf<LinuxApp>()

    fun register(app: LinuxApp) {
        if (apps.none { it.id == app.id }) {
            apps.add(app)
        }
    }

    fun unregister(id: String) {
        apps.removeAll { it.id == id }
    }

    fun all(): List<LinuxApp> = apps.toList()

    fun byId(id: String): LinuxApp? = apps.firstOrNull { it.id == id }

    /** Clear all registered Linux apps (e.g. on RootFS change) */
    fun clear() {
        apps.clear()
    }
}
