package com.minios.elizierdias.apps.files

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.minios.elizierdias.ui.components.PcLazyVerticalScrollbar
import com.minios.elizierdias.windows.LaunchResult
import com.minios.elizierdias.windows.WindowsManager
import java.io.File
import kotlinx.coroutines.launch

private data class FileEntry(
    val name: String,
    val file: File?,
    val isDir: Boolean,
    val isShortcut: Boolean = false,
)

private fun mimeOf(file: File): String {
    val ext = file.extension.lowercase()
    val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    if (fromMap != null) return fromMap
    return when (ext) {
        "yml", "yaml" -> "text/yaml"
        "md", "markdown" -> "text/markdown"
        "json" -> "application/json"
        "kt", "kts", "java", "py", "sh", "c", "cpp", "h", "rs", "go" -> "text/plain"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "doc" -> "application/msword"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "apk" -> "application/vnd.android.package-archive"
        "exe" -> "application/x-msdownload"
        "msi" -> "application/x-msi"
        "bat", "cmd" -> "application/x-bat"
        else -> "application/octet-stream"
    }
}

private fun isImage(ext: String) =
    ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

private fun isText(ext: String) =
    ext in setOf(
        "txt", "log", "md", "markdown", "json", "yml", "yaml", "xml", "html", "htm",
        "css", "js", "ts", "kt", "kts", "java", "py", "sh", "c", "cpp", "h", "rs", "go",
        "csv", "ini", "conf", "cfg", "properties", "gradle", "toml",
    )

private fun isWindowsExecutable(ext: String) =
    ext in setOf("exe", "msi", "bat", "cmd", "scr")

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    return "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}

@Composable
fun FilesApp() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()

    val windowsManager = remember(context) {
        WindowsManager(context).also { it.initialize() }
    }

    var currentDir by remember { mutableStateOf<File?>(null) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var errorMsg by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var previewText by remember { mutableStateOf<String?>(null) }

    // Diálogo de confirmação para executáveis Windows
    var pendingExe by remember { mutableStateOf<File?>(null) }
    var launchStatus by remember { mutableStateOf<String?>(null) }

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

    fun openAllFilesSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
            }
        }
    }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted || checkPermission()
        if (hasPermission) {
            currentDir = null
        }
    }

    fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openAllFilesSettings()
        } else {
            storageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun loadDir(dir: File?) {
        errorMsg = ""
        try {
            if (dir == null) {
                val roots = mutableListOf<FileEntry>()
                val ext = Environment.getExternalStorageDirectory()
                if (ext != null && ext.exists()) {
                    roots.add(FileEntry("Armazenamento interno", ext, true, isShortcut = true))
                }
                val mini = File("/sdcard/MiniOS")
                if (mini.exists()) {
                    roots.add(FileEntry("MiniOS (sdcard)", mini, true, isShortcut = true))
                }
                val dcim = File(ext, "DCIM")
                if (dcim.exists()) roots.add(FileEntry("DCIM", dcim, true, isShortcut = true))
                val dl = File(ext, "Download")
                if (dl.exists()) roots.add(FileEntry("Download", dl, true, isShortcut = true))
                entries = roots
                return
            }
            if (!dir.exists() || !dir.canRead()) {
                entries = emptyList()
                errorMsg = "Sem acesso: ${dir.absolutePath}"
                hasPermission = checkPermission()
                return
            }
            val list = dir.listFiles()?.sortedWith(
                compareBy({ !it.isDirectory }, { it.name.lowercase() }),
            ) ?: emptyList()
            entries = list.map { f -> FileEntry(f.name, f, f.isDirectory) }
        } catch (e: Exception) {
            entries = emptyList()
            errorMsg = e.message ?: "Erro ao listar"
        }
    }

    fun refreshPermissionAndLoad() {
        val granted = checkPermission()
        hasPermission = granted
        if (granted) loadDir(currentDir) else entries = emptyList()
    }

    fun openExternal(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file,
            )
            val mime = mimeOf(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Abrir com"))
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Sem app para abrir: ${file.name} (${e.message})",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun handleWindowsExecutable(file: File) {
        when (val result = windowsManager.prepareLaunch(file)) {
            is LaunchResult.ConfirmationRequired -> {
                pendingExe = file
            }
            is LaunchResult.NeedsInstall -> {
                launchStatus = result.message
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
            is LaunchResult.Failure -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
            is LaunchResult.Success -> {
                // já lançado (não deve acontecer em prepareLaunch)
            }
        }
    }

    fun confirmAndLaunch(file: File) {
        pendingExe = null
        scope.launch {
            launchStatus = "A iniciar ${file.name}..."
            when (val result = windowsManager.launchConfirmed(file)) {
                is LaunchResult.Success -> {
                    launchStatus = "Iniciado: ${file.name}"
                    Toast.makeText(
                        context,
                        "Windows: ${file.name} (placeholder até Fase 2)",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                is LaunchResult.NeedsInstall -> {
                    launchStatus = result.message
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
                is LaunchResult.Failure -> {
                    launchStatus = result.message
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
                is LaunchResult.ConfirmationRequired -> {
                    // não deve acontecer aqui
                }
            }
        }
    }

    fun openFile(file: File) {
        val ext = file.extension.lowercase()
        when {
            isWindowsExecutable(ext) -> {
                handleWindowsExecutable(file)
            }
            isImage(ext) -> {
                previewFile = file
                previewText = null
            }
            isText(ext) || file.length() < 512_000 && ext.isEmpty() -> {
                try {
                    val text = file.readText()
                    previewFile = file
                    previewText = text.take(200_000)
                } catch (e: Exception) {
                    openExternal(file)
                }
            }
            else -> openExternal(file)
        }
    }

    DisposableEffect(activity) {
        val act = activity
        if (act != null) {
            val obs = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshPermissionAndLoad()
            }
            act.lifecycle.addObserver(obs)
            onDispose { act.lifecycle.removeObserver(obs) }
        } else {
            onDispose { }
        }
    }

    LaunchedEffect(Unit) {
        hasPermission = checkPermission()
        if (hasPermission) loadDir(null)
    }
    LaunchedEffect(hasPermission) { if (hasPermission) loadDir(currentDir) }

    val listState = rememberLazyListState()

    // Diálogo de confirmação EXE
    pendingExe?.let { exe ->
        AlertDialog(
            onDismissRequest = { pendingExe = null },
            title = { Text("Executar programa Windows?") },
            text = {
                Column {
                    Text("Ficheiro: ${exe.name}")
                    Spacer(Modifier.height(4.dp))
                    Text("Caminho: ${exe.absolutePath}", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    Text("Tamanho: ${formatSize(exe.length())}")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Programas Windows podem alterar ficheiros no prefixo Wine. " +
                            "Só continue se confiar na origem do ficheiro.",
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmAndLaunch(exe) }) {
                    Text("Executar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingExe = null }) {
                    Text("Cancelar")
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B22))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (previewFile != null) {
                IconButton(onClick = {
                    previewFile = null
                    previewText = null
                }) {
                    Icon(Icons.Filled.Close, "Fechar", tint = Color(0xFFC9D1D9))
                }
            } else if (currentDir != null) {
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
                text = previewFile?.name ?: currentDir?.absolutePath ?: "Armazenamento",
                color = Color(0xFF8B949E),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (previewFile != null) {
                Button(onClick = { previewFile?.let { openExternal(it) } }) {
                    Text("Abrir externo", fontSize = 11.sp)
                }
            } else {
                Button(onClick = {
                    if (checkPermission()) refreshPermissionAndLoad() else requestAccess()
                }) {
                    Text(
                        text = if (hasPermission) "Atualizar" else "Permitir acesso",
                        fontSize = 11.sp,
                    )
                }
            }
        }

        launchStatus?.let { msg ->
            Text(
                msg,
                color = Color(0xFF58A6FF),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        if (!hasPermission) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Para ver ficheiros, concede acesso ao armazenamento.",
                    color = Color(0xFFC9D1D9),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { requestAccess() }) { Text("Conceder permissao") }
            }
        }

        if (errorMsg.isNotEmpty()) {
            Text(
                errorMsg,
                color = Color(0xFFF85149),
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp),
            )
        }

        val pf = previewFile
        if (hasPermission && pf != null) {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val ext = pf.extension.lowercase()
                when {
                    isImage(ext) -> {
                        AsyncImage(
                            model = pf,
                            contentDescription = pf.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    previewText != null -> {
                        Text(
                            text = previewText!!,
                            color = Color(0xFFC9D1D9),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp),
                        )
                    }
                    else -> {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Pre-visualizacao nao disponivel para este tipo.",
                                color = Color(0xFF8B949E),
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { openExternal(pf) }) {
                                Text("Abrir com outra app")
                            }
                        }
                    }
                }
            }
        } else if (hasPermission) {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(entries) { e ->
                        val isExe = e.file != null && !e.isDir &&
                            isWindowsExecutable(e.file.extension.lowercase())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when {
                                        e.isDir && e.file != null -> {
                                            currentDir = e.file
                                            loadDir(e.file)
                                        }
                                        e.file != null && !e.isDir -> openFile(e.file)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = when {
                                    e.isShortcut -> Icons.Filled.PhoneAndroid
                                    e.isDir -> Icons.Filled.Folder
                                    else -> Icons.Filled.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = when {
                                    e.isDir -> Color(0xFF58A6FF)
                                    isExe -> Color(0xFFD2A8FF)
                                    else -> Color(0xFF8B949E)
                                },
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                e.name,
                                color = if (isExe) Color(0xFFD2A8FF) else Color(0xFFC9D1D9),
                                fontSize = 14.sp,
                            )
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
