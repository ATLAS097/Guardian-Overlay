package com.guardian.overlay.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.guardian.overlay.R
import com.guardian.overlay.accessibility.AccessibilityState
import com.guardian.overlay.databinding.FragmentSettingsBinding
import com.guardian.overlay.service.GuardianAccessibilityService
import com.guardian.overlay.settings.AppSettingsStore
import com.guardian.overlay.settings.ThemeController
import com.guardian.overlay.settings.ThemeMode

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var settings: AppSettingsStore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)
        settings = AppSettingsStore(requireContext())
        bindSavedState()

        binding.openAccessibilitySettingsBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.saveSettingsBtn.setOnClickListener {
            val wantsDetection = binding.detectionEnabledSwitch.isChecked
            val wantsAssistive = binding.assistiveTouchSwitch.isChecked

            if ((wantsDetection || wantsAssistive) && !AccessibilityState.isGuardianServiceEnabled(requireContext())) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.accessibility_required_title)
                    .setMessage(R.string.accessibility_required_message)
                    .setPositiveButton(R.string.open_accessibility_settings) { _, _ ->
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                return@setOnClickListener
            }

            settings.setDetectionEnabled(binding.detectionEnabledSwitch.isChecked)
            settings.setOnlineLookupEnabled(binding.onlineLookupSwitch.isChecked)
            settings.setAssistiveTouchEnabled(binding.assistiveTouchSwitch.isChecked)
            settings.setThemeMode(selectedTheme())
            requireContext().sendBroadcast(Intent(GuardianAccessibilityService.ACTION_SYNC_ASSISTIVE_BUBBLE))
            ThemeController.apply(requireContext())
            requireActivity().recreate()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun bindSavedState() {
        binding.detectionEnabledSwitch.isChecked = settings.isDetectionEnabled()
        binding.onlineLookupSwitch.isChecked = settings.isOnlineLookupEnabled()
        binding.assistiveTouchSwitch.isChecked = settings.isAssistiveTouchEnabled()

        when (settings.getThemeMode()) {
            ThemeMode.SYSTEM -> binding.themeSystem.isChecked = true
            ThemeMode.LIGHT -> binding.themeLight.isChecked = true
            ThemeMode.DARK -> binding.themeDark.isChecked = true
        }
    }

    private fun selectedTheme(): ThemeMode {
        return when (binding.themeChoice.checkedRadioButtonId) {
            binding.themeLight.id -> ThemeMode.LIGHT
            binding.themeDark.id -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }
}
