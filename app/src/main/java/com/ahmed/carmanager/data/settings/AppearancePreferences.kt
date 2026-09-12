package com.ahmed.carmanager.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode { SYSTEM, LIGHT, DARK }
enum class AccentPalette { OCEAN, EMERALD, CRIMSON, VIOLET, AMBER, GRAPHITE }

class AppearancePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("carmanager_appearance", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _accentPalette = MutableStateFlow(readAccent())
    val accentPalette: StateFlow<AccentPalette> = _accentPalette.asStateFlow()

    fun setThemeMode(value: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        _themeMode.value = value
    }

    fun setAccentPalette(value: AccentPalette) {
        prefs.edit().putString(KEY_ACCENT, value.name).apply()
        _accentPalette.value = value
    }

    private fun readThemeMode(): AppThemeMode = runCatching {
        AppThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: AppThemeMode.SYSTEM.name)
    }.getOrDefault(AppThemeMode.SYSTEM)

    private fun readAccent(): AccentPalette = runCatching {
        AccentPalette.valueOf(prefs.getString(KEY_ACCENT, null) ?: AccentPalette.OCEAN.name)
    }.getOrDefault(AccentPalette.OCEAN)

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ACCENT = "accent_palette"
    }
}
