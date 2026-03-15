package com.guardian.overlay.ui.gallery

import android.Manifest
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.guardian.overlay.R
import com.guardian.overlay.databinding.FragmentGalleryBinding
import com.guardian.overlay.detection.DetectorProvider
import com.guardian.overlay.detection.PhrasePackLoader
import com.guardian.overlay.ocr.OcrProcessor
import com.guardian.overlay.processing.RiskTextHighlighter

class GalleryFragment : Fragment(R.layout.fragment_gallery) {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private val detector by lazy { DetectorProvider.get(requireContext()) }
    private val riskKeywords by lazy {
        RiskTextHighlighter.buildKeywordSet(PhrasePackLoader(requireContext()).loadRules())
    }
    private val ocrProcessor = OcrProcessor()
    private lateinit var adapter: GalleryImageAdapter

    private val mediaPermissionRequester = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadGalleryItems()
        } else {
            binding.galleryState.text = getString(R.string.gallery_permission_required)
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGalleryBinding.bind(view)

        adapter = GalleryImageAdapter(requireContext().contentResolver) { image ->
            scanImage(image.uri)
        }

        binding.galleryRecycler.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.galleryRecycler.adapter = adapter

        binding.retryPermissionBtn.setOnClickListener {
            requestPermissionAndLoad()
        }

        requestPermissionAndLoad()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun requestPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (requireContext().checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            loadGalleryItems()
        } else {
            mediaPermissionRequester.launch(permission)
        }
    }

    private fun loadGalleryItems() {
        binding.progressBar.visibility = View.VISIBLE
        binding.galleryState.visibility = View.GONE

        val resolver = requireContext().contentResolver
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        val items = mutableListOf<GalleryImageItem>()
        resolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && items.size < 400) {
                val id = cursor.getLong(idIdx)
                val contentUri = Uri.withAppendedPath(uri, id.toString())
                items.add(GalleryImageItem(id = id, uri = contentUri))
            }
        }

        binding.progressBar.visibility = View.GONE

        if (items.isEmpty()) {
            binding.galleryState.visibility = View.VISIBLE
            binding.galleryState.text = getString(R.string.gallery_empty)
        } else {
            binding.galleryState.visibility = View.GONE
        }

        adapter.submitList(items)
    }

    private fun scanImage(imageUri: Uri) {
        binding.scanState.text = getString(R.string.running_ocr)
        ocrProcessor.extractScanDataFromImageUri(
            context = requireContext(),
            imageUri = imageUri,
            onSuccess = { scanData ->
                val result = detector.detect(scanData.text, "gallery_image")
                val highlightedBoxes = RiskTextHighlighter.findSuspiciousBoxes(scanData.lines, riskKeywords)
                val imageSize = getImageDimensions(imageUri)
                binding.scanState.text = getString(R.string.gallery_tap_to_scan)
                showResultDialog(
                    title = if (result.isScam) getString(R.string.verdict_scam) else getString(R.string.verdict_low_risk),
                    message = getString(
                        R.string.gallery_result_message,
                        result.prettyScore(),
                        result.reasons.joinToString(separator = "\n- ", prefix = "- "),
                        if (result.isScam) getString(R.string.prevention_steps_risky) else getString(R.string.prevention_steps_safe)
                    ),
                    imageUri = imageUri,
                    highlightedBoxes = highlightedBoxes,
                    imageWidth = imageSize.first,
                    imageHeight = imageSize.second
                )
            },
            onError = { ex ->
                binding.scanState.text = getString(R.string.ocr_failed, ex.message ?: getString(R.string.unknown_error))
            }
        )
    }

    private fun getImageDimensions(imageUri: Uri): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            requireContext().contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
            val width = opts.outWidth.coerceAtLeast(1)
            val height = opts.outHeight.coerceAtLeast(1)
            Pair(width, height)
        } catch (_: Throwable) {
            Pair(1, 1)
        }
    }

    private fun showResultDialog(
        title: String,
        message: String,
        imageUri: Uri,
        highlightedBoxes: List<Rect>,
        imageWidth: Int,
        imageHeight: Int
    ) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_gallery_scan_result, null)
        val imageView = dialogView.findViewById<android.widget.ImageView>(R.id.resultImage)
        val overlay = dialogView.findViewById<ScamHighlightOverlayView>(R.id.highlightOverlay)
        val highlightSummary = dialogView.findViewById<android.widget.TextView>(R.id.highlightSummary)
        val resultMessage = dialogView.findViewById<android.widget.TextView>(R.id.resultMessage)

        imageView.setImageURI(imageUri)
        overlay.setHighlightData(imageWidth, imageHeight, highlightedBoxes)
        highlightSummary.text = if (highlightedBoxes.isEmpty()) {
            getString(R.string.gallery_no_highlighted_regions)
        } else {
            getString(R.string.gallery_highlighted_regions, highlightedBoxes.size)
        }
        resultMessage.text = message

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.ok, null)
            .show()

        val width = (resources.displayMetrics.widthPixels * 0.95f).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
