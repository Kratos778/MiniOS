package com.minios.elizierdias.apps.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Entry(val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Folder)
private val root = listOf(
    Entry("Home"), Entry("Downloads"), Entry("Documents"), Entry("Pictures"),
    Entry("Android Storage", Icons.Filled.PhoneAndroid),
)

@Composable
fun FilesApp() {
    var path by remember { mutableStateOf(listOf("MiniOS")) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        Text(path.joinToString(" / "), color = Color(0xFF8B949E), fontSize = 12.sp, modifier = Modifier.padding(10.dp))
        LazyColumn {
            items(root) { e ->
                Row(
                    Modifier.fillMaxWidth().clickable { path = listOf("MiniOS", e.name) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(e.icon, null, tint = Color(0xFF58A6FF), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(e.name, color = Color(0xFFC9D1D9), fontSize = 14.sp)
                }
            }
        }
    }
}
