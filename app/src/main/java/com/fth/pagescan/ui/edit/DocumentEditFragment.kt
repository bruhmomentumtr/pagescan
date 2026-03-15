package com.fth.pagescan.ui.edit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import android.view.HapticFeedbackConstants
import com.fth.pagescan.R
import com.fth.pagescan.data.local.entity.FilterType
import com.fth.pagescan.databinding.FragmentDocumentEditBinding
import com.fth.pagescan.ui.viewmodel.ScannerViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DocumentEditFragment : Fragment() {

    private var _binding: FragmentDocumentEditBinding? = null
    private val binding get() = _binding!!

    // Ortak ViewModel
    private val viewModel: ScannerViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDocumentEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnNext.setOnClickListener {
            val points = binding.cropOverlay.getMappedCropPoints()
            val isOcrEnabled = binding.switchOcr.isChecked
            val isPdfEnabled = binding.switchPdf.isChecked
            
            val filterType = when (binding.chipGroupFilters.checkedChipId) {
                R.id.chipFilterMagicColor -> FilterType.MAGIC_COLOR
                R.id.chipFilterBW -> FilterType.B_AND_W
                R.id.chipFilterGrayscale -> FilterType.GRAYSCALE
                R.id.chipFilterOriginal -> FilterType.NONE
                else -> FilterType.B_AND_W
            }
            
            viewModel.processDocument(points, filterType, isOcrEnabled, isPdfEnabled)
        }

        // ViewModel StateFlow'u dinleyelim
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is ScannerViewModel.ScannerState.ImageCaptured -> {
                        // Yeni fotoğraf geldiğinde ImageView ve Kırpma aracına yükle
                        binding.imageView.setImageBitmap(state.originalBitmap)
                        binding.cropOverlay.setBitmap(state.originalBitmap)
                        binding.cropOverlay.visibility = View.VISIBLE
                    }
                    is ScannerViewModel.ScannerState.ProcessingImage -> {
                        binding.imageProgressIndicator.visibility = View.VISIBLE
                        binding.pdfProgressIndicator.visibility = View.GONE
                        binding.btnNext.isEnabled = false
                    }
                    is ScannerViewModel.ScannerState.ProcessingOcr -> {
                        binding.imageProgressIndicator.visibility = View.GONE
                        Snackbar.make(binding.root, "Metinler okunuyor...", Snackbar.LENGTH_INDEFINITE).show()
                    }
                    is ScannerViewModel.ScannerState.ExportingPdf -> {
                        binding.pdfProgressIndicator.visibility = View.VISIBLE
                    }
                    is ScannerViewModel.ScannerState.SavedSuccessfully -> {
                        binding.pdfProgressIndicator.visibility = View.GONE
                        binding.btnNext.isEnabled = true
                        binding.root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        Toast.makeText(context, "PDF Export Completed!", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                    is ScannerViewModel.ScannerState.Error -> {
                        binding.imageProgressIndicator.visibility = View.GONE
                        binding.pdfProgressIndicator.visibility = View.GONE
                        binding.btnNext.isEnabled = true
                        Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        binding.imageProgressIndicator.visibility = View.GONE
                        binding.pdfProgressIndicator.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
