package com.ornament.pdfeditor.drawing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.Size
import android.util.SizeF
import androidx.annotation.ColorInt
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.PdfCanvasConstants
import com.ornament.pdfeditor.extenstions.minus
import com.ornament.pdfeditor.extenstions.plus
import com.ornament.pdfeditor.extenstions.times

class BezierCurve(val pageIndex: Int, private val pageSize: SizeF, private val width: Float, @ColorInt private val color: Int) {
    private val points = mutableListOf<PointF>()

    fun drawOnCanvas(canvas: Canvas, paint: Paint, pageBounds: RectF, actualScale: Float, alpha: Int = 255) {
        if (points.isEmpty()) return
        paint.strokeWidth = width * actualScale
        paint.alpha = alpha
        val offset = PointF(pageBounds.left, pageBounds.top)
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

        //drawing path
        val pageSize = Size(
            (pageBounds.right - pageBounds.left).toInt(),
            (pageBounds.bottom - pageBounds.top).toInt()
        )
        val bitmap = Bitmap.createBitmap(pageSize.width, pageSize.height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawPath(path, paint)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }

    fun drawOnPdfCanvas(pdfCanvas: PdfCanvas) {
        pdfCanvas.apply {
            setLineWidth(width)
            setLineCapStyle(PdfCanvasConstants.LineCapStyle.ROUND)
            val androidColor = Color.valueOf(color)
            setStrokeColor(DeviceRgb(
                androidColor.red(),
                androidColor.green(),
                androidColor.blue(),
            ))
        }
        val points = getAllPoints()
        pdfCanvas.moveTo(points.first().x.toDouble(), pageSize.height - points.first().y.toDouble())
        if (points.size < 3) {
            pdfCanvas.lineTo(points.last().x.toDouble(), pageSize.height -points.last().y.toDouble())
        } else for (index in 1 until points.size step 3) {
            pdfCanvas.curveTo(
                points[index].x.toDouble(),
                pageSize.height - points[index].y.toDouble(),
                points[index + 1].x.toDouble(),
                pageSize.height - points[index + 1].y.toDouble(),
                points[index + 2].x.toDouble(),
                pageSize.height - points[index + 2].y.toDouble()
            )
        }
        pdfCanvas.stroke()
    }

    fun addPoint(point: PointF, pageBounds: RectF) {
        val offsetPoint = point - PointF(pageBounds.left, pageBounds.top)
        val pageScale = pageSize.width / (pageBounds.right - pageBounds.left)
        points.add(offsetPoint * pageScale)
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