/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.shell.taskbar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minios.elizierdias.core.MiniWindow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TaskbarBg = Color(0xE6181818)
private val PillBg = Color(0xFF2C2C2C)
private val Accent = Color(0xFF60CDFF)
private val OnSurface = Color(0xFFF3F3F3)
private val OnSurfaceDim = Color(0xFFC8C8C8)

@Composable
fun Taskbar(
    openWindows: List<MiniWindow>,
    mouseEnabled: Boolean,
    onMouseToggle: (Boolean) -> Unit,
    onStartClick: () -> Unit,
    onWindowClick: (String) -> Unit,
) {
    val context = LocalContext.current

    var batteryLevel by remember { mutableIntStateOf(readBatteryLevel(context)) }
    var charging by remember { mutableStateOf(isCharging(context)) }
    var connected by remember { mutableStateOf(isNetworkConnected(context)) }
    var wifi by remember { mutableStateOf(isWifi(context)) }

    var timeText by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    var dateText by remember {
        mutableStateOf(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()))
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            dateText = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(now)
            delay(15_000L)
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                batteryLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, batteryLevel)
                    ?: batteryLevel
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            connected = isNetworkConnected(context)
            wifi = isWifi(context)
            delay(2_500L)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(TaskbarBg)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // —— Start (Win11-like)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onStartClick)
                .background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.GridView,
                contentDescription = "Start",
                tint = Accent,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier = Modifier.width(8.dp))

        // —— Open windows (left cluster)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            openWindows.forEach { window ->
                val active = window.isFocused && !window.isMinimized
                Row(
                    modifier = Modifier
                        .widthIn(max = 140.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) Color(0x33FFFFFF) else Color.Transparent)
                        .then(
                            if (active) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = Accent.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(8.dp),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onWindowClick(window.instanceId) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = window.app.icon,
                        contentDescription = null,
                        tint = if (active) OnSurface else OnSurfaceDim,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier = Modifier.width(6.dp))
                    Text(
                        text = window.app.title,
                        color = if (active) OnSurface else OnSurfaceDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // —— System tray pill (WiFi + mouse + battery + clock) — visible like Win11
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PillBg)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Wi‑Fi / rede — label legível
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        connected = isNetworkConnected(context)
                        wifi = isWifi(context)
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (connected) Icons.Filled.SignalWifi4Bar else Icons.Filled.SignalWifiOff,
                    contentDescription = "Wi‑Fi",
                    tint = if (connected) Accent else OnSurfaceDim,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when {
                        !connected -> "Sem rede"
                        wifi -> "Wi‑Fi"
                        else -> "Dados"
                    },
                    color = if (connected) OnSurface else OnSurfaceDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Mouse toggle
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (mouseEnabled) Color(0x334C9AFF) else Color.Transparent)
                    .clickable { onMouseToggle(!mouseEnabled) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Mouse,
                    contentDescription = if (mouseEnabled) "Mouse ON" else "Mouse OFF",
                    tint = if (mouseEnabled) Accent else OnSurfaceDim,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Battery
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when {
                        batteryLevel <= 15 -> Icons.Filled.BatteryAlert
                        batteryLevel <= 40 -> Icons.Filled.BatteryStd
                        else -> Icons.Filled.BatteryFull
                    },
                    contentDescription = "Bateria",
                    tint = when {
                        batteryLevel <= 15 -> Color(0xFFFF6B6B)
                        charging -> Color(0xFF3FB950)
                        else -> OnSurfaceDim
                    },
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "$batteryLevel%",
                    color = OnSurface,
                    fontSize = 11.sp,
                )
            }

            // Clock + date (like Win11 tray)
            Column(
                modifier = Modifier.padding(start = 4.dp, end = 2.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = timeText,
                    color = OnSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 14.sp,
                )
                Text(
                    text = dateText,
                    color = OnSurfaceDim,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                )
            }
        }
    }
}

private fun isNetworkConnected(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun isWifi(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

private fun readBatteryLevel(context: Context): Int {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
}

private fun isCharging(context: Context): Boolean {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: return false
    return status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
}
