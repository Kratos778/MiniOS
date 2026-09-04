package com.minios.elizierdias.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(
    name = "minios_config",
)

object ConfigKeys {

    val WALLPAPER_ID =
        stringPreferencesKey("wallpaper_id")

    val WALLPAPER_URI =
        stringPreferencesKey("wallpaper_uri")

    val POWER_MODE =
        stringPreferencesKey("power_mode")

    /** true = vídeo wallpaper com som; false = mudo */
    val WALLPAPER_VIDEO_SOUND =
        booleanPreferencesKey("wallpaper_video_sound")
}

class MiniOSConfig(
    private val context: Context,
) {

    val wallpaperId: Flow<String> =
        context.dataStore.data.map { preferences ->
            preferences[ConfigKeys.WALLPAPER_ID]
                ?: "default_gradient"
        }

    val wallpaperUri: Flow<String> =
        context.dataStore.data.map { preferences ->
            preferences[ConfigKeys.WALLPAPER_URI]
                ?: ""
        }

    /** Som do vídeo wallpaper (default: desligado) */
    val wallpaperVideoSound: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[ConfigKeys.WALLPAPER_VIDEO_SOUND] ?: false
        }

    val powerMode: Flow<PowerMode> =
        context.dataStore.data.map { preferences ->
            when (preferences[ConfigKeys.POWER_MODE]) {
                "PERFORMANCE" -> PowerMode.PERFORMANCE
                "BATTERY_SAVER" -> PowerMode.BATTERY_SAVER
                else -> PowerMode.BALANCED
            }
        }

    suspend fun setWallpaper(id: String) {
        context.dataStore.edit { preferences ->
            preferences[ConfigKeys.WALLPAPER_ID] = id
            preferences[ConfigKeys.WALLPAPER_URI] = ""
        }
    }

    suspend fun setWallpaperUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[ConfigKeys.WALLPAPER_ID] = "custom_photo"
            preferences[ConfigKeys.WALLPAPER_URI] = uri
        }
    }

    suspend fun setWallpaperVideoSound(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ConfigKeys.WALLPAPER_VIDEO_SOUND] = enabled
        }
    }

    suspend fun setPowerMode(mode: PowerMode) {
        context.dataStore.edit { preferences ->
            preferences[ConfigKeys.POWER_MODE] = mode.name
        }
    }

    suspend fun clearCustomWallpaper() {
        context.dataStore.edit { preferences ->
            preferences[ConfigKeys.WALLPAPER_ID] = "default_gradient"
            preferences[ConfigKeys.WALLPAPER_URI] = ""
        }
    }
}
