package com.minios.elizierdias.apps.softwarecenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minios.elizierdias.linux.LinuxManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CatalogItem(
    val id: String,
    val title: String,
    val category: String,
    val aptName: String,
    val blurb: String,
)

/** Apps úteis em Debian ARM64 — um toque = apt-get install. */
private val catalog = listOf(
    CatalogItem("nano", "Nano", "Editor", "nano", "Editor de texto simples no terminal"),
    CatalogItem("vim", "Vim", "Editor", "vim", "Editor de texto avançado"),
    CatalogItem("git", "Git", "Dev", "git", "Controlo de versões"),
    CatalogItem("python3", "Python 3", "Dev", "python3", "Interpretador Python"),
    CatalogItem("curl", "curl", "Rede", "curl", "Transferência HTTP na linha de comando"),
    CatalogItem("wget", "wget", "Rede", "wget", "Download de ficheiros"),
    CatalogItem("htop", "htop", "Sistema", "htop", "Monitor de processos"),
    CatalogItem("neofetch", "neofetch", "Sistema", "neofetch", "Info do sistema no terminal"),
    CatalogItem("ffmpeg", "FFmpeg", "Media", "ffmpeg", "Conversão de vídeo/áudio"),
    CatalogItem("imagemagick", "ImageMagick", "Media", "imagemagick", "Edição de imagens CLI"),
    CatalogItem("nodejs", "Node.js", "Dev", "nodejs", "Runtime JavaScript"),
    CatalogItem("nmap", "nmap", "Rede", "nmap", "Scanner de rede"),
)

@Composable
fun SoftwareCenterApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val linux = remember(context) { LinuxManager(context) }
    val pm = remember(linux) { linux.getPackageManager() }
    val isReady by linux.isReady.collectAsState()

    var filter by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("Concede armazenamento + corre full-setup no Terminal se ainda não instalaste o Debian.") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(isReady) {
        if (!isReady) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val list = runCatching { pm.listInstalled() }.getOrElse { emptyList() }
            installed = list.map { it.name }.toSet()
        }
    }

    val shown = catalog.filter {
        filter.isBlank() ||
            it.title.contains(filter, true) ||
            it.aptName.contains(filter, true) ||
            it.category.contains(filter, true)
    }

    fun doInstall(item: CatalogItem) {
        if (busy) return
        busy = true
        progress = "A instalar ${item.aptName}..."
        log = "A instalar ${item.aptName} via apt-get...\n"
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (!linux.isReady.value) {
                        return@withContext com.minios.elizierdias.linux.LinuxPackageManager.OpResult(
                            false,
                            "Linux ainda não está pronto. Abre o Terminal e corre: full-setup",
                        )
                    }
                    pm.install(item.aptName) { line ->
                        progress = line
                        scope.launch(Dispatchers.Main) {
                            log = (log + line + "\n").takeLast(4000)
                        }
                    }
                }
                log = (log + result.message + "\n").takeLast(4000)
                if (result.success) {
                    installed = installed + item.aptName
                }
            } catch (e: Exception) {
                log = (log + "Erro: ${e.message}\n").takeLast(4000)
            } finally {
                busy = false
                progress = null
            }
        }
    }

    fun doUpdate() {
        if (busy) return
        busy = true
        progress = "apt-get update..."
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    pm.update { line ->
                        progress = line
                        scope.launch(Dispatchers.Main) {
                            log = (log + line + "\n").takeLast(4000)
                        }
                    }
                }
                log = (log + result.message + "\n").takeLast(4000)
            } catch (e: Exception) {
                log = (log + "Erro: ${e.message}\n").takeLast(4000)
            } finally {
                busy = false
                progress = null
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(12.dp),
    ) {
        Text("Software Center", color = Color(0xFFC9D1D9), fontSize = 16.sp)
        Text(
            if (isReady) "Debian pronto — toca Instalar para apt-get" else "Linux não pronto — Terminal → full-setup",
            color = if (isReady) Color(0xFF3FB950) else Color(0xFFD29922),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("filtrar (git, python...)", color = Color(0xFF484F58), fontSize = 12.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF161B22),
                    unfocusedContainerColor = Color(0xFF161B22),
                    focusedTextColor = Color(0xFFE6EDF3),
                    unfocusedTextColor = Color(0xFFE6EDF3),
                    cursorColor = Color(0xFF3FB950),
                ),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { doUpdate() },
                enabled = !busy && isReady,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
            ) {
                Text("Update", fontSize = 11.sp)
            }
        }
        if (progress != null) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(progress!!, color = Color(0xFF8B949E), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.id }) { item ->
                val isInst = item.aptName in installed
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF161B22))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = Color(0xFFC9D1D9), fontSize = 13.sp)
                        Text(
                            "${item.category} · apt: ${item.aptName}",
                            color = Color(0xFF8B949E),
                            fontSize = 11.sp,
                        )
                        Text(item.blurb, color = Color(0xFF6E7681), fontSize = 10.sp)
                    }
                    Button(
                        onClick = { doInstall(item) },
                        enabled = !busy && isReady && !isInst,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isInst) Color(0xFF238636) else Color(0xFF1F6FEB),
                            disabledContainerColor = Color(0xFF21262D),
                        ),
                    ) {
                        Text(
                            when {
                                isInst -> "Instalado"
                                busy -> "..."
                                else -> "Instalar"
                            },
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            log.takeLast(800),
            color = Color(0xFF8B949E),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp),
        )
    }
}
