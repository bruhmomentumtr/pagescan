package com.fth.pagescan.util

import android.graphics.ImageFormat
import android.graphics.PointF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Analyzes live camera frames to detect the largest 4-point contour (a document).
 */
class DocumentAnalyzer(
    private val viewWidth: Int,
    private val viewHeight: Int,
    private val onEdgesDetected: (List<PointF>) -> Unit
) : ImageAnalysis.Analyzer {

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        if (image.format != ImageFormat.YUV_420_888) {
            image.close()
            return
        }

        try {
            // Y Kanalı (Grayscale verisi)
            val yBuffer = image.planes[0].buffer
            val ySize = yBuffer.remaining()
            val yBytes = ByteArray(ySize)
            yBuffer.get(yBytes)

            val grayMat = Mat(image.height, image.width, CvType.CV_8UC1)
            grayMat.put(0, 0, yBytes)

            // Düşük çözünürlüklü işleme (Performans için resmi küçült)
            val processWidth = 400.0
            val scale = processWidth / image.width.toDouble()
            val processHeight = image.height * scale
            val scaledMat = Mat()
            Imgproc.resize(grayMat, scaledMat, Size(processWidth, processHeight))

            // Blur ve Canny Edge Detection
            Imgproc.GaussianBlur(scaledMat, scaledMat, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(scaledMat, scaledMat, 75.0, 200.0)

            // Contours (Kenarları) Bul
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                scaledMat,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            // En büyük dörtgeni bul
            var maxArea = -1.0
            var largestContour: MatOfPoint2f? = null

            for (contour in contours) {
                val contour2f = MatOfPoint2f(*contour.toArray())
                val area = Imgproc.contourArea(contour2f)

                if (area > 5000) { // Sadece belli bir büyüklüğün üzerindeki şekilleri dikkate al
                    val peri = Imgproc.arcLength(contour2f, true)
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)

                    // Eğer 4 kenarlıysa ve alanı en büyükse kaydet
                    if (approx.toArray().size == 4 && area > maxArea) {
                        largestContour = approx
                        maxArea = area
                    }
                }
            }

            // Eğer bir doküman bulunduysa koordinatlarını ekrana mapping yap
            if (largestContour != null) {
                val pointsArray = largestContour.toArray()
                val mappedPoints = pointsArray.map { point ->
                    // 1. Ölçeği asıl resim boyutuna geri çevir
                    val originalX = point.x / scale
                    val originalY = point.y / scale

                    // 2. CameraX PreviewView ile asıl resim arasındaki oranı (Mapping) hesapla
                    // (FIT_CENTER/FILL varsayımı ile basitçe)
                    val scaleX = viewWidth.toFloat() / image.width.toFloat()
                    val scaleY = viewHeight.toFloat() / image.height.toFloat()
                    val finalScale = max(scaleX, scaleY)

                    // Ortaya hizalama offset'i
                    val dX = (viewWidth - (image.width * finalScale)) / 2f
                    val dY = (viewHeight - (image.height * finalScale)) / 2f

                    PointF(
                        (originalX * finalScale + dX).toFloat(),
                        (originalY * finalScale + dY).toFloat()
                    )
                }

                // Ekrana yansıtması için Callback
                onEdgesDetected(mappedPoints)
            } else {
                // Doküman bulunamadı bildirimi (listeyi boş göndererek overlay'i siliyoruz)
                onEdgesDetected(emptyList())
            }

            grayMat.release()
            scaledMat.release()
            hierarchy.release()
            
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close() // Analiz bitince frame'i bırakmalıyız ki kamera kilitlenmesin
        }
    }
}
