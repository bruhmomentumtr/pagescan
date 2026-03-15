package com.fth.pagescan.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.fth.pagescan.data.local.entity.FilterType
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import android.app.Application
import android.graphics.PointF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fth.pagescan.data.local.entity.DocumentEntity
import com.fth.pagescan.data.local.entity.PageEntity
import com.fth.pagescan.util.ImageProcessor
import com.fth.pagescan.util.PdfExporter
import com.fth.pagescan.util.TextRecognizerHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * View model that manages the state of the document scanning process.
 * Keeps track of the raw image, cropped image, applied filters, and OCR results.
 */
class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    // Scanner durumlarını temsil eden Sealed Class
    sealed class ScannerState {
        object Idle : ScannerState()
        object Loading : ScannerState()
        data class ImageCaptured(val originalBitmap: Bitmap) : ScannerState()
        data class ImageCropped(val croppedBitmap: Bitmap) : ScannerState()
        data class ProcessingImage(val progress: Int) : ScannerState()
        data class ProcessingOcr(val progress: Int) : ScannerState()
        data class OcrCompleted(val recognizedText: Text, val plainText: String) : ScannerState()
        object ExportingPdf : ScannerState()
        data class Error(val message: String) : ScannerState()
        object SavedSuccessfully : ScannerState()
    }

    private val _uiState = MutableStateFlow<ScannerState>(ScannerState.Idle)
    val uiState: StateFlow<ScannerState> = _uiState.asStateFlow()

    // Aktif sayfa verileri
    var currentOriginalBitmap: Bitmap? = null
    var currentCroppedBitmap: Bitmap? = null
    var currentFilteredBitmap: Bitmap? = null
    var currentFilterType: FilterType = FilterType.MAGIC_COLOR
    var currentOcrResult: Text? = null
    var currentRecognizedText: String = ""

    // Kameredan fotoğraf çekildiğinde veya galeriden seçildiğinde çağrılır
    fun onImageCaptured(bitmap: Bitmap) {
        currentOriginalBitmap = bitmap
        _uiState.value = ScannerState.ImageCaptured(bitmap)
    }

    // Kırpma işlemi tamamlandığında çağrılır
    fun onImageCropped(bitmap: Bitmap) {
        currentCroppedBitmap = bitmap
        currentFilteredBitmap = bitmap // Başlangıçta filtrelenmemiş hali eşittir
        _uiState.value = ScannerState.ImageCropped(bitmap)
    }
    
    // OCR işlemi başladığında çağırılır
    fun onOcrStarted() {
        _uiState.value = ScannerState.ProcessingOcr(0)
    }

    // OCR bittiğinde rıza ile tetiklenebilir
    fun onOcrCompleted(textResult: Text, plainText: String) {
        currentOcrResult = textResult
        currentRecognizedText = plainText
        _uiState.value = ScannerState.OcrCompleted(textResult, plainText)
    }

    // Tam akış işlemi: Kırp -> Filtrele -> Kaydet -> OCR -> PDF Export
    fun processDocument(corners: Array<PointF>, filterType: FilterType, isOcrEnabled: Boolean = false, isPdfEnabled: Boolean = true) {
        val originalBitmap = currentOriginalBitmap ?: return
        
        viewModelScope.launch {
            try {
                // 1. Resim İşleme
                _uiState.value = ScannerState.ProcessingImage(0)
                val imageProcessor = ImageProcessor(getApplication())
                val processedBitmap = imageProcessor.applyPerspectiveTransform(originalBitmap, corners) 
                    ?: throw Exception("Failed to crop image")
                    
                val filteredBitmap = imageProcessor.applyFilter(processedBitmap, filterType) ?: processedBitmap
                
                currentCroppedBitmap = processedBitmap
                currentFilteredBitmap = filteredBitmap
                currentFilterType = filterType
                
                val imagePath = imageProcessor.saveBitmapToInternalStorage(filteredBitmap) 
                    ?: throw Exception("Failed to save image")
                
                // 2. OCR Okuma (Opsiyonel)
                var textResult: Text? = null
                var extractedText = ""
                
                if (isOcrEnabled) {
                    _uiState.value = ScannerState.ProcessingOcr(0)
                    val textHelper = TextRecognizerHelper()
                    textResult = textHelper.recognizeText(filteredBitmap)
                    extractedText = textHelper.extractPlainText(textResult)
                    
                    currentOcrResult = textResult
                    currentRecognizedText = extractedText
                }
                
                if (textResult != null) {
                    _uiState.value = ScannerState.OcrCompleted(textResult, extractedText)
                    delay(1500) // Snackbar'ın okunabilmesi için UX bekletmesi
                }
                
                val document = DocumentEntity(title = "Scanned Doc ${System.currentTimeMillis()}")
                val page = PageEntity(
                    documentId = document.documentId,
                    pageNumber = 1,
                    originalImagePath = "",
                    processedImagePath = imagePath,
                    recognizedText = extractedText
                )

                // 3. Galeriye Kaydetme
                imageProcessor.saveBitmapToGallery(filteredBitmap, document.title)
                
                // 4. PDF Aktarımı (Opsiyonel)
                if (isPdfEnabled) {
                    _uiState.value = ScannerState.ExportingPdf
                    val ocrMap = if (textResult != null) mapOf(page.pageId to textResult) else emptyMap()
                    val pdfExporter = PdfExporter(getApplication())
                    pdfExporter.exportToPdf(document, listOf(page), ocrMap)
                }
                
                _uiState.value = ScannerState.SavedSuccessfully
                
            } catch (e: Exception) {
                _uiState.value = ScannerState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
    
    // Yükleme ekranı göstermek için
    fun setLoading() {
        _uiState.value = ScannerState.Loading
    }
    
    fun setSavedSuccessfully() {
        _uiState.value = ScannerState.SavedSuccessfully
    }

    fun setError(message: String) {
        _uiState.value = ScannerState.Error(message)
    }

    fun resetState() {
        currentOriginalBitmap = null
        currentCroppedBitmap = null
        currentFilteredBitmap = null
        currentFilterType = FilterType.MAGIC_COLOR
        currentOcrResult = null
        currentRecognizedText = ""
        _uiState.value = ScannerState.Idle
    }
}
