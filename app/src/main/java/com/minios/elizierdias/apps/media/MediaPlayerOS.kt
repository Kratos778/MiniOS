package com.minios.elizierdias.apps.media

// TEMPORARY RESTORE - will be replaced with search version
// See artifacts/MediaPlayerOS.kt for the full improved version with VLC-style search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun MediaPlayerOS() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "MediaPlayerOS — a atualizar... puxa o commit seguinte",
            color = Color.White,
        )
    }
}
