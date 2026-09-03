package com.minios.elizierdias.personalization

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class Wallpaper(val id: String, val previewColor: Color, val brush: Brush)

object Wallpapers {
    val defaultGradient = Wallpaper(
        "default_gradient", Color(0xFF1F6FEB),
        Brush.linearGradient(listOf(Color(0xFF0D1117), Color(0xFF1F6FEB))),
    )
    val sunset = Wallpaper(
        "sunset", Color(0xFFDB6D28),
        Brush.linearGradient(listOf(Color(0xFF3D1E12), Color(0xFFDB6D28))),
    )
    val forest = Wallpaper(
        "forest", Color(0xFF238636),
        Brush.linearGradient(listOf(Color(0xFF0D1117), Color(0xFF238636))),
    )
    val violet = Wallpaper(
        "violet", Color(0xFF8957E5),
        Brush.linearGradient(listOf(Color(0xFF0D1117), Color(0xFF8957E5))),
    )
    val all = listOf(defaultGradient, sunset, forest, violet)
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: defaultGradient
}
