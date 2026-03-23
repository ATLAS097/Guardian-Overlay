package com.guardian.overlay.ui.trusted

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.guardian.overlay.R
import com.guardian.overlay.databinding.FragmentTrustedContactBinding
import com.guardian.overlay.settings.AppSettingsStore
import com.guardian.overlay.settings.TrustedContactActionMode

class TrustedContactFragment : Fragment(R.layout.fragment_trusted_contact) {
    private var _binding: FragmentTrustedContactBinding? = null
    private val binding get() = _binding!!
    private lateinit var settings: AppSettingsStore
    private var isBindingState = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTrustedContactBinding.bind(view)
        settings = AppSettingsStore(requireContext())

        bindSavedState()
        bindListeners()
        playEntranceAnimation()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun bindSavedState() {
        isBindingState = true
        binding.trustedContactEnabledSwitch.isChecked = settings.isTrustedContactEnabled()
        binding.trustedContactNameInput.setText(settings.getTrustedContactName())
        binding.trustedContactNumberInput.setText(settings.getTrustedContactNumber())

        when (settings.getTrustedContactActionMode()) {
            TrustedContactActionMode.CHOOSER -> binding.actionModeChooser.isChecked = true
            TrustedContactActionMode.SMS -> binding.actionModeSms.isChecked = true
            TrustedContactActionMode.CALL -> binding.actionModeCall.isChecked = true
            TrustedContactActionMode.SHARE -> binding.actionModeShare.isChecked = true
        }
        isBindingState = false
    }

    private fun bindListeners() {
        binding.trustedContactEnabledSwitch.setOnCheckedChangeListener { _, enabled ->
            if (isBindingState) return@setOnCheckedChangeListener
            val number = binding.trustedContactNumberInput.text?.toString()?.trim().orEmpty()
            if (enabled && !isValidPhoneNumber(number)) {
                isBindingState = true
                binding.trustedContactEnabledSwitch.isChecked = false
                isBindingState = false
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.trusted_contact_title)
                    .setMessage(R.string.settings_trusted_contact_invalid)
                    .setPositiveButton(R.string.ok, null)
                    .show()
                return@setOnCheckedChangeListener
            }
            settings.setTrustedContactEnabled(enabled)
        }

        binding.trustedContactNameInput.doAfterTextChanged { text ->
            if (isBindingState) return@doAfterTextChanged
            settings.setTrustedContactName(text?.toString().orEmpty())
        }

        binding.trustedContactNumberInput.doAfterTextChanged { text ->
            if (isBindingState) return@doAfterTextChanged
            val number = text?.toString().orEmpty()
            settings.setTrustedContactNumber(number)
            if (settings.isTrustedContactEnabled() && !isValidPhoneNumber(number)) {
                isBindingState = true
                binding.trustedContactEnabledSwitch.isChecked = false
                isBindingState = false
                settings.setTrustedContactEnabled(false)
            }
        }

        binding.actionModeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isBindingState) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                binding.actionModeSms.id -> TrustedContactActionMode.SMS
                binding.actionModeCall.id -> TrustedContactActionMode.CALL
                binding.actionModeShare.id -> TrustedContactActionMode.SHARE
                else -> TrustedContactActionMode.CHOOSER
            }
            settings.setTrustedContactActionMode(mode)
        }
    }

    private fun playEntranceAnimation() {
        val sections = listOf(binding.trustedHeroCard, binding.trustedContactCard, binding.trustedActionCard)
        sections.forEachIndexed { index, section ->
            section.alpha = 0f
            section.translationY = 20f
            section.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 70L)
                .setDuration(250)
                .start()
        }
    }

    private fun isValidPhoneNumber(number: String): Boolean {
        val normalized = number.trim().replace(Regex("[^+\\d]"), "")
        return normalized.matches(Regex("^\\+?\\d{7,15}$"))
    }
}
