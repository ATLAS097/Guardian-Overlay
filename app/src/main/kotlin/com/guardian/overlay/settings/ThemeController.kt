package com.guardian.overlay.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeController {
    fun apply(context: Context) {
        val mode = AppSettingsStore(context).getThemeMode().toNightMode()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
