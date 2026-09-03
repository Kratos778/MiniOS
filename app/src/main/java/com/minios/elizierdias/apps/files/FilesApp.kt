package com.minios.elizierdias.apps.files

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File

private data class FileEntry(
    val name: String,
    val file: File?,
    val isDir: Boolean,
    val isShortcut: Boolean = false,
)

@Composable
fun FilesApp() {
    val context = LocalContext.current
    var currentDir by remember { mutableStateOf<File?>(null) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var errorMsg by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }

    fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    val requestLegacy = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) currentDir = Environment.getExternalStorageDirectory()
        else errorMsg = "Permissao negada"
    }

    fun openAllFilesSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
                errorMsg = "Abre Definicoes → Apps → MiniOS → Permissoes"
            }
        }
    }

    fun loadDir(dir: File?) {
        if (dir == null) {
            val roots = mutableListOf<FileEntry>()
            val ext = Environment.getExternalStorageDirectory()
            if (ext.exists()) roots.add(FileEntry("Armazenamento interno", ext, true, true))
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads.exists()) roots.add(FileEntry("Downloads", downloads, true, true))
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (pictures.exists()) roots.add(FileEntry("Pictures / Galeria", pictures, true, true))
            val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            if (dcim.exists()) roots.add(FileEntry("DCIM", dcim, true, true))
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (docs.exists()) roots.add(FileEntry("Documents", docs, true, true))
            entries = roots
            errorMsg = if (roots.isEmpty()) "Sem acesso ao armazenamento" else ""
            return
        }
        try {
            val list = dir.listFiles()?.sortedWith(
                compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
            ) ?: emptyList()
            entries = list.map { f -> FileEntry(f.name, f, f.isDirectory) }
            errorMsg = ""
        } catch (e: Exception) {
            entries = emptyList()
            errorMsg = "Erro: ${e.message}"
        }
    }

    LaunchedEffect(Unit) {
        hasPermission = checkPermission()
        if (hasPermission) loadDir(null)
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) loadDir(currentDir)
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF161B22)).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentDir != null) {
                IconButton(onClick = {
                    val parent = currentDir?.parentFile
                    if (parent == null || parent.path == currentDir?.path) {
                        currentDir = null
                        loadDir(null)
                    } else {
                        currentDir = parent
                        loadDir(parent)
                    }
                }) {
                    Icon(Icons.Filled.ArrowBack, "Voltar", tint = Color(0xFFC9D1D9))
                }
            }
            Text(
                currentDir?.absolutePath ?: "Armazenamento",
                color = Color(0xFF8B949E), fontSize = 12.sp,
                modifier = Modifier.weight(1f), maxLines = 1,
            )
            Button(onClick = {
                hasPermission = checkPermission()
                if (hasPermission) loadDir(currentDir) else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) openAllFilesSettings()
                    else requestLegacy.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }) {
                Text(if (hasPermission) "Atualizar" else "Permitir acesso", fontSize = 11.sp)
            }
        }

        if (!hasPermission) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Para ver ficheiros e a galeria, precisa de acesso ao armazenamento.",
                    color = Color(0xFFC9D1D9), fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) openAllFilesSettings()
                    else requestLegacy.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }) {
                    Text("Conceder permissao")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android 11+: Definicoes → Acesso a todos os ficheiros",
                    color = Color(0xFF8B949E), fontSize = 11.sp,
                )
            }
        }

        if (errorMsg.isNotEmpty()) {
            Text(errorMsg, color = Color(0xFFF85149), fontSize = 12.sp, modifier = Modifier.padding(12.dp))
        }

        LazyColumn {
            items(entries) { e ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (e.isDir && e.file != null) {
                            currentDir = e.file
                            loadDir(e.file)
                        }
                    }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when {
                            e.isShortcut -> Icons.Filled.PhoneAndroid
                            e.isDir -> Icons.Filled.Folder
                            else -> Icons.Filled.InsertDriveFile
                        },
                        null,
                        tint = if (e.isDir) Color(0xFF58A6FF) else Color(0xFF8B949E),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(e.name, color = Color(0xFFC9D1D9), fontSize = 14.sp)
                }
            }
        }
    }
}
