package com.minios.elizierdias.apps.softwarecenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Pkg(val name: String, val cat: String, val ver: String)
private val catalog = listOf(
    Pkg("MiniOS Files", "MiniOS", "0.1.0"),
    Pkg("MiniOS Terminal", "MiniOS", "0.1.0"),
    Pkg("Bash", "System", "5.2"),
    Pkg("Git", "Linux", "2.43"),
    Pkg("Python", "Linux", "3.12"),
    Pkg("Node.js", "Linux", "20 LTS"),
)

@Composable
fun SoftwareCenterApp() {
    Column(Modifier.fillMaxSize().background(Color(0xFF0D1117)).padding(12.dp)) {
        Text("Software Center", color = Color(0xFFC9D1D9), fontSize = 15.sp)
        Text("Instalacao real chega com Linux Runtime (v0.2+)", color = Color(0xFF8B949E), fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        LazyColumn {
            items(catalog) { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF161B22)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = Color(0xFFC9D1D9), fontSize = 13.sp)
                        Text("${p.cat} · v${p.ver} · ARM64", color = Color(0xFF8B949E), fontSize = 11.sp)
                    }
                    Button(onClick = {}, enabled = false) { Text("Indisponivel", fontSize = 11.sp) }
                }
            }
        }
    }
}
