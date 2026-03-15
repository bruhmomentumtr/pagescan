package com.fth.pagescan.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.fth.pagescan.data.local.entity.DocumentEntity
import com.fth.pagescan.data.local.entity.PageEntity
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Utility class for exporting scanned pages as a "Sandwich" PDF.
 * A Sandwich PDF contains the image visibly and the OCR recognized text invisibly layered on top
 * in its correct coordinates, making the PDF both visually accurate and searchable/selectable.
 */
class PdfExporter(private val context: Context) {

    private val TAG = "PdfExporter"
    // Standard A4 dimensions in points (1 point = 1/72 inch)
    private val A4_WIDTH = 595
    private val A4_HEIGHT = 842

    // Paint used to render the OCR text invisibly (Alpha = 0)
    // Needs anti-alias or fake bold depending on fonts to align properly
    private val invisibleTextPaint = Paint().apply {
        color = Color.argb(1, 255, 255, 255) // Görünmez (0 Alpha bazen silinebiliyor, 1 Alpha garantili)
        isFakeBoldText = true // Seçilebilirliği artırmak için biraz kalın yapabiliriz
        textAlign = Paint.Align.LEFT
    }

    /**
     * Creates a PDF document containing the given pages and saves it to the device's scoped storage (MediaStore).
     * Works on Dispatchers.IO for safe file operations and memory management.
     *
     * @param document The parent document entity (used for naming).
     * @param pages The list of pages belonging to this document.
     * @param ocrResults Pre-computed ML Kit OCR [Text] results mapping page IDs to OCR data.
     * @return Result object containing the content URI string if successful, or null.
     */
    suspend fun exportToPdf(
        document: DocumentEntity,
        pages: List<PageEntity>,
        ocrResults: Map<String, Text> = emptyMap()
    ): String? = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext null

        val pdfDocument = PdfDocument()

        try {
            for ((index, page) in pages.sortedBy { it.pageNumber }.withIndex()) {
                val imageFile = File(page.processedImagePath)
                if (!imageFile.exists()) {
                    Log.w(TAG, "Image file not found for page: ${page.pageNumber}. Skipping.")
                    continue
                }

                // 1. Bitmap'i Belleğe Yükle (OutOfMemory önlemi için bounds check yapılabilir ama şimdilik direkt alıyoruz)
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: continue

                // 2. Yüksek Çözünürlüğü Korumak İçin Dinamik PDF Boyutu
                // Standart A4 (595x842) yerine sayfa boyutunu resmin kendi piksellerinde ayarlıyoruz.
                val MAX_DIMENSION = 2500f
                val originalWidth = bitmap.width.toFloat()
                val originalHeight = bitmap.height.toFloat()
                
                var scale = 1f
                if (originalWidth > MAX_DIMENSION || originalHeight > MAX_DIMENSION) {
                    scale = kotlin.math.min(MAX_DIMENSION / originalWidth, MAX_DIMENSION / originalHeight)
                }
                
                val pageWidth = (originalWidth * scale).toInt()
                val pageHeight = (originalHeight * scale).toInt()

                // 3. PDF Sayfasını Oluştur
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                // 4. Katman 1 (Alt): Yüksek Çözünürlüklü Resmi Çiz
                val destRect = Rect(0, 0, pageWidth, pageHeight)
                
                if (scale < 1f) {
                    val scaledDownBitmap = Bitmap.createScaledBitmap(bitmap, pageWidth, pageHeight, true)
                    canvas.drawBitmap(scaledDownBitmap, null, destRect, null)
                    scaledDownBitmap.recycle()
                } else {
                    canvas.drawBitmap(bitmap, null, destRect, null)
                }

                // 5. Katman 2 (Üst): "Sandwich" OCR Metni (Şeffaf)
                val ocrData = ocrResults[page.pageId]
                if (ocrData != null) {
                    drawInvisibleOcrText(canvas, ocrData, scale, 0, 0)
                }

                // 6. Belleği Temizle ve Sayfayı Kapat
                pdfDocument.finishPage(pdfPage)
                bitmap.recycle() // OOM'ye karşı en kritik hamle
            }

            // 7. Scoped Storage (MediaStore) ile PDF'i kaydet (Android 10+)
            return@withContext savePdfToMediaStore(pdfDocument, document.title)

        } catch (e: Exception) {
            Log.e(TAG, "Failed creating PDF", e)
            return@withContext null
        } finally {
            pdfDocument.close()
        }
    }

    /**
     * Draws extracted text exactly where it appears in the image, but fully transparent.
     */
    private fun drawInvisibleOcrText(
        canvas: Canvas,
        ocrResult: Text,
        scale: Float,
        marginLeft: Int,
        marginTop: Int
    ) {
        for (block in ocrResult.textBlocks) {
            for (line in block.lines) {
                // OCR bloğunun asıl resimdeki koordinatları
                val boundingBox = line.boundingBox ?: continue
                
                // Metin kutusunun PDF üzerindeki ölçeklenmiş konumu
                val left = (boundingBox.left * scale) + marginLeft
                val bottom = (boundingBox.bottom * scale) + marginTop
                val boxHeight = (boundingBox.height() * scale)
                
                // Font boyutunu asıl kutu yüksekliğine göre ayarla
                invisibleTextPaint.textSize = boxHeight
                
                // ML Kit'ten gelen satırı çiz
                canvas.drawText(line.text, left, bottom, invisibleTextPaint)
            }
        }
    }

    /**
     * MediaStore kullanarak Documents(Belgeler) klasörüne dışarı aktarır.
     * Kullanıcı dosya yöneticisinden erişebilir.
     */
    private suspend fun savePdfToMediaStore(pdfDocument: PdfDocument, title: String): String? = withContext(Dispatchers.IO) {
        val fileName = "${title.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        var outputStream: OutputStream? = null
        var resultUri: String? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/PageScan")
                }

                val documentUri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                if (documentUri != null) {
                    outputStream = resolver.openOutputStream(documentUri)
                    resultUri = documentUri.toString()
                }
            } else {
                // Eski Android versiyonları için (Q öncesi)
                @Suppress("DEPRECATION")
                val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val scanDir = File(docsDir, "PageScan")
                if (!scanDir.exists()) scanDir.mkdirs()

                val file = File(scanDir, fileName)
                outputStream = FileOutputStream(file)
                resultUri = file.absolutePath
            }

            outputStream?.use { out ->
                pdfDocument.writeTo(out)
                out.flush()
            }
            return@withContext resultUri
            
        } catch (e: Exception) {
            Log.e(TAG, "Error writing PDF to MediaStore", e)
            return@withContext null
        } finally {
            outputStream?.close()
        }
    }
}
