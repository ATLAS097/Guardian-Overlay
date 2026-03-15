package com.guardian.overlay.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class AppSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isDetectionEnabled(): Boolean = prefs.getBoolean(KEY_DETECTION_ENABLED, true)

    fun setDetectionEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_DETECTION_ENABLED, value).apply()
    }

    fun isOnlineLookupEnabled(): Boolean = prefs.getBoolean(KEY_ONLINE_LOOKUP_ENABLED, false)

    fun setOnlineLookupEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ONLINE_LOOKUP_ENABLED, value).apply()
    }

    fun isAssistiveTouchEnabled(): Boolean = prefs.getBoolean(KEY_ASSISTIVE_TOUCH_ENABLED, false)

    fun setAssistiveTouchEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ASSISTIVE_TOUCH_ENABLED, value).apply()
    }

    fun getThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.value) ?: ThemeMode.SYSTEM.value
        return ThemeMode.from(raw)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.value).apply()
    }

    companion object {
        private const val PREF_NAME = "guardian_settings"
        private const val KEY_DETECTION_ENABLED = "detection_enabled"
        private const val KEY_ONLINE_LOOKUP_ENABLED = "online_lookup_enabled"
        private const val KEY_ASSISTIVE_TOUCH_ENABLED = "assistive_touch_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}

enum class ThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun toNightMode(): Int = when (this) {
        SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }

    companion object {
        fun from(value: String): ThemeMode {
            return entries.firstOrNull { it.value == value } ?: SYSTEM
        }
    }
}
