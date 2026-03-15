package com.fth.pagescan.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.fth.pagescan.R
import com.google.android.material.color.MaterialColors
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Custom View for cropping documents with a 4-point perspective
 * features a magnifying glass (loupe) effect when dragging corners.
 */
class DocumentCropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Kırpma köşeleri: Sol-Üst, Sağ-Üst, Sağ-Alt, Sol-Alt
    private val points = Array(4) { PointF() }
    private var activePointIndex = -1

    fun getCropPoints(): Array<PointF> = points

    // Renkler (Material 3 Dynamic Colors)
    private val primaryColor = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.BLUE)
    private val overlayColor = Color.argb(120, 0, 0, 0) // Yarı şeffaf karanlık

    // Çizim Araçları (Paints)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        strokeWidth = 6f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f) // Kesikli çizgi
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        style = Paint.Style.FILL
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = overlayColor
        style = Paint.Style.FILL
    }
    
    // Büyüteç (Loupe) Araçları
    private val loupeRadius = 150f
    private val loupeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }
    private val loupeBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@DocumentCropOverlayView, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        style = Paint.Style.FILL
    }

    private val path = Path()
    private val touchRadius = 60f // Dokunma alanı genişliği

    // Büyütülecek Bitmap (Alt katmandaki resim)
    private var imageBitmap: android.graphics.Bitmap? = null
    
    // Resim ölçekleme (Görünen View ile asıl Bitmap arasındaki oran)
    private var scaleX = 1f
    private var scaleY = 1f
    private var dX = 0f
    private var dY = 0f

    init {
        // Render optimizasyonu
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setBitmap(bitmap: android.graphics.Bitmap) {
        imageBitmap = bitmap
        calculateImageScale()
        resetPoints()
        invalidate()
    }
    
    // 4 köşeyi resmin asıl çizilen sınırlarına (iç tarafa) oranla başlatır
    private fun resetPoints() {
        if (imageBitmap == null) return
        
        val margin = 50f
        
        // Resmin ekranda kapladığı gerçek alan (Letterbox/siyah boşluklar hariç)
        val rectLeft = dX
        val rectTop = dY
        val rectRight = dX + (imageBitmap!!.width * scaleX)
        val rectBottom = dY + (imageBitmap!!.height * scaleY)
        
        points[0].set(rectLeft + margin, rectTop + margin)                 // Sol-Üst
        points[1].set(rectRight - margin, rectTop + margin)             // Sağ-Üst
        points[2].set(rectRight - margin, rectBottom - margin)         // Sağ-Alt
        points[3].set(rectLeft + margin, rectBottom - margin)             // Sol-Alt
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateImageScale()
        resetPoints()
    }

    private fun calculateImageScale() {
        imageBitmap?.let {
            val viewRatio = width.toFloat() / height.toFloat()
            val imageRatio = it.width.toFloat() / it.height.toFloat()

            if (viewRatio > imageRatio) {
                // View daha geniş (FIT_CENTER mantığı)
                scaleY = height.toFloat() / it.height.toFloat()
                scaleX = scaleY
                dX = (width - it.width * scaleX) / 2f
                dY = 0f
            } else {
                // Resim daha geniş
                scaleX = width.toFloat() / it.width.toFloat()
                scaleY = scaleX
                dX = 0f
                dY = (height - it.height * scaleY) / 2f
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageBitmap == null) return

        // 1. Karanlık arkaplanı çiz (Seçim alanı dışı)
        path.reset()
        path.moveTo(points[0].x, points[0].y)
        path.lineTo(points[1].x, points[1].y)
        path.lineTo(points[2].x, points[2].y)
        path.lineTo(points[3].x, points[3].y)
        path.close()

        canvas.save()
        canvas.clipOutPath(path) // Seçim alanı içini kes çıkar
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        canvas.restore()

        // 2. Seçim alanı sınırlarını çiz (Kesikli)
        canvas.drawPath(path, linePaint)

        // 3. Köşeleri çiz (Dairecik)
        points.forEach { point ->
            canvas.drawCircle(point.x, point.y, 25f, cornerPaint)
        }

        // 4. Büyüteç (Loupe) Çizimi
        if (activePointIndex != -1) {
            drawLoupe(canvas, points[activePointIndex])
        }
    }

    private fun drawLoupe(canvas: Canvas, touchPoint: PointF) {
        imageBitmap?.let { bmp ->
            // Büyütecin konumu (Parmağın hemen üstünde sol veya sağa kaydırarak çizeceğiz)
            val loupeX = if (touchPoint.x > width / 2) touchPoint.x - loupeRadius - 50f else touchPoint.x + loupeRadius + 50f
            val loupeY = max(loupeRadius + 10f, touchPoint.y - loupeRadius - 150f)

            // Büyüteç arkaplanı (Daire)
            canvas.drawCircle(loupeX, loupeY, loupeRadius, loupeBackgroundPaint)

            canvas.save()
            // Sadece büyüteç çemberinin için çizim yap
            val loupePath = Path().apply { addCircle(loupeX, loupeY, loupeRadius, Path.Direction.CW) }
            canvas.clipPath(loupePath)

            // Bitmap'ten çekilecek alanın hesaplanması (2x Zoom)
            val zoomFactor = 2f
            
            // Asıl bitmap üzerindeki parmak koordinatını bul
            val imageTargetX = (touchPoint.x - dX) / scaleX
            val imageTargetY = (touchPoint.y - dY) / scaleY

            // Çizim için matrisi ayarla:
            // 1. Resmi origin(0,0) noktasına al
            canvas.translate(loupeX, loupeY)
            // 2. Resmi büyüt
            canvas.scale(scaleX * zoomFactor, scaleY * zoomFactor)
            // 3. Resmin içindeki hedef noktayı (imageTargetX, Y) merkeze yerleştir
            canvas.translate(-imageTargetX, -imageTargetY)

            // Resmi çiz
            canvas.drawBitmap(bmp, 0f, 0f, null)
            canvas.restore()

            // Büyüteç çerçevesini çiz
            canvas.drawCircle(loupeX, loupeY, loupeRadius, loupeBorderPaint)
            // Büyüteç merkezindeki artı imleci (Crosshair)
            canvas.drawLine(loupeX - 20f, loupeY, loupeX + 20f, loupeY, cornerPaint)
            canvas.drawLine(loupeX, loupeY - 20f, loupeX, loupeY + 20f, cornerPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (imageBitmap == null) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointIndex = getClosestPointIndex(event.x, event.y)
                if (activePointIndex != -1) {
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointIndex != -1) {
                    // Sınırları aşmasını engelle
                    points[activePointIndex].x = min(max(event.x, 0f), width.toFloat())
                    points[activePointIndex].y = min(max(event.y, 0f), height.toFloat())
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getClosestPointIndex(x: Float, y: Float): Int {
        var closestIndex = -1
        var minDistance = Float.MAX_VALUE

        for (i in points.indices) {
            val distance = hypot((points[i].x - x).toDouble(), (points[i].y - y).toDouble()).toFloat()
            if (distance < touchRadius * 2 && distance < minDistance) {
                minDistance = distance
                closestIndex = i
            }
        }
        return closestIndex
    }

    // View koordinatlarını (Ekranda görünen), orijinal Bitmap piksellerine çevirir
    fun getMappedCropPoints(): Array<PointF> {
        val mappedPoints = Array(4) { PointF() }
        if (imageBitmap == null) return points
        
        val bmpWidth = imageBitmap!!.width.toFloat()
        val bmpHeight = imageBitmap!!.height.toFloat()
        
        for (i in 0 until 4) {
             val px = (points[i].x - dX) / scaleX
             val py = (points[i].y - dY) / scaleY
             // Resim sınırları içine sıkıştır (Clamping)
             mappedPoints[i] = PointF(
                 min(max(px, 0f), bmpWidth),
                 min(max(py, 0f), bmpHeight)
             )
        }
        return mappedPoints
    }
}
