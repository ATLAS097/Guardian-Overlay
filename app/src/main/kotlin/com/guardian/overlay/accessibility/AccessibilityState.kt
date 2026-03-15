package com.guardian.overlay.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityState {
    fun isGuardianServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(
            context,
            "com.guardian.overlay.service.GuardianAccessibilityService"
        ).flattenToString()

        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
