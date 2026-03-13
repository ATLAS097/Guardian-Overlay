package com.guardian.overlay.detection

import android.content.Context

object DetectorProvider {
    @Volatile
    private var instance: ScamDetector? = null

    fun get(context: Context): ScamDetector {
        return instance ?: synchronized(this) {
            instance ?: FakeMiniTfliteDetector(context.applicationContext).also { instance = it }
        }
    }
}
