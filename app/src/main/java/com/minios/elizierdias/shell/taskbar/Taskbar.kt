package com.minios.elizierdias.shell.taskbar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.SignalWifi4Bar
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minios.elizierdias.core.MiniWindow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Taskbar(
    openWindows: List<MiniWindow>,
    mouseEnabled: Boolean,
    onMouseToggle: (Boolean) -> Unit,
    onStartClick: () -> Unit,
    onWindowClick: (String) -> Unit,
) {
    val context = LocalContext.current

    var batteryLevel by remember {
        mutableIntStateOf(readBatteryLevel(context))
    }

    var connected by remember {
        mutableStateOf(isNetworkConnected(context))
    }

    var now by remember {
        mutableStateOf(
            SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            ).format(Date())
        )
    }

    /*
     * Atualiza o relógio.
     */
    LaunchedEffect(Unit) {
        while (true) {
            now = SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            ).format(Date())

            delay(15_000L)
        }
    }

    /*
     * Atualiza bateria quando o Android
     * envia uma mudança.
     */
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                batteryLevel =
                    intent?.getIntExtra(
                        BatteryManager.EXTRA_LEVEL,
                        batteryLevel
                    ) ?: batteryLevel
            }
        }

        val filter = IntentFilter(
            Intent.ACTION_BATTERY_CHANGED
        )

        context.registerReceiver(
            receiver,
            filter
        )

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    /*
     * Atualiza o estado da rede periodicamente.
     */
    LaunchedEffect(Unit) {
        while (true) {
            connected =
                isNetworkConnected(context)

            delay(3_000L)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF161B22)
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        /*
         * START
         */
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .clip(
                    RoundedCornerShape(6.dp)
                )
                .clickable {
                    onStartClick()
                }
                .padding(
                    horizontal = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = "Start",
                tint = Color(0xFF58A6FF),
                modifier = Modifier.size(18.dp)
            )

            Spacer(
                modifier = Modifier.width(6.dp)
            )

            Text(
                text = "MiniOS",
                color = Color(0xFFC9D1D9),
                fontSize = 13.sp
            )
        }

        /*
         * JANELAS ABERTAS
         */
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            openWindows.forEach { window ->

                Row(
                    modifier = Modifier
                        .padding(end = 5.dp)
                        .clip(
                            RoundedCornerShape(6.dp)
                        )
                        .background(
                            if (
                                window.isFocused &&
                                !window.isMinimized
                            ) {
                                Color(0xFF21262D)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable {
                            onWindowClick(
                                window.instanceId
                            )
                        }
                        .padding(
                            horizontal = 9.dp,
                            vertical = 5.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = window.app.icon,
                        contentDescription = null,
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(14.dp)
                    )

                    Spacer(
                        modifier = Modifier.width(5.dp)
                    )

                    Text(
                        text = window.app.title,
                        color = Color(0xFFC9D1D9),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }

        /*
         * REDE
         */
        Row(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(6.dp)
                )
                .clickable {
                    connected =
                        isNetworkConnected(context)
                }
                .padding(
                    horizontal = 7.dp,
                    vertical = 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector =
                    Icons.Filled.SignalWifi4Bar,
                contentDescription = "Rede",
                tint =
                    if (connected) {
                        Color(0xFF58A6FF)
                    } else {
                        Color(0xFF8B949E)
                    },
                modifier = Modifier.size(17.dp)
            )

            Spacer(
                modifier = Modifier.width(2.dp)
            )

            Text(
                text = "^",
                color =
                    if (connected) {
                        Color(0xFF58A6FF)
                    } else {
                        Color(0xFF8B949E)
                    },
                fontSize = 13.sp
            )
        }

        /*
         * MOUSE
         *
         * O estado pertence ao Desktop.
         * A Taskbar apenas exibe o estado
         * e solicita a alteração.
         */
        Row(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(6.dp)
                )
                .background(
                    if (mouseEnabled) {
                        Color(0xFF21262D)
                    } else {
                        Color.Transparent
                    }
                )
                .clickable {
                    onMouseToggle(!mouseEnabled)
                }
                .padding(
                    horizontal = 7.dp,
                    vertical = 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Filled.Mouse,
                contentDescription =
                    if (mouseEnabled) {
                        "Mouse ON"
                    } else {
                        "Mouse OFF"
                    },
                tint =
                    if (mouseEnabled) {
                        Color(0xFF58A6FF)
                    } else {
                        Color(0xFF8B949E)
                    },
                modifier = Modifier.size(17.dp)
            )

            Spacer(
                modifier = Modifier.width(4.dp)
            )

            Text(
                text =
                    if (mouseEnabled) {
                        "ON"
                    } else {
                        "OFF"
                    },
                color =
                    if (mouseEnabled) {
                        Color(0xFF58A6FF)
                    } else {
                        Color(0xFF8B949E)
                    },
                fontSize = 11.sp
            )
        }

        /*
         * BATERIA
         */
        Row(
            modifier = Modifier
                .padding(
                    horizontal = 5.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Filled.BatteryFull,
                contentDescription = "Bateria",
                tint =
                    when {
                        batteryLevel <= 15 ->
                            Color(0xFFF85149)

                        batteryLevel <= 30 ->
                            Color(0xFFD29922)

                        else ->
                            Color(0xFF8B949E)
                    },
                modifier = Modifier.size(18.dp)
            )

            Spacer(
                modifier = Modifier.width(3.dp)
            )

            Text(
                text = "$batteryLevel%",
                color = Color(0xFFC9D1D9),
                fontSize = 11.sp
            )
        }

        /*
         * RELÓGIO
         */
        Box(
            modifier = Modifier
                .padding(
                    start = 5.dp,
                    end = 7.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = now,
                color = Color(0xFFC9D1D9),
                fontSize = 12.sp
            )
        }
    }
}

/*
 * Verifica se existe uma rede ativa.
 */
private fun isNetworkConnected(
    context: Context
): Boolean {

    val connectivityManager =
        context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

    val network =
        connectivityManager.activeNetwork
            ?: return false

    val capabilities =
        connectivityManager
            .getNetworkCapabilities(network)
            ?: return false

    return capabilities.hasCapability(
        NetworkCapabilities.NET_CAPABILITY_INTERNET
    )
}

/*
 * Obtém a bateria atual.
 */
private fun readBatteryLevel(
    context: Context
): Int {

    val batteryManager =
        context.getSystemService(
            Context.BATTERY_SERVICE
        ) as BatteryManager

    return batteryManager.getIntProperty(
        BatteryManager.BATTERY_PROPERTY_CAPACITY
    ).coerceIn(0, 100)
}
