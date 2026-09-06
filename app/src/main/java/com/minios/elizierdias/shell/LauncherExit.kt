/*
 * Copyright (c) 2026 Elizier Layerti Gungui Dias
 * MiniOS - Desktop-style environment for Android
 *
 * PROPRIETARY SOFTWARE — All Rights Reserved.
 */

package com.minios.elizierdias.shell

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast

/**
 * Sai do MiniOS para o launcher Android normal.
 * Se o MiniOS for o HOME predefinido, escolhe outro app HOME (nao o MiniOS).
 */
fun exitMiniOS(context: Context) {
    val pm = context.packageManager
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    val candidates = pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
        .filter { it.activityInfo.packageName != context.packageName }

    val pick = candidates.firstOrNull()
    if (pick != null) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            component = ComponentName(
                pick.activityInfo.packageName,
                pick.activityInfo.name,
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Nao foi possivel sair do MiniOS", Toast.LENGTH_SHORT).show()
        }
    } else {
        // Nenhum outro launcher — apenas fecha a activity
        Toast.makeText(
            context,
            "Define outro ecran inicial nas definicoes do telefone se ficares preso",
            Toast.LENGTH_LONG,
        ).show()
    }

    (context as? Activity)?.finish()
}
