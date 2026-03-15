package com.fth.pagescan.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors

/**
 * Overlay view that draws a real-time boundary polygon for detected document edges
 * over the CameraX Preview.
 */
class EdgeOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val path = Path()
    private var points: List<PointF> = emptyList()

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.BLUE)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.BLUE)
        alpha = 50 // Semi-transparent fill
        style = Paint.Style.FILL
    }

    /**
     * Updates the polygon points based on OpenCV detection.
     * @param newPoints A list of exactly 4 mapped points (corners of the document).
     */
    fun updateEdges(newPoints: List<PointF>) {
        if (newPoints.size == 4) {
            points = newPoints
            invalidate()
        }
    }

    /**
     * Clears the overlay when no document is detected.
     */
    fun clearEdges() {
        if (points.isNotEmpty()) {
            points = emptyList()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size != 4) return

        path.reset()
        path.moveTo(points[0].x, points[0].y)
        path.lineTo(points[1].x, points[1].y)
        path.lineTo(points[2].x, points[2].y)
        path.lineTo(points[3].x, points[3].y)
        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
    }
}
