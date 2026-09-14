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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
import com.minios.elizierdias.core.NoskKeepAliveService
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

        maybeRequestHomeRole()
        maybeRequestIgnoreBatteryOptimizations()
        NoskKeepAliveService.start(this)

        setContent {
            MiniOSTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DesktopRoot()
                }
            }
        }
    }

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

    private fun maybeRequestIgnoreBatteryOptimizations() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
            val prefs = getSharedPreferences("noskos_setup", MODE_PRIVATE)
            if (prefs.getBoolean("battery_prompt_done", false)) return

            val pm = getSystemService(PowerManager::class.java) ?: return
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                prefs.edit().putBoolean("battery_prompt_done", true).apply()
                return
            }

            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            prefs.edit().putBoolean("battery_prompt_done", true).apply()
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
            }
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL,
            TRIM_MEMORY_COMPLETE,
            TRIM_MEMORY_MODERATE,
            -> {
                try {
                    System.gc()
                } catch (_: Exception) {
                }
            }
        }
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
