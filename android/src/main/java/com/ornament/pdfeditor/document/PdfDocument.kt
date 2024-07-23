package com.ornament.pdfeditor.document

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Size
import android.util.SizeF
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.ornament.pdfeditor.bridge.PDFEditorOptions
import com.ornament.pdfeditor.drawing.BezierCurve
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class PdfDocument(
    override val filename: String,
    override val parcelFileDescriptor: ParcelFileDescriptor
) : Document() {

    companion object {
        private const val PAGE_MARGIN = 5f
    }

    private var pageCount = 1
    private var pdfDocument: PdfDocument
    private var renderer: PdfRenderer
    private var currentPage: PdfRenderer.Page? = null
    private var pageSize: SizeF
    override lateinit var size: SizeF

    private val bitmapPages = mutableMapOf<Int, Bitmap>()
    private val boundsOfPages = mutableMapOf<Int, RectF>()
    private val pagesDrawing = mutableListOf<Pair<Int, BezierCurve>>()


    override fun save(outputDirectory: String, options: PDFEditorOptions): String {
        val outputStream = ByteArrayOutputStream()
        val copy = PdfDocument(PdfWriter(outputStream)).also {
            pdfDocument.copyPagesTo(1, pdfDocument.numberOfPages, it)
        }
        for (pageNumber in 1..pdfDocument.numberOfPages) {
            PdfCanvas(copy.getPage(pageNumber)).let { pdfCanvas ->
                pagesDrawing.filter { it.first == pageNumber - 1 }.forEach {
                    it.second.drawOnPdfCanvas(pdfCanvas, pageSize, 1f / minScale)
                }
            }
        }
        copy.close()
        val outputPath = "$outputDirectory/$filename-edited.pdf"
        FileOutputStream(outputPath).also { outputFileStream ->
            outputStream.writeTo(outputFileStream)
            outputFileStream.close()
            outputStream.close()
        }
        return outputPath
    }

    init {
        pdfDocument = PdfDocument(PdfReader(FileInputStream(parcelFileDescriptor.fileDescriptor)))
        renderer = PdfRenderer(parcelFileDescriptor)
        pageCount = renderer.pageCount
        currentPage?.close()
        currentPage = renderer.openPage(0)
        pageSize = with(currentPage!!) { SizeF(width.toFloat(), height.toFloat()) }
        size = with(currentPage!!) { SizeF(width.toFloat(), (height.toFloat() + PAGE_MARGIN) * renderer.pageCount - PAGE_MARGIN) }
    }

    override fun render(canvas: Canvas, scale: Float, offset: PointF, viewPortSize: Size, refresh: Boolean) {
        for (pageIndex in 0 until pdfDocument.numberOfPages) {
            showPdfPage(canvas, pageIndex, scale * minScale, offset, viewPortSize, refresh)
        }
    }

    override fun contains(point: PointF): Boolean {
        boundsOfPages.forEach { (_, rect) ->
            if (rect.contains(point.x, point.y)) {
                return true
            }
        }
        return false
    }

    override fun addDrawing(point: PointF, drawing: BezierCurve) {
        var pageIndex: Int? = null
        boundsOfPages.forEach { (index, rect) ->
            if (rect.contains(point.x, point.y)) {
                pageIndex = index
            }
        }
        pageIndex?.let { index ->
            pagesDrawing.add(index to drawing)
        }
    }

    private fun showPdfPage(canvas: Canvas, index: Int, scale: Float, movementOffset: PointF, viewPortSize: Size, refresh: Boolean = false) {
        val pageRect = findPdfPageRect(index, scale, movementOffset)
        val pageBitmap = (
                if (refresh) renderPdfPage(index, scale, movementOffset, viewPortSize)
                else bitmapPages[index] ?: renderPdfPage(index, scale, movementOffset, viewPortSize)) ?: return
        canvas.drawBitmap(pageBitmap, null, pageRect, Paint())
    }
    private fun findPdfPageRect(index: Int, scale: Float, movementOffset: PointF): RectF {
        currentPage?.close()
        currentPage = renderer.openPage(index)

        val offset = PointF(
            movementOffset.x,
            (pageSize.height + PAGE_MARGIN) * index * scale + movementOffset.y
        )

        return RectF(
            offset.x,
            offset.y,
            pageSize.width * scale + offset.x,
            pageSize.height * scale + offset.y
        ).also {
            boundsOfPages[index] = it
        }
    }

    private fun renderPdfPage(index: Int, scale: Float, movementOffset: PointF, viewPortSize: Size): Bitmap? {
        val pageRect = findPdfPageRect(index, scale, movementOffset)
        if (pageRect.top > viewPortSize.height || pageRect.bottom < 0) return null
        val bitmap = Bitmap.createBitmap(
            (pageSize.width * scale).toInt(),
            (pageSize.height * scale).toInt(),
            Bitmap.Config.ARGB_8888
        )
        Canvas(bitmap).drawColor(Color.WHITE)
        currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmapPages[index] = bitmap
        return bitmap
    }

    override fun renderDrawing(bitmap: Bitmap, scale: Float, viewPortSize: Size, lineColor: Int, lineWidth: Double) {

        for (pageIndex in 0 until pageCount) {
            renderDrawingOnPage(pageIndex, bitmap, scale, viewPortSize, lineColor, lineWidth)
        }
    }

    private fun renderDrawingOnPage(index: Int, bitmap: Bitmap, scale: Float, viewPortSize: Size, lineColor: Int, lineWidth: Double) {
        val pageRect = boundsOfPages[index] ?: return
        val drawClip = RectF(
            max(pageRect.left, 0f),
            max(pageRect.top, 0f),
            min(pageRect.right, viewPortSize.width.toFloat()),
            min(pageRect.bottom, viewPortSize.height.toFloat()),
        )
        val width = drawClip.width().toInt()
        val height = drawClip.height().toInt()
        if (width <= 0 || height <= 0) return
        val pagePaint = Paint().apply {
            color = lineColor
            strokeWidth = lineWidth.toFloat()
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val drawingBitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
        Canvas(drawingBitmap).let { canvas ->
            pagesDrawing.filter { it.first == index }.forEach {
                it.second.drawOnCanvas(
                    canvas,
                    pagePaint,
                    RectF(
                        pageRect.left - drawClip.left,
                        pageRect.top - drawClip.top,
                        drawClip.width(),
                        drawClip.height()
                    ),
                    scale,
                    if (it.second.isClosed) 255 else 128
                )
            }
        }
        Canvas(bitmap).drawBitmap(drawingBitmap, null, drawClip, Paint())
    }
    override fun reset() {
        boundsOfPages.clear()
        bitmapPages.clear()
        pagesDrawing.clear()
    }

    override fun undo() {
        pagesDrawing.removeLastOrNull()
    }

    override fun clear() {
        pagesDrawing.clear()
    }

    override fun addPointToDrawing(point: PointF, offset: PointF, scale: Float) {
        pagesDrawing.firstOrNull { !it.second.isClosed }?.let {
            val pageOffset = PointF(boundsOfPages[it.first]?.left ?: 0f, boundsOfPages[it.first]?.top ?: 0f)
            it.second.addPoint(point, pageOffset, scale)
        }
    }
}
