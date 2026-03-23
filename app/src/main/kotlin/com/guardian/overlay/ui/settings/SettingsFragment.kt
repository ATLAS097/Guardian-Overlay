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
    private var isBindingState = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)
        settings = AppSettingsStore(requireContext())
        bindSavedState()
        bindImmediateListeners()

        binding.openAccessibilitySettingsBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun bindSavedState() {
        isBindingState = true
        binding.detectionEnabledSwitch.isChecked = settings.isDetectionEnabled()
        binding.onlineLookupSwitch.isChecked = settings.isOnlineLookupEnabled()
        binding.assistiveTouchSwitch.isChecked = settings.isAssistiveTouchEnabled()

        when (settings.getThemeMode()) {
            ThemeMode.SYSTEM -> binding.themeSystem.isChecked = true
            ThemeMode.LIGHT -> binding.themeLight.isChecked = true
            ThemeMode.DARK -> binding.themeDark.isChecked = true
        }
        isBindingState = false
    }

    private fun selectedTheme(): ThemeMode {
        return when (binding.themeChoice.checkedRadioButtonId) {
            binding.themeLight.id -> ThemeMode.LIGHT
            binding.themeDark.id -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    private fun bindImmediateListeners() {
        binding.detectionEnabledSwitch.setOnCheckedChangeListener { _, enabled ->
            if (isBindingState) return@setOnCheckedChangeListener
            if (enabled && !AccessibilityState.isGuardianServiceEnabled(requireContext())) {
                revertDetectionSwitch()
                showAccessibilityRequiredDialog()
                return@setOnCheckedChangeListener
            }
            settings.setDetectionEnabled(enabled)
        }

        binding.assistiveTouchSwitch.setOnCheckedChangeListener { _, enabled ->
            if (isBindingState) return@setOnCheckedChangeListener
            if (enabled && !AccessibilityState.isGuardianServiceEnabled(requireContext())) {
                revertAssistiveSwitch()
                showAccessibilityRequiredDialog()
                return@setOnCheckedChangeListener
            }
            settings.setAssistiveTouchEnabled(enabled)
            requireContext().sendBroadcast(Intent(GuardianAccessibilityService.ACTION_SYNC_ASSISTIVE_BUBBLE))
        }

        binding.onlineLookupSwitch.setOnCheckedChangeListener { _, enabled ->
            if (isBindingState) return@setOnCheckedChangeListener
            settings.setOnlineLookupEnabled(enabled)
        }

        binding.themeChoice.setOnCheckedChangeListener { _, _ ->
            if (isBindingState) return@setOnCheckedChangeListener
            val mode = selectedTheme()
            if (settings.getThemeMode() == mode) return@setOnCheckedChangeListener
            settings.setThemeMode(mode)
            ThemeController.apply(requireContext())
            requireActivity().recreate()
        }
    }

    private fun showAccessibilityRequiredDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.accessibility_required_title)
            .setMessage(R.string.accessibility_required_message)
            .setPositiveButton(R.string.open_accessibility_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun revertDetectionSwitch() {
        isBindingState = true
        binding.detectionEnabledSwitch.isChecked = false
        isBindingState = false
    }

    private fun revertAssistiveSwitch() {
        isBindingState = true
        binding.assistiveTouchSwitch.isChecked = false
        isBindingState = false
    }
}
