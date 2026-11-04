package com.minios.elizierdias.apps.settings

// Restored in next patch if this is too large — placeholder safe compile
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
fun SettingsApp() {
    Box(Modifier.fillMaxSize().background(Color(0xFF0D1117)), contentAlignment = Alignment.Center) {
        Text("Settings a restaurar — reabre apos build", color = Color(0xFFC9D1D9), fontSize = 14.sp)
    }
}
