package com.fth.pagescan.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import android.view.HapticFeedbackConstants
import com.fth.pagescan.R
import com.fth.pagescan.util.DocumentAnalyzer
import com.fth.pagescan.databinding.FragmentCameraBinding
import com.fth.pagescan.ui.custom.EdgeOverlayView
import com.fth.pagescan.ui.viewmodel.ScannerViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    // ViewModel Activity scope'unda oluşturulur ki veriler fragmentlar arası paylaşılabilsin
    private val viewModel: ScannerViewModel by activityViewModels()

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            binding.permissionLayout.visibility = View.GONE
            startCamera()
        } else {
            binding.permissionLayout.visibility = View.VISIBLE
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            processGalleryImage(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            binding.permissionLayout.visibility = View.GONE
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnCapture.setOnClickListener { takePhoto() }
        binding.btnGallery.setOnClickListener { openGallery() }
        
        binding.btnOpenSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", requireContext().packageName, null)
            startActivity(intent)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // ViewFinder boyutları 0 ise layout'u bekle
            binding.viewFinder.post {
                imageAnalysis.setAnalyzer(cameraExecutor, DocumentAnalyzer(
                    binding.viewFinder.width,
                    binding.viewFinder.height
                ) { points ->
                    activity?.runOnUiThread {
                        if (_binding != null) {
                            if (points.isNotEmpty()) {
                                binding.edgeOverlay.updateEdges(points)
                            } else {
                                binding.edgeOverlay.clearEdges()
                            }
                        }
                    }
                })
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture, imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("CameraFragment", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
        // Haptic Feedback for shutter
        binding.root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    
                    if (bitmap != null) {
                        viewModel.onImageCaptured(bitmap)
                        navigateToEdit()
                    } else {
                        Toast.makeText(requireContext(), "Failed to process image.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraFragment", "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null) ?: return null

        // Kamera rotasyonunu düzelt
        val rotationDegrees = image.imageInfo.rotationDegrees.toFloat()
        if (rotationDegrees != 0f) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees)
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun processGalleryImage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                viewModel.onImageCaptured(bitmap)
                navigateToEdit()
            }
        } catch (e: Exception) {
            Log.e("CameraFragment", "Error loading gallery image", e)
        }
    }

    private fun navigateToEdit() {
        findNavController().navigate(R.id.action_cameraFragment_to_documentEditFragment)
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}
