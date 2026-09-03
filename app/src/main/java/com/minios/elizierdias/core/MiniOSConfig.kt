package com.minios.elizierdias.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "minios_config")

object ConfigKeys {
    val WALLPAPER_ID = stringPreferencesKey("wallpaper_id")
    val WALLPAPER_URI = stringPreferencesKey("wallpaper_uri")
    val POWER_MODE = stringPreferencesKey("power_mode")
}

class MiniOSConfig(private val context: Context) {

    val wallpaperId: Flow<String> =
        context.dataStore.data.map {
            it[ConfigKeys.WALLPAPER_ID] ?: "default_gradient"
        }

    val wallpaperUri: Flow<String> =
        context.dataStore.data.map {
            it[ConfigKeys.WALLPAPER_URI] ?: ""
        }

    val powerMode: Flow<PowerMode> =
        context.dataStore.data.map {
            when (it[ConfigKeys.POWER_MODE]) {
                "PERFORMANCE" -> PowerMode.PERFORMANCE
                "BATTERY_SAVER" -> PowerMode.BATTERY_SAVER
                else -> PowerMode.BALANCED
            }
        }

    suspend fun setWallpaper(id: String) {
        context.dataStore.edit {
            it[ConfigKeys.WALLPAPER_ID] = id
            it[ConfigKeys.WALLPAPER_URI] = ""
        }
    }

    suspend fun setWallpaperUri(uri: String) {
        context.dataStore.edit {
            it[ConfigKeys.WALLPAPER_ID] = "custom_photo"
            it[ConfigKeys.WALLPAPER_URI] = uri
        }
    }

    suspend fun setPowerMode(mode: PowerMode) {
        context.dataStore.edit {
            it[ConfigKeys.POWER_MODE] = mode.name
        }
    }
}
