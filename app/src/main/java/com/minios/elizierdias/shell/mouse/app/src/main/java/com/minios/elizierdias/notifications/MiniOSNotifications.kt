package com.minios.elizierdias.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MiniOSNotification(
    val title: String,
    val message: String,
)

object MiniOSNotifications {

    private var currentNotification: MiniOSNotification? = null

    fun show(
        title: String,
        message: String,
    ) {
        currentNotification = MiniOSNotification(
            title = title,
            message = message,
        )
    }

    fun dismiss() {
        currentNotification = null
    }
}

@Composable
fun MiniOSNotificationHost(
    notification: MiniOSNotification?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = notification != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        notification?.let { item ->

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF202124),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(
                        horizontal = 14.dp,
                        vertical = 10.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {

                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 13.sp,
                    )

                    Text(
                        text = item.message,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                    )
                }

                Spacer(
                    modifier = Modifier.width(8.dp)
                )
            }
        }
    }
}
