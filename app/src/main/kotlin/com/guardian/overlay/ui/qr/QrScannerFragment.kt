package com.guardian.overlay.ui.qr

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.guardian.overlay.R
import com.guardian.overlay.databinding.FragmentQrScannerBinding
import com.guardian.overlay.detection.DetectorProvider
import com.guardian.overlay.qr.QrProcessor
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class QrScannerFragment : Fragment(R.layout.fragment_qr_scanner) {
    private var _binding: FragmentQrScannerBinding? = null
    private val binding get() = _binding!!

    private val detector by lazy { DetectorProvider.get(requireContext()) }
    private val qrProcessor = QrProcessor()
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val isAnalyzing = AtomicBoolean(false)
    private var lastDetectedTs = 0L

    private val cameraPermissionRequester = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            binding.cameraHint.text = getString(R.string.camera_permission_required)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentQrScannerBinding.bind(view)
        requestCameraPermissionOrStart()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun requestCameraPermissionOrStart() {
        val hasPermission = requireContext().checkSelfPermission(Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            startCamera()
        } else {
            cameraPermissionRequester.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        binding.cameraHint.text = getString(R.string.qr_camera_hint)
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture?.addListener({
            val cameraProvider = cameraProviderFuture?.get() ?: return@addListener
            bindCameraUseCases(cameraProvider)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { imageAnalysis ->
                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastDetectedTs < SCAN_COOLDOWN_MS || !isAnalyzing.compareAndSet(false, true)) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    qrProcessor.scanFromMediaImage(
                        mediaImage = mediaImage,
                        rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                        onSuccess = { payloads ->
                            if (payloads.isNotEmpty()) {
                                lastDetectedTs = System.currentTimeMillis()
                                val payload = payloads.first()
                                showVerdict(payload)
                            }
                            isAnalyzing.set(false)
                            imageProxy.close()
                        },
                        onError = {
                            isAnalyzing.set(false)
                            imageProxy.close()
                        }
                    )
                }
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        } catch (_: Throwable) {
            binding.cameraHint.text = getString(R.string.camera_start_failed)
        }
    }

    private fun showVerdict(payload: String) {
        if (!isAdded) return

        val result = detector.detect(payload, "qr_live_camera")
        val verdict = if (result.isScam) getString(R.string.verdict_scam) else getString(R.string.verdict_low_risk)
        val message = getString(
            R.string.qr_result_message,
            payload,
            result.prettyScore(),
            result.reasons.joinToString(separator = "\n- ", prefix = "- "),
            if (result.isScam) getString(R.string.prevention_steps_risky) else getString(R.string.prevention_steps_safe)
        )

        requireActivity().runOnUiThread {
            AlertDialog.Builder(requireContext())
                .setTitle(verdict)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    companion object {
        private const val SCAN_COOLDOWN_MS = 2_000L
    }
}
