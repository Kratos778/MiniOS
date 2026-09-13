/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * NoskOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.minios.elizierdias.shell.desktop.Desktop
import com.minios.elizierdias.ui.theme.MiniOSTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        )

        enableEdgeToEdge()
        hideSystemBars()

        // Pedir para ser launcher / ecran inicial (se ainda nao for)
        maybeRequestHomeRole()

        setContent {
            MiniOSTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DesktopRoot()
                }
            }
        }
    }

    /**
     * Android so mostra o seletor de launcher se pedirmos ROLE_HOME
     * ou se o utilizador carregar no botao Home. Sem isto o NoskOS
     * parece so "mais uma app".
     */
    private fun maybeRequestHomeRole() {
        try {
            val prefs = getSharedPreferences("noskos_setup", MODE_PRIVATE)
            if (prefs.getBoolean("home_prompt_done", false)) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = getSystemService(RoleManager::class.java) ?: return
                if (rm.isRoleAvailable(RoleManager.ROLE_HOME) &&
                    !rm.isRoleHeld(RoleManager.ROLE_HOME)
                ) {
                    startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    prefs.edit().putBoolean("home_prompt_done", true).apply()
                    return
                }
            }
            // Fallback: ecran de apps iniciais do sistema
            try {
                startActivity(
                    Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                prefs.edit().putBoolean("home_prompt_done", true).apply()
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }
}

@Composable
private fun DesktopRoot() {
    Desktop()
}
