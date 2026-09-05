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
 * Future package manager bridge for the Linux environment
 * (apt / dpkg for Debian, apk for Alpine, etc.).
 *
 * First target: Debian ARM64 with apt.
 */
class LinuxPackageManager {

    data class PackageInfo(
        val name: String,
        val version: String,
        val description: String = "",
        val installed: Boolean = false,
    )

    /**
     * List installed packages (stub).
     */
    fun listInstalled(): List<PackageInfo> {
        return emptyList()
    }

    /**
     * Search packages (stub).
     */
    fun search(query: String): List<PackageInfo> {
        return emptyList()
    }

    /**
     * Install a package (stub).
     * Will later run "apt-get install -y <name>" inside the RootFS.
     */
    suspend fun install(packageName: String): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Linux runtime not connected yet"))
    }

    /**
     * Remove a package (stub).
     */
    suspend fun remove(packageName: String): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Linux runtime not connected yet"))
    }

    /**
     * Update package lists (stub).
     */
    suspend fun update(): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Linux runtime not connected yet"))
    }
}
