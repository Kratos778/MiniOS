package com.minios.elizierdias.apps.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * MediaPlayerOS — placeholder while full VLC UI is restored.
 * Replace with MediaPlayerOS_FIXED.kt content from local attachment.
 */
@Composable
fun MediaPlayerOS() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1D)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "MediaPlayerOS — cola o MediaPlayerOS_FIXED.kt aqui",
            color = Color(0xFFFF8A00),
            fontSize = 13.sp,
        )
    }
}
