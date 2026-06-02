package com.ornament.pdfeditor.drawing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.SizeF
import androidx.annotation.ColorInt
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.PdfCanvasConstants
import com.ornament.pdfeditor.extenstions.div
import com.ornament.pdfeditor.extenstions.minus
import com.ornament.pdfeditor.extenstions.plus
import com.ornament.pdfeditor.extenstions.times

class BezierCurve(private val baseWidth: Float, @ColorInt private val color: Int) {
    private val points = mutableListOf<PointF>()

    var isClosed: Boolean = false
        private set

    fun close() {
        isClosed = true
    }

    fun drawOnCanvas(canvas: Canvas, paint: Paint, drawClip: RectF, actualScale: Float, alpha: Int = 255) {
        if (points.isEmpty()) return
        paint.strokeWidth = baseWidth * actualScale
        paint.alpha = alpha
        val offset = PointF(drawClip.left, drawClip.top)
        val path = Path()
        val offsetPoints = getAllPoints().map { it * actualScale + offset }
        path.moveTo(offsetPoints[0].x, offsetPoints[0].y)
        if (offsetPoints.size < 3) {
            path.lineTo(offsetPoints.last().x, offsetPoints.last().y)
            return
        }
        for (index in 1 until offsetPoints.size step 3) {
            path.cubicTo(
                offsetPoints[index].x,
                offsetPoints[index].y,
                offsetPoints[index + 1].x,
                offsetPoints[index + 1].y,
                offsetPoints[index + 2].x,
                offsetPoints[index + 2].y
            )
        }

        canvas.drawPath(path, paint)
    }

    fun drawOnPdfCanvas(pdfCanvas: PdfCanvas, pageSize: SizeF, scale: Float, rotation: Int = 0, mediaSize: SizeF = pageSize) {
        pdfCanvas.apply {
            setLineWidth(baseWidth * scale)
            setLineCapStyle(PdfCanvasConstants.LineCapStyle.ROUND)
            val androidColor = Color.valueOf(color)
            setStrokeColor(DeviceRgb(
                androidColor.red(),
                androidColor.green(),
                androidColor.blue(),
            ))
        }
        // Points are stored in rendered display coordinates (y-down, rotation already applied by PdfRenderer).
        // Convert to PDF user space (y-up, unrotated MediaBox) based on the page rotation entry.
        val W = mediaSize.width
        val H = mediaSize.height
        val rawPoints = getAllPoints().map { it * scale }
        val pdfPoints = when (rotation) {
            90  -> rawPoints.map { PointF(it.y,     it.x)     }
            180 -> rawPoints.map { PointF(W - it.x, it.y)     }
            270 -> rawPoints.map { PointF(W - it.y, H - it.x) }
            else -> rawPoints.map { PointF(it.x,    H - it.y) }
        }
        pdfCanvas.moveTo(pdfPoints.first().x.toDouble(), pdfPoints.first().y.toDouble())
        if (pdfPoints.size < 3) {
            pdfCanvas.lineTo(pdfPoints.last().x.toDouble(), pdfPoints.last().y.toDouble())
        } else for (index in 1 until pdfPoints.size step 3) {
            pdfCanvas.curveTo(
                pdfPoints[index].x.toDouble(),     pdfPoints[index].y.toDouble(),
                pdfPoints[index + 1].x.toDouble(), pdfPoints[index + 1].y.toDouble(),
                pdfPoints[index + 2].x.toDouble(), pdfPoints[index + 2].y.toDouble()
            )
        }
        pdfCanvas.stroke()
    }

    fun addPoint(point: PointF, offset: PointF, scale: Float) {
        val offsetPoint = point - offset
        points.add(offsetPoint / scale)
    }

    private fun getAllPoints() : List<PointF> {
        if (points.isEmpty()) return emptyList()
        val result = mutableListOf<PointF>()
        result.add(points.first())
        for (index in 1 until points.size) {
            val startPoint = points[index - 1]
            val endPoint = points[index]
            val firstControlPoint =
                startPoint + (endPoint - if (index > 1) points[index - 2] else startPoint) * 0.25f
            val secondControlPoint =
                endPoint - ((if (index < points.size - 1) points[index + 1] else endPoint) - startPoint) * 0.25f
            result.addAll(listOf(firstControlPoint, secondControlPoint, endPoint))
        }
        return result
    }
}
