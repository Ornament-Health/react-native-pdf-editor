package com.ornament.pdfeditor.document

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.util.Size
import android.util.SizeF
import com.ornament.pdfeditor.bridge.PDFEditorOptions
import com.ornament.pdfeditor.drawing.BezierCurve

abstract class Document {

    abstract var size: SizeF
        protected set

    var minScale = 1f

    abstract fun save(outputDirectory: String, options: PDFEditorOptions): String

    abstract fun render(canvas: Canvas, scale: Float, offset: PointF, viewPortSize: Size, refresh: Boolean)

    abstract fun contains(point: PointF): Boolean

    abstract fun addDrawing(point: PointF, drawing: BezierCurve)

    abstract fun renderDrawing(bitmap: Bitmap, scale: Float, viewPortSize: Size, lineColor: Int, lineWidth: Double)

    abstract fun addPointToDrawing(point: PointF, offset: PointF, scale: Float)

    abstract fun reset()

    abstract fun undo()

    abstract fun clear()

    companion object {
        fun create(filePath: String) =
            when (val ext = filePath.substringAfterLast('.', "").uppercase()) {
                "JPG", "JPEG", "PNG" -> ImageDocument(filePath)
                "PDF" -> PdfDocument(filePath)
                else -> throw Exception("Not compatible file extension '$ext'")
        }
    }
}