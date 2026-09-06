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
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
private val FlyoutBg = Color(0xF22C2C2C)

@Composable
fun Taskbar(
    openWindows: List<MiniWindow>,
    mouseEnabled: Boolean,
    onMouseToggle: (Boolean) -> Unit,
    onStartClick: () -> Unit,
    onWindowClick: (String) -> Unit,
    onExitMiniOS: () -> Unit = {},
) {
    val context = LocalContext.current

    var batteryLevel by remember { mutableIntStateOf(readBatteryLevel(context)) }
    var charging by remember { mutableStateOf(isCharging(context)) }
    var connected by remember { mutableStateOf(isNetworkConnected(context)) }
    var wifi by remember { mutableStateOf(isWifi(context)) }
    var showQuickSettings by remember { mutableStateOf(false) }
    var statusNote by remember { mutableStateOf<String?>(null) }

    var timeText by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    var dateText by remember {
        mutableStateOf(SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()))
    }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVol = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volume by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol,
        )
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
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            connected = isNetworkConnected(context)
            wifi = isWifi(context)
            delay(2_500L)
        }
    }

    if (showQuickSettings) {
        Popup(
            alignment = Alignment.BottomEnd,
            offset = IntOffset(-12, -56),
            onDismissRequest = { showQuickSettings = false },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(FlyoutBg)
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Text(
                    text = "MiniOS — rapido",
                    color = OnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickTile(
                        modifier = Modifier.weight(1f),
                        icon = if (connected && wifi) {
                            Icons.Filled.SignalWifi4Bar
                        } else {
                            Icons.Filled.WifiOff
                        },
                        label = when {
                            !connected -> "Sem rede"
                            wifi -> "Wi-Fi"
                            else -> "Dados"
                        },
                        active = connected,
                        onClick = {
                            connected = isNetworkConnected(context)
                            wifi = isWifi(context)
                            statusNote = when {
                                !connected -> "Sem ligacao"
                                wifi -> "Wi-Fi ativo"
                                else -> "Dados moveis ativos"
                            }
                        },
                    )
                    QuickTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.NetworkCell,
                        label = "Internet",
                        active = connected,
                        onClick = {
                            connected = isNetworkConnected(context)
                            wifi = isWifi(context)
                            statusNote = if (connected) "Online" else "Offline"
                        },
                    )
                    QuickTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Bluetooth,
                        label = "Bluetooth",
                        active = false,
                        onClick = {
                            statusNote = "Bluetooth: so estado (sem sair do MiniOS)"
                        },
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Mouse,
                        label = if (mouseEnabled) "Mouse ON" else "Mouse OFF",
                        active = mouseEnabled,
                        onClick = { onMouseToggle(!mouseEnabled) },
                    )
                    QuickTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        label = "Sair",
                        active = false,
                        onClick = {
                            showQuickSettings = false
                            onExitMiniOS()
                        },
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = OnSurface,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = volume,
                        onValueChange = { v ->
                            volume = v
                            val level = (v * maxVol).toInt().coerceIn(0, maxVol)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            inactiveTrackColor = Color(0x44FFFFFF),
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (charging) {
                        "Bateria $batteryLevel% (a carregar)"
                    } else {
                        "Bateria $batteryLevel%"
                    },
                    color = OnSurfaceDim,
                    fontSize = 12.sp,
                )
                statusNote?.let { note ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = note, color = Accent, fontSize = 11.sp)
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(TaskbarBg)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onStartClick)
                .background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = "Start",
                tint = Accent,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

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
                        .widthIn(max = 130.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) Color(0x33FFFFFF) else Color.Transparent)
                        .then(
                            if (active) {
                                Modifier.border(
                                    1.dp,
                                    Accent.copy(alpha = 0.55f),
                                    RoundedCornerShape(8.dp),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onWindowClick(window.instanceId) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = window.app.icon,
                        contentDescription = null,
                        tint = if (active) OnSurface else OnSurfaceDim,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = window.app.title,
                        color = if (active) OnSurface else OnSurfaceDim,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PillBg)
                .clickable { showQuickSettings = !showQuickSettings }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (connected) Icons.Filled.SignalWifi4Bar else Icons.Filled.WifiOff,
                contentDescription = "Rede",
                tint = if (connected) Accent else OnSurfaceDim,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = when {
                    !connected -> "Off"
                    wifi -> "Wi-Fi"
                    else -> "Dados"
                },
                color = if (connected) OnSurface else OnSurfaceDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Filled.BatteryFull,
                contentDescription = "Bateria",
                tint = when {
                    batteryLevel <= 15 -> Color(0xFFFF6B6B)
                    charging -> Color(0xFF3FB950)
                    else -> OnSurfaceDim
                },
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "$batteryLevel%",
                color = OnSurface,
                fontSize = 11.sp,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeText,
                    color = OnSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 13.sp,
                )
                Text(
                    text = dateText,
                    color = OnSurfaceDim,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun QuickTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Accent.copy(alpha = 0.35f) else Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Accent else OnSurface,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = OnSurface,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    return try {
        val intent = if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: return false
        status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    } catch (_: Exception) {
        false
    }
}
