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

    fun isTrustedContactEnabled(): Boolean = prefs.getBoolean(KEY_TRUSTED_CONTACT_ENABLED, false)

    fun setTrustedContactEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_TRUSTED_CONTACT_ENABLED, value).apply()
    }

    fun getTrustedContactName(): String = prefs.getString(KEY_TRUSTED_CONTACT_NAME, "")?.trim().orEmpty()

    fun setTrustedContactName(value: String) {
        prefs.edit().putString(KEY_TRUSTED_CONTACT_NAME, value.trim()).apply()
    }

    fun getTrustedContactNumber(): String = prefs.getString(KEY_TRUSTED_CONTACT_NUMBER, "")?.trim().orEmpty()

    fun setTrustedContactNumber(value: String) {
        prefs.edit().putString(KEY_TRUSTED_CONTACT_NUMBER, normalizePhoneNumber(value)).apply()
    }

    fun getTrustedContactActionMode(): TrustedContactActionMode {
        val raw = prefs.getString(KEY_TRUSTED_CONTACT_ACTION_MODE, TrustedContactActionMode.CHOOSER.value)
            ?: TrustedContactActionMode.CHOOSER.value
        return TrustedContactActionMode.from(raw)
    }

    fun setTrustedContactActionMode(mode: TrustedContactActionMode) {
        prefs.edit().putString(KEY_TRUSTED_CONTACT_ACTION_MODE, mode.value).apply()
    }

    fun hasTrustedContactConfigured(): Boolean {
        return isValidPhoneNumber(getTrustedContactNumber())
    }

    private fun normalizePhoneNumber(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return ""
        val keepPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (keepPlus && digits.isNotEmpty()) "+$digits" else digits
    }

    private fun isValidPhoneNumber(value: String): Boolean {
        return value.matches(Regex("^\\+?\\d{7,15}$"))
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
        private const val KEY_TRUSTED_CONTACT_ENABLED = "trusted_contact_enabled"
        private const val KEY_TRUSTED_CONTACT_NAME = "trusted_contact_name"
        private const val KEY_TRUSTED_CONTACT_NUMBER = "trusted_contact_number"
        private const val KEY_TRUSTED_CONTACT_ACTION_MODE = "trusted_contact_action_mode"
    }
}

enum class TrustedContactActionMode(val value: String) {
    CHOOSER("chooser"),
    SMS("sms"),
    CALL("call"),
    SHARE("share");

    companion object {
        fun from(value: String): TrustedContactActionMode {
            return entries.firstOrNull { it.value == value } ?: CHOOSER
        }
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
