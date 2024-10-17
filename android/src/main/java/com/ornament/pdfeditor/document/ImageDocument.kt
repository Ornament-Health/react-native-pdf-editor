package com.ornament.pdfeditor.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.Size
import android.util.SizeF
import com.ornament.pdfeditor.bridge.PDFEditorOptions
import com.ornament.pdfeditor.drawing.BezierCurve
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class ImageDocument(
    override val filename: String,
    override val parcelFileDescriptor: ParcelFileDescriptor
) : Document() {

    private lateinit var imageBitmap: Bitmap
    override lateinit var size: SizeF
    private var bounds: RectF? = null
    private val imageDrawing = mutableListOf<BezierCurve>()

    override fun save(outputDirectory: String, options: PDFEditorOptions): String {
        val pagePaint = Paint().apply {
            color = options.lineColor
            strokeWidth = options.lineWidth.toFloat()
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val drawingBitmap = Bitmap.createBitmap(
            imageBitmap.width,
            imageBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        Canvas(drawingBitmap).let { canvas ->
            imageDrawing.forEach {
                it.drawOnCanvas(
                    canvas,
                    pagePaint,
                    RectF(
                        0f,
                        0f,
                        drawingBitmap.width.toFloat(),
                        drawingBitmap.height.toFloat()
                    ),
                    1f / minScale
                )
            }
        }
        val copy = imageBitmap.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(copy).drawBitmap(drawingBitmap, 0f, 0f, Paint())
        val outputStream = ByteArrayOutputStream()
        copy.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val outputPath = "$outputDirectory/$filename-edited.png"
        FileOutputStream(outputPath).also { outputFileStream ->
            outputStream.writeTo(outputFileStream)
            outputFileStream.close()
            outputStream.close()
        }
        return "file://$outputPath"
    }

    init {
        decodeImage()?.let { imageBitmap = it }
        size = with(imageBitmap) { SizeF(width.toFloat(), height.toFloat()) }
    }

    private fun decodeImage(): Bitmap? =
        BitmapFactory.decodeFileDescriptor(parcelFileDescriptor.fileDescriptor)


    override fun render(
        canvas: Canvas,
        scale: Float,
        offset: PointF,
        viewPortSize: Size,
        refresh: Boolean
    ) {
        val imageRect = findPdfPageRect(scale * minScale, offset)
        canvas.drawBitmap(imageBitmap, null, imageRect, Paint())
        //bitmapPages[index] = imagesBitmaps[index]
    }

    private fun findPdfPageRect(scale: Float, movementOffset: PointF): RectF {
        return RectF(
            movementOffset.x,
            movementOffset.y,
            size.width * scale + movementOffset.x,
            size.height * scale + movementOffset.y
        ).also {
            bounds = it
        }
    }

    override fun contains(point: PointF) = bounds?.contains(point.x, point.y) == true

    override fun addDrawing(point: PointF, drawing: BezierCurve) {
        if (contains(point)) {
            imageDrawing.add(drawing)
        }
    }

    override fun renderDrawing(
        bitmap: Bitmap,
        scale: Float,
        viewPortSize: Size,
        lineColor: Int,
        lineWidth: Double
    ) {
        val imageRect = bounds ?: return
        val drawClip = RectF(
            max(imageRect.left, 0f),
            max(imageRect.top, 0f),
            min(imageRect.right, viewPortSize.width.toFloat()),
            min(imageRect.bottom, viewPortSize.height.toFloat()),
        )
        if (drawClip.height() < 0 || drawClip.width() < 0) return
        val pagePaint = Paint().apply {
            color = lineColor
            strokeWidth = lineWidth.toFloat()
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val drawingBitmap = Bitmap.createBitmap(
            drawClip.width().toInt(),
            drawClip.height().toInt(),
            Bitmap.Config.ARGB_8888
        )
        Canvas(drawingBitmap).let { canvas ->
            imageDrawing.forEach {
                it.drawOnCanvas(
                    canvas,
                    pagePaint,
                    RectF(
                        imageRect.left - drawClip.left,
                        imageRect.top - drawClip.top,
                        drawClip.width(),
                        drawClip.height()
                    ),
                    scale,
                    if (it.isClosed) 255 else 128
                )
            }
        }
        Canvas(bitmap).drawBitmap(drawingBitmap, null, drawClip, Paint())
    }

    override fun reset() {
        bounds = null
        imageDrawing.clear()
    }

    override fun undo() {
        imageDrawing.removeLastOrNull()
    }

    override fun clear() {
        imageDrawing.clear()
    }

    override fun addPointToDrawing(point: PointF, offset: PointF, scale: Float) {
        imageDrawing.lastOrNull()?.takeIf { !it.isClosed }?.addPoint(point, offset, scale)
    }
}
