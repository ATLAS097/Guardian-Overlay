package com.guardian.overlay

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.guardian.overlay.accessibility.AccessibilityState
import com.guardian.overlay.databinding.ActivityMainBinding
import com.guardian.overlay.service.GuardianAccessibilityService
import com.guardian.overlay.settings.AppSettingsStore
import com.guardian.overlay.settings.ThemeController

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsStore: AppSettingsStore
    private lateinit var navController: androidx.navigation.NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeController.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settingsStore = AppSettingsStore(this)

        val navHost = supportFragmentManager.findFragmentById(R.id.navHostContainer) as NavHostFragment
        navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        binding.assistiveLauncherBtn.setOnClickListener {
            openAssistiveBubble()
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun openAssistiveBubble() {
        if (!AccessibilityState.isGuardianServiceEnabled(this)) {
            Toast.makeText(this, getString(R.string.accessibility_required_for_assistive), Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        settingsStore.setAssistiveTouchEnabled(true)
        sendBroadcast(Intent(GuardianAccessibilityService.ACTION_SHOW_ASSISTIVE_BUBBLE))
        Toast.makeText(this, getString(R.string.assistive_bubble_opened), Toast.LENGTH_SHORT).show()
    }

    private fun handleIntent(intent: Intent?) {
        val tab = intent?.getStringExtra(EXTRA_OPEN_TAB) ?: return
        when (tab) {
            TAB_QR -> binding.bottomNav.selectedItemId = R.id.qrScannerFragment
            TAB_GALLERY -> binding.bottomNav.selectedItemId = R.id.galleryFragment
            TAB_HOME -> binding.bottomNav.selectedItemId = R.id.homeFragment
            TAB_SETTINGS -> binding.bottomNav.selectedItemId = R.id.settingsFragment
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_HOME = "home"
        const val TAB_QR = "qr"
        const val TAB_GALLERY = "gallery"
        const val TAB_SETTINGS = "settings"
    }
}
