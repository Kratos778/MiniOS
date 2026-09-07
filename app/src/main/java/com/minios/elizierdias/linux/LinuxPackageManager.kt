/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 * This file is part of MiniOS.
 * See LICENSE and COPYRIGHT.md for full terms.
 *
 * Unauthorized copying, modification, distribution or reuse of this file,
 * via any medium, is strictly prohibited without prior written permission.
 */

package com.minios.elizierdias.linux

import java.util.concurrent.atomic.AtomicBoolean

/**
 * APT/dpkg bridge for Debian inside PRoot.
 * Idempotent installs, concurrent lock, safe package names.
 */
class LinuxPackageManager(
    private val runtime: LinuxRuntime,
) {

    data class PackageInfo(
        val name: String,
        val version: String,
        val description: String = "",
        val installed: Boolean = false,
    )

    data class OpResult(
        val success: Boolean,
        val message: String,
        val skipped: Boolean = false,
    )

    companion object {
        private val PACKAGE_NAME_RE = Regex("^[a-z0-9][a-z0-9+._-]{0,200}$")
        private val aptBusy = AtomicBoolean(false)
    }

    private fun log(tag: String, msg: String) = "[$tag] $msg"

    fun validatePackageName(name: String): Result<String> {
        val n = name.trim().lowercase()
        if (n.isEmpty()) {
            return Result.failure(IllegalArgumentException(log("ERROR", "Nome de pacote vazio")))
        }
        if (!PACKAGE_NAME_RE.matches(n)) {
            return Result.failure(
                IllegalArgumentException(
                    log("ERROR", "Nome de pacote inválido: '$name' (só a-z 0-9 + . _ -)"),
                ),
            )
        }
        return Result.success(n)
    }

    private fun tryLock(): Boolean = aptBusy.compareAndSet(false, true)

    private fun unlock() {
        aptBusy.set(false)
    }

    fun isBusy(): Boolean = aptBusy.get()

    /**
     * True if package is fully installed.
     * Uses dpkg -s (reliable) instead of fragile -f format strings.
     */
    suspend fun isPackageInstalled(packageName: String): Boolean {
        val name = validatePackageName(packageName).getOrElse { return false }
        val r = runtime.exec(
            "dpkg -s $name 2>/dev/null | grep '^Status:' || true",
            timeoutSec = 30,
        )
        val status = r.getOrNull()?.stdout.orEmpty()
        return status.contains("install ok installed")
    }

    suspend fun packageVersion(packageName: String): String? {
        val name = validatePackageName(packageName).getOrElse { return null }
        val r = runtime.exec(
            "dpkg -s $name 2>/dev/null | grep '^Version:' | head -1 || true",
            timeoutSec = 30,
        )
        val line = r.getOrNull()?.stdout?.trim().orEmpty()
        return line.removePrefix("Version:").trim().ifEmpty { null }
    }

    suspend fun listInstalled(): List<PackageInfo> {
        val r = runtime.exec(
            "dpkg-query -W -f='\${'$'}{Package}\\t\${'$'}{Version}\\n' 2>/dev/null || true",
            timeoutSec = 60,
        )
        val out = r.getOrNull()?.stdout.orEmpty()
        return out.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && '\t' in it }
            .map { line ->
                val parts = line.split('\t', limit = 2)
                PackageInfo(
                    name = parts[0],
                    version = parts.getOrElse(1) { "" },
                    installed = true,
                )
            }
            .toList()
    }

    suspend fun search(query: String): List<PackageInfo> {
        val q = query.trim()
        if (q.isEmpty() || q.length > 100) return emptyList()
        val safe = q.replace(Regex("[^a-zA-Z0-9+._* -]"), "")
        if (safe.isBlank()) return emptyList()
        val r = runtime.exec(
            "apt-cache search --names-only ${safe.take(80)} 2>/dev/null | head -n 40",
            timeoutSec = 60,
        )
        val out = r.getOrNull()?.stdout.orEmpty()
        return out.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val idx = line.indexOf(" - ")
                if (idx > 0) {
                    PackageInfo(
                        name = line.substring(0, idx).trim(),
                        version = "",
                        description = line.substring(idx + 3).trim(),
                        installed = false,
                    )
                } else {
                    PackageInfo(name = line, version = "", installed = false)
                }
            }
            .toList()
    }

    suspend fun update(onProgress: ((String) -> Unit)? = null): OpResult {
        if (!tryLock()) {
            return OpResult(
                false,
                log("WARNING", "Já existe uma operação APT em andamento. Aguarde."),
            )
        }
        return try {
            onProgress?.invoke(log("APT", "A atualizar índices..."))
            val r = runtime.exec(
                "DEBIAN_FRONTEND=noninteractive apt-get update -y",
                timeoutSec = 300,
            )
            val body = buildString {
                r.getOrNull()?.let {
                    if (it.stdout.isNotBlank()) appendLine(it.stdout)
                    if (it.stderr.isNotBlank()) appendLine(it.stderr)
                }
                r.exceptionOrNull()?.let { appendLine(it.message) }
            }.trim()
            val ok = r.getOrNull()?.exitCode == 0
            if (ok) {
                OpResult(true, log("APT", "Índices atualizados.\n$body"))
            } else {
                OpResult(false, log("ERROR", "apt-get update falhou.\n$body"))
            }
        } finally {
            unlock()
        }
    }

    suspend fun install(
        packageName: String,
        onProgress: ((String) -> Unit)? = null,
    ): OpResult {
        val name = validatePackageName(packageName).getOrElse {
            return OpResult(false, it.message ?: "nome inválido")
        }

        if (!tryLock()) {
            return OpResult(
                false,
                log("WARNING", "Já existe uma instalação em andamento. Aguarde a operação atual terminar."),
            )
        }

        return try {
            onProgress?.invoke(log("INSTALL", "Verificando $name..."))
            if (isPackageInstalled(name)) {
                val ver = packageVersion(name) ?: "?"
                val msg = log(
                    "WARNING",
                    "O pacote '$name' já está instalado ($ver).\n" +
                        "Nenhuma instalação foi realizada para evitar ocupar espaço desnecessariamente.",
                )
                onProgress?.invoke(msg)
                return OpResult(true, msg, skipped = true)
            }

            onProgress?.invoke(log("INSTALL", "Instalando $name..."))
            val r = runtime.exec(
                "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends $name",
                timeoutSec = 600,
            )
            val body = buildString {
                r.getOrNull()?.let {
                    if (it.stdout.isNotBlank()) appendLine(it.stdout)
                    if (it.stderr.isNotBlank()) appendLine(it.stderr)
                }
                r.exceptionOrNull()?.let { appendLine(it.message) }
            }.trim()

            val exitOk = r.getOrNull()?.exitCode == 0
            val really = isPackageInstalled(name)
            when {
                really -> {
                    val ver = packageVersion(name) ?: ""
                    val msg = log(
                        "INSTALL",
                        "$name instalado com sucesso${if (ver.isNotEmpty()) " ($ver)" else ""}.",
                    )
                    onProgress?.invoke(msg)
                    OpResult(true, "$msg\n$body")
                }
                exitOk -> {
                    // apt OK — sometimes Status line lags; treat as success if exit 0
                    val msg = log(
                        "INSTALL",
                        "$name: apt terminou com sucesso (exit 0).",
                    )
                    onProgress?.invoke(msg)
                    OpResult(true, "$msg\n$body")
                }
                else -> {
                    OpResult(false, log("ERROR", "Falha ao instalar $name.\n$body"))
                }
            }
        } finally {
            unlock()
        }
    }

    suspend fun remove(
        packageName: String,
        onProgress: ((String) -> Unit)? = null,
    ): OpResult {
        val name = validatePackageName(packageName).getOrElse {
            return OpResult(false, it.message ?: "nome inválido")
        }

        if (!tryLock()) {
            return OpResult(
                false,
                log("WARNING", "Já existe uma operação APT em andamento. Aguarde."),
            )
        }

        return try {
            if (!isPackageInstalled(name)) {
                val msg = log("WARNING", "'$name' não está instalado. Nada a remover.")
                return OpResult(true, msg, skipped = true)
            }
            onProgress?.invoke(log("APT", "A remover $name..."))
            val r = runtime.exec(
                "DEBIAN_FRONTEND=noninteractive apt-get remove -y $name",
                timeoutSec = 300,
            )
            val body = buildString {
                r.getOrNull()?.let {
                    if (it.stdout.isNotBlank()) appendLine(it.stdout)
                    if (it.stderr.isNotBlank()) appendLine(it.stderr)
                }
            }.trim()
            val gone = !isPackageInstalled(name)
            if (gone || r.getOrNull()?.exitCode == 0) {
                OpResult(true, log("APT", "$name removido.\n$body"))
            } else {
                OpResult(false, log("ERROR", "Falha ao remover $name.\n$body"))
            }
        } finally {
            unlock()
        }
    }
}
