package com.minios.elizierdias.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(
    name = "minios_config"
)

object ConfigKeys {

    val WALLPAPER_ID =
        stringPreferencesKey("wallpaper_id")

    val WALLPAPER_URI =
        stringPreferencesKey("wallpaper_uri")

    val POWER_MODE =
        stringPreferencesKey("power_mode")
}

class MiniOSConfig(
    private val context: Context
) {

    /**
     * Wallpaper atualmente selecionado.
     *
     * Para wallpapers internos:
     * default_gradient
     * sunset
     * forest
     * violet
     *
     * Para fotografia personalizada:
     * custom_photo
     */
    val wallpaperId: Flow<String> =
        context.dataStore.data.map { preferences ->
            preferences[ConfigKeys.WALLPAPER_ID]
                ?: "default_gradient"
        }

    /**
     * Caminho da fotografia personalizada.
     *
     * Fica vazio quando está selecionado um
     * wallpaper interno do MiniOS.
     */
    val wallpaperUri: Flow<String> =
        context.dataStore.data.map { preferences ->
            preferences[ConfigKeys.WALLPAPER_URI]
                ?: ""
        }

    /**
     * Modo de desempenho do MiniOS.
     */
    val powerMode: Flow<PowerMode> =
        context.dataStore.data.map { preferences ->

            when (preferences[ConfigKeys.POWER_MODE]) {

                "PERFORMANCE" ->
                    PowerMode.PERFORMANCE

                "BATTERY_SAVER" ->
                    PowerMode.BATTERY_SAVER

                else ->
                    PowerMode.BALANCED
            }
        }

    /**
     * Seleciona um wallpaper interno.
     *
     * Ao selecionar um wallpaper interno,
     * qualquer fotografia personalizada deixa
     * de estar ativa.
     */
    suspend fun setWallpaper(id: String) {

        context.dataStore.edit { preferences ->

            preferences[ConfigKeys.WALLPAPER_ID] = id

            // Remove a fotografia personalizada.
            preferences[ConfigKeys.WALLPAPER_URI] = ""
        }
    }

    /**
     * Seleciona uma fotografia personalizada.
     *
     * O caminho recebido deve apontar para uma
     * cópia da imagem armazenada dentro do
     * armazenamento privado do MiniOS.
     */
    suspend fun setWallpaperUri(uri: String) {

        context.dataStore.edit { preferences ->

            preferences[ConfigKeys.WALLPAPER_ID] =
                "custom_photo"

            preferences[ConfigKeys.WALLPAPER_URI] =
                uri
        }
    }

    /**
     * Altera o modo de desempenho.
     */
    suspend fun setPowerMode(mode: PowerMode) {

        context.dataStore.edit { preferences ->

            preferences[ConfigKeys.POWER_MODE] =
                mode.name
        }
    }

    /**
     * Remove completamente o wallpaper
     * personalizado da configuração.
     *
     * O arquivo físico da imagem é tratado
     * pelo SettingsApp.
     */
    suspend fun clearCustomWallpaper() {

        context.dataStore.edit { preferences ->

            preferences[ConfigKeys.WALLPAPER_ID] =
                "default_gradient"

            preferences[ConfigKeys.WALLPAPER_URI] =
                ""
        }
    }
}
