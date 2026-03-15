package com.fth.pagescan.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.fth.pagescan.data.local.entity.FilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Utility class for processing document images using OpenCV.
 * Handles perspective transformations, color enhancements, and adaptive thresholds.
 * All functions are safely executed on Dispatchers.Default.
 */
class ImageProcessor(private val context: Context) {

    private val TAG = "ImageProcessor"
    
    // OpenCV kütüphanesinin başarıyla yüklenip yüklenmediğini takip etmek için state
    private var isOpencvLoaded = false

    init {
        // Native kütüphaneyi yükler try-catch mekanizması ile güvenceye alınır.
        try {
            if (OpenCVLoader.initDebug()) {
                isOpencvLoaded = true
                Log.i(TAG, "OpenCV loaded successfully.")
            } else {
                Log.e(TAG, "OpenCV initialization failed.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load OpenCV native library.", e)
        }
    }

    /**
     * Applies perspective warp to "flatten" the selected document based on 4 corner points.
     * @param originalBitmap The source full-size image.
     * @param corners Array of 4 PointF objects mapping to TL, TR, BR, BL in source image coords.
     * @return A flattened (rectangular) Bitmap of just the document area.
     */
    suspend fun applyPerspectiveTransform(
        originalBitmap: Bitmap,
        corners: Array<PointF>
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (!isOpencvLoaded || corners.size != 4) return@withContext null

        try {
            // 1. Mat nesnelerini oluştur (Kaynak ve Hedef)
            val srcMat = Mat()
            Utils.bitmapToMat(originalBitmap, srcMat)

            // Puanlamalar her zaman: [Sol-Üst, Sağ-Üst, Sağ-Alt, Sol-Alt] şeklinde gelir
            val srcPoints = MatOfPoint2f(
                Point(corners[0].x.toDouble(), corners[0].y.toDouble()),
                Point(corners[1].x.toDouble(), corners[1].y.toDouble()),
                Point(corners[2].x.toDouble(), corners[2].y.toDouble()),
                Point(corners[3].x.toDouble(), corners[3].y.toDouble())
            )

            // 2. Yeni resmin genişliğini ve yüksekliğini bul (Kenar uzunluklarından maksimum olan)
            val widthA = Math.hypot(
                (corners[2].x - corners[3].x).toDouble(),
                (corners[2].y - corners[3].y).toDouble()
            )
            val widthB = Math.hypot(
                (corners[1].x - corners[0].x).toDouble(),
                (corners[1].y - corners[0].y).toDouble()
            )
            val finalWidth = kotlin.math.max(widthA, widthB).toInt()

            val heightA = Math.hypot(
                (corners[1].x - corners[2].x).toDouble(),
                (corners[1].y - corners[2].y).toDouble()
            )
            val heightB = Math.hypot(
                (corners[0].x - corners[3].x).toDouble(),
                (corners[0].y - corners[3].y).toDouble()
            )
            val finalHeight = kotlin.math.max(heightA, heightB).toInt()

            // 3. Hedef dikdörtgeninin koordinatları (Sol Üst sıfır noktasıdır)
            val dstPoints = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(finalWidth.toDouble() - 1, 0.0),
                Point(finalWidth.toDouble() - 1, finalHeight.toDouble() - 1),
                Point(0.0, finalHeight.toDouble() - 1)
            )

            // 4. Dönüşüm matrisini al
            val transformMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

            // 5. Hedef Mat oluştur ve dönüşümü uygula
            val dstMat = Mat(finalHeight, finalWidth, CvType.CV_8UC4)
            Imgproc.warpPerspective(srcMat, dstMat, transformMatrix, dstMat.size())

            // 6. Mat'i tekrar Bitmap'e çevir
            val resultBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dstMat, resultBitmap)

            // Bellek sızıntılarını önlemek için Mat serbest bırakma
            srcMat.release()
            dstMat.release()
            srcPoints.release()
            dstPoints.release()
            transformMatrix.release()

            return@withContext resultBitmap

        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory while applying perspective transform", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error applying perspective transform", e)
            return@withContext null
        }
    }

    /**
     * Applies the requested visual filter to the cropped document bitmap.
     * @param bitmap The cropped document bitmap.
     * @param filterType MAGIC_COLOR for enhancement, B_AND_W for sharp text, GRAYSCALE, or NONE
     * @return A new Bitmap with the filter applied.
     */
    suspend fun applyFilter(
        bitmap: Bitmap,
        filterType: FilterType
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (!isOpencvLoaded) return@withContext bitmap

        if (filterType == FilterType.NONE) return@withContext bitmap

        try {
            val srcMat = Mat()
            Utils.bitmapToMat(bitmap, srcMat)
            val dstMat = Mat()

            when (filterType) {
                // "Magic Color": Beyaz dengesi ve kontrastı artırır
                FilterType.MAGIC_COLOR -> {
                    // Önce renk uzayına çevir (Lab, aydınlığı ayırmak için daha iyi)
                    val labMat = Mat()
                    Imgproc.cvtColor(srcMat, labMat, Imgproc.COLOR_RGBA2RGB)
                    Imgproc.cvtColor(labMat, labMat, Imgproc.COLOR_RGB2Lab)

                    // Kanalları ayır (L, a, b)
                    val channels = ArrayList<Mat>()
                    org.opencv.core.Core.split(labMat, channels)

                    // L (Lightness/Aydınlık) kanalına Histogram Eşitleme (CLAHE) uygula
                    val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
                    clahe.apply(channels[0], channels[0])

                    // Kanalları tekrar birleştir
                    org.opencv.core.Core.merge(channels, labMat)
                    
                    // Geri RGBA'ya çevir
                    Imgproc.cvtColor(labMat, dstMat, Imgproc.COLOR_Lab2RGB)
                    Imgproc.cvtColor(dstMat, dstMat, Imgproc.COLOR_RGB2RGBA)
                    
                    labMat.release()
                    channels.forEach { it.release() }
                }

                // "Grayscale": Yalnızca griye çevir
                FilterType.GRAYSCALE -> {
                    Imgproc.cvtColor(srcMat, dstMat, Imgproc.COLOR_RGBA2GRAY)
                    Imgproc.cvtColor(dstMat, dstMat, Imgproc.COLOR_GRAY2RGBA) // Bitmap dönüşümü için boyut tutarlılığı
                }

                // "B&W": Sharp text using Adaptive Threshold (Office Lens tarzı)
                FilterType.B_AND_W -> {
                    val grayMat = Mat()
                    // Önce Gri yap
                    Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
                    
                    // Arkaplan gürültüsünü düzeltmek için hafif blur
                    Imgproc.medianBlur(grayMat, grayMat, 3)

                    // Adaptive Threshold uygula (Belge gölgelerinde dahi metni okuyabilmek için)
                    Imgproc.adaptiveThreshold(
                        grayMat, dstMat,
                        255.0, // Maksimum değer (Beyaz)
                        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                        Imgproc.THRESH_BINARY,
                        15, // Block Size (Metin ve boşlukları algılamak için alan çapı)
                        10.0 // C değeri (Threshold'dan çıkarılacak sabit, kontrastı belirler)
                    )

                    Imgproc.cvtColor(dstMat, dstMat, Imgproc.COLOR_GRAY2RGBA)
                    grayMat.release()
                }

                else -> {
                    srcMat.copyTo(dstMat)
                }
            }

            val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dstMat, resultBitmap)

            srcMat.release()
            dstMat.release()

            return@withContext resultBitmap

        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM error applying filter", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error applying filter", e)
            return@withContext null
        }
    }

    /**
     * Saves the final processed Bitmap confidentially to internal storage.
     * @param bitmap The Bitmap to be saved.
     * @param prefix File name prefix (e.g. "doc_page_")
     * @return Absolute file path to the saved JPEG image, or null if failed.
     */
    suspend fun saveBitmapToInternalStorage(bitmap: Bitmap, prefix: String = "page"): String? =
        withContext(Dispatchers.IO) { // Dosya işlemleri her zaman IO thread üzerinde
            try {
                // Context.filesDir güvenlik duvarı içindedir, diğer uygulamalar göremez
                val imagesDir = File(context.filesDir, "documents")
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }

                val fileName = "${prefix}_${UUID.randomUUID()}.jpg"
                val file = File(imagesDir, fileName)

                FileOutputStream(file).use { out ->
                    // 90 kalite, metin okunabilirliği ile dosya boyutu arasındaki tatlı nokta
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                }

                Log.d(TAG, "Saved image to ${file.absolutePath}")
                return@withContext file.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Error saving bitmap to storage.", e)
                return@withContext null
            }
        }
        
    /**
     * Saves the final processed Bitmap to the public device Gallery (MediaStore).
     * @param bitmap The Bitmap to be saved.
     * @param title File name title (e.g. "Scanned Document")
     * @return Absolute file path or content URI string to the saved JPEG image, or null if failed.
     */
    suspend fun saveBitmapToGallery(bitmap: Bitmap, title: String = "Scanned_Document"): String? =
        withContext(Dispatchers.IO) {
            val fileName = "${title.replace(" ", "_")}_${System.currentTimeMillis()}.jpg"
            var outputStream: java.io.OutputStream? = null
            var resultUri: String? = null

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PageScan")
                    }

                    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (imageUri != null) {
                        outputStream = resolver.openOutputStream(imageUri)
                        resultUri = imageUri.toString()
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val scanDir = File(imagesDir, "PageScan")
                    if (!scanDir.exists()) scanDir.mkdirs()

                    val file = File(scanDir, fileName)
                    outputStream = FileOutputStream(file)
                    resultUri = file.absolutePath
                }

                outputStream?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    out.flush()
                }
                
                Log.d(TAG, "Saved image to Gallery: $resultUri")
                return@withContext resultUri
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving bitmap to Gallery.", e)
                return@withContext null
            } finally {
                outputStream?.close()
            }
        }
}
