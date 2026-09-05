package com.minios.elizierdias.apps.files

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.minios.elizierdias.ui.components.PcLazyVerticalScrollbar
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
    val activity = context as? ComponentActivity
    val listState = rememberLazyListState()

    var currentDir by remember { mutableStateOf<File?>(null) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var errorMsg by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }

    fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    val requestLegacy = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) {
            errorMsg = ""
            currentDir = null
        } else {
            errorMsg = "Permissão negada"
        }
    }

    fun openAllFilesSettings() {
        errorMsg = ""
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    errorMsg = "Ativa acesso a todos os ficheiros"
                } catch (_: Exception) {
                    errorMsg = "Abre Definições → Apps → MiniOS → Permissões"
                }
            }
        }
    }

    fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openAllFilesSettings()
        } else {
            requestLegacy.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
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
            val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (music.exists()) roots.add(FileEntry("Music", music, true, true))
            val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (movies.exists()) roots.add(FileEntry("Movies", movies, true, true))
            entries = roots
            errorMsg = if (roots.isEmpty()) "Sem acesso ao armazenamento" else ""
            return
        }
        try {
            val listed = dir.listFiles()
            if (listed == null) {
                entries = emptyList()
                errorMsg = "Sem permissão para ler esta pasta"
                hasPermission = checkPermission()
                return
            }
            val list = listed.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            entries = list.map { f -> FileEntry(f.name, f, f.isDirectory) }
            errorMsg = ""
        } catch (e: Exception) {
            entries = emptyList()
            errorMsg = "Erro: ${e.message}"
        }
    }

    fun refreshPermissionAndLoad() {
        val granted = checkPermission()
        hasPermission = granted
        if (granted) loadDir(currentDir) else entries = emptyList()
    }

    DisposableEffect(activity) {
        val owner = activity ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissionAndLoad()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { refreshPermissionAndLoad() }
    LaunchedEffect(hasPermission) { if (hasPermission) loadDir(currentDir) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF161B22)).padding(horizontal = 8.dp, vertical = 6.dp),
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
                text = currentDir?.absolutePath ?: "Armazenamento",
                color = Color(0xFF8B949E),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Button(onClick = {
                if (checkPermission()) refreshPermissionAndLoad() else requestAccess()
            }) {
                Text(text = if (hasPermission) "Atualizar" else "Permitir acesso", fontSize = 11.sp)
            }
        }

        if (!hasPermission) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Para ver ficheiros, concede acesso ao armazenamento.", color = Color(0xFFC9D1D9), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { requestAccess() }) { Text("Conceder permissão") }
            }
        }

        if (errorMsg.isNotEmpty()) {
            Text(errorMsg, color = Color(0xFFF85149), fontSize = 12.sp, modifier = Modifier.padding(12.dp))
        }

        if (hasPermission) {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(entries) { e ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (e.isDir && e.file != null) {
                                    currentDir = e.file
                                    loadDir(e.file)
                                }
                            }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = when {
                                    e.isShortcut -> Icons.Filled.PhoneAndroid
                                    e.isDir -> Icons.Filled.Folder
                                    else -> Icons.Filled.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = if (e.isDir) Color(0xFF58A6FF) else Color(0xFF8B949E),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(e.name, color = Color(0xFFC9D1D9), fontSize = 14.sp)
                        }
                    }
                }
                PcLazyVerticalScrollbar(
                    state = listState,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                )
            }
        }
    }
}
