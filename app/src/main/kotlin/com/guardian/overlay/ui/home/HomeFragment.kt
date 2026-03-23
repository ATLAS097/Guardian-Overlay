package com.guardian.overlay.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.guardian.overlay.R
import com.guardian.overlay.databinding.FragmentHomeBinding
import com.guardian.overlay.settings.AppSettingsStore

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)
        renderStatus()
        playEntranceAnimation()
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun renderStatus() {
        val context = context ?: return
        val settings = AppSettingsStore(context)

        val detection = if (settings.isDetectionEnabled()) {
            getString(R.string.status_enabled)
        } else {
            getString(R.string.status_disabled)
        }
        val onlineLookup = if (settings.isOnlineLookupEnabled()) {
            getString(R.string.status_enabled)
        } else {
            getString(R.string.status_disabled)
        }
        val assistiveTouch = if (settings.isAssistiveTouchEnabled()) {
            getString(R.string.status_enabled)
        } else {
            getString(R.string.status_disabled)
        }

        binding.statusValue.text = getString(
            R.string.home_status_template,
            detection,
            onlineLookup,
            assistiveTouch
        )
    }

    private fun playEntranceAnimation() {
        val cards = listOf(binding.homeHeroCard, binding.homeStatusCard, binding.homePreventionCard)
        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            card.translationY = 22f
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 70L)
                .setDuration(260)
                .start()
        }
    }
}
