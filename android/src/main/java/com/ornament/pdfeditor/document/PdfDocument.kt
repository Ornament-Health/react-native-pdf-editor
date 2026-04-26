package com.ornament.pdfeditor.document

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Size
import android.util.SizeF
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.ornament.pdfeditor.PDFEditorConstants
import com.ornament.pdfeditor.bridge.PDFEditorOptions
import com.ornament.pdfeditor.drawing.BezierCurve
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.LinkedHashMap
import kotlin.collections.ArrayDeque
import kotlin.math.max
import kotlin.math.min

class PdfDocument(
    override val filename: String,
    override val parcelFileDescriptor: ParcelFileDescriptor
) : Document() {

    companion object {
        private const val PAGE_MARGIN = 5f
        private const val MAX_CACHED_BITMAPS = 12
    }

    private data class PageCacheKey(val index: Int, val scaleBucket: Int)

    private var pageCountValue = 1
    private var pdfDocument: PdfDocument
    private var renderer: PdfRenderer
    private var pageSize: SizeF
    override lateinit var size: SizeF

    override val pageCount: Int
        get() = pageCountValue

    private val bitmapPages = object : LinkedHashMap<PageCacheKey, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PageCacheKey, Bitmap>?): Boolean {
            val shouldRemove = size > MAX_CACHED_BITMAPS
            if (shouldRemove) {
                eldest?.value?.let { bitmap ->
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            return shouldRemove
        }
    }
    private val boundsOfPages = mutableMapOf<Int, RectF>()
    private val pagesDrawing = mutableListOf<Pair<Int, BezierCurve>>()
    private val redoStack = ArrayDeque<Pair<Int, BezierCurve>>()
    private var cacheHitCount = 0L
    private var cacheMissCount = 0L
    private var renderCount = 0L
    private var renderPageAccumNs = 0L


    override fun save(outputDirectory: String, options: PDFEditorOptions, excludedPages: Set<Int>): String? {
        val includedPages = (0 until pdfDocument.numberOfPages).filterNot { excludedPages.contains(it) }
        if (includedPages.isEmpty()) return null

        val outputStream = ByteArrayOutputStream()
        val copy = PdfDocument(PdfWriter(outputStream))

        includedPages.forEachIndexed { destinationIndex, pageIndex ->
            pdfDocument.copyPagesTo(pageIndex + 1, pageIndex + 1, copy)
            PdfCanvas(copy.getPage(destinationIndex + 1)).let { pdfCanvas ->
                pagesDrawing.filter { it.first == pageIndex }.forEach {
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
        return "file://$outputPath"
    }

    init {
        FileInputStream(parcelFileDescriptor.fileDescriptor).also { inputFileStream ->
          pdfDocument = PdfDocument(PdfReader(inputFileStream))
          inputFileStream.close()
        }
        renderer = PdfRenderer(parcelFileDescriptor)
        pageCountValue = renderer.pageCount
        val firstPage = renderer.openPage(0)
        pageSize = with(firstPage) { SizeF(width.toFloat(), height.toFloat()) }
        size = with(firstPage) { SizeF(width.toFloat(), (height.toFloat() + PAGE_MARGIN) * renderer.pageCount - PAGE_MARGIN) }
        firstPage.close()
    }

    override fun render(
        canvas: Canvas,
        scale: Float,
        offset: PointF,
        viewPortSize: Size,
        refresh: Boolean,
        interactive: Boolean,
        zoomingOut: Boolean
    ) {
        if (refresh) {
            recycleCachedBitmaps()
        }

        val actualScale = scale * minScale
        val visibleRange = visiblePageRange(actualScale, offset, viewPortSize, interactive, zoomingOut)
        if (visibleRange.isEmpty()) {
            boundsOfPages.clear()
            return
        }

        boundsOfPages.clear()
        val renderOrder = prioritizedVisiblePages(visibleRange, actualScale, offset, viewPortSize)
        for (pageIndex in renderOrder) {
            showPdfPage(canvas, pageIndex, actualScale, offset, viewPortSize, refresh, interactive)
        }
        renderCount += 1
        if (renderCount % 40L == 0L) {
            val total = cacheHitCount + cacheMissCount
            val hitRate = if (total > 0) (cacheHitCount * 100f / total) else 0f
            val avgPageRenderMs = if (cacheMissCount > 0) {
                renderPageAccumNs.toFloat() / cacheMissCount / 1_000_000f
            } else {
                0f
            }
            Log.d(
                PDFEditorConstants.ACTION_TAG,
                "pdf-render-metrics interactive=$interactive zoomingOut=$zoomingOut hitRate=$hitRate misses=$cacheMissCount avgPageRenderMs=$avgPageRenderMs"
            )
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
            redoStack.clear()
        }
    }

    private fun showPdfPage(
        canvas: Canvas,
        index: Int,
        scale: Float,
        movementOffset: PointF,
        viewPortSize: Size,
        refresh: Boolean = false,
        interactive: Boolean = false
    ) {
        val pageRect = findPdfPageRect(index, scale, movementOffset)
        val cacheKey = PageCacheKey(index, cacheScaleBucket(scale, interactive))
        val cached = if (!refresh) bitmapPages[cacheKey] else null
        val pageBitmap = if (cached != null) {
            cacheHitCount += 1
            cached
        } else {
            cacheMissCount += 1
            val rendered = renderPdfPage(index, scale, pageRect, viewPortSize) ?: return
            putCachedBitmap(cacheKey, rendered)
            rendered
        }
        canvas.drawBitmap(pageBitmap, null, pageRect, Paint())
    }
    private fun findPdfPageRect(index: Int, scale: Float, movementOffset: PointF): RectF {
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

    private fun renderPdfPage(index: Int, scale: Float, pageRect: RectF, viewPortSize: Size): Bitmap? {
        if (pageRect.top > viewPortSize.height || pageRect.bottom < 0) return null
        val startedNs = System.nanoTime()
        val page = renderer.openPage(index)
        val bitmap = Bitmap.createBitmap(
            (pageSize.width * scale).toInt(),
            (pageSize.height * scale).toInt(),
            Bitmap.Config.ARGB_8888
        )
        Canvas(bitmap).drawColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderPageAccumNs += (System.nanoTime() - startedNs)
        return bitmap
    }

    override fun renderDrawing(bitmap: Bitmap, scale: Float, viewPortSize: Size, lineColor: Int, lineWidth: Double) {

        for (pageIndex in boundsOfPages.keys) {
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
        val bitmapCanvas = Canvas(bitmap)
        val saveCount = bitmapCanvas.save()
        bitmapCanvas.clipRect(drawClip)
        pagesDrawing.filter { it.first == index }.forEach {
            it.second.drawOnCanvas(
                bitmapCanvas,
                pagePaint,
                pageRect,
                scale,
                if (it.second.isClosed) 255 else 128
            )
        }
        bitmapCanvas.restoreToCount(saveCount)
    }
    override fun reset() {
        boundsOfPages.clear()
        recycleCachedBitmaps()
        pagesDrawing.clear()
        redoStack.clear()
    }

    override fun undo() {
        pagesDrawing.removeLastOrNull()?.let { redoStack.addLast(it) }
    }

    override fun redo() {
        redoStack.removeLastOrNull()?.let { pagesDrawing.add(it) }
    }

    override fun clear() {
        pagesDrawing.clear()
        redoStack.clear()
    }

    override fun pageBounds(): Map<Int, RectF> = boundsOfPages.toMap()

    override fun generateThumbnail(maxWidth: Int, maxHeight: Int): Bitmap? {
        if (maxWidth <= 0 || maxHeight <= 0 || pageCount <= 0) return null
        return try {
            val page = renderer.openPage(0)
            val width = page.width
            val height = page.height
            val scale = min(
                maxWidth / width.toFloat(),
                maxHeight / height.toFloat()
            ).coerceAtLeast(0.0001f)
            val bitmapWidth = (width * scale).toInt().coerceAtLeast(1)
            val bitmapHeight = (height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        } catch (error: Exception) {
            android.util.Log.e("RNPDFEditor", "Failed to generate PDF thumbnail", error)
            null
        }
    }

    override fun addPointToDrawing(point: PointF, offset: PointF, scale: Float) {
        pagesDrawing.firstOrNull { !it.second.isClosed }?.let {
            val pageOffset = PointF(boundsOfPages[it.first]?.left ?: 0f, boundsOfPages[it.first]?.top ?: 0f)
            it.second.addPoint(point, pageOffset, scale)
        }
    }

    override fun dispose() {
        recycleCachedBitmaps()
        try {
            renderer.close()
        } catch (_: Exception) {
        }
        try {
            pdfDocument.close()
        } catch (_: Exception) {
        }
        super.dispose()
    }

    private fun visiblePageRange(
        scale: Float,
        movementOffset: PointF,
        viewPortSize: Size,
        interactive: Boolean,
        zoomingOut: Boolean
    ): IntRange {
        if (pageCount <= 0) return IntRange.EMPTY
        val pageStep = (pageSize.height + PAGE_MARGIN) * scale
        if (pageStep <= 0f) return IntRange.EMPTY

        val viewportTop = 0f
        val viewportBottom = viewPortSize.height.toFloat()
        val overscan = when {
            interactive && zoomingOut -> 3
            interactive -> 2
            else -> 1
        }
        val start = (((viewportTop - movementOffset.y) / pageStep).toInt() - overscan).coerceAtLeast(0)
        val end = (((viewportBottom - movementOffset.y) / pageStep).toInt() + overscan)
            .coerceAtMost(pageCount - 1)

        return if (start > end) IntRange.EMPTY else start..end
    }

    private fun cacheScaleBucket(scale: Float, interactive: Boolean): Int {
        val precision = if (interactive) 20 else 100
        return (scale * precision).toInt().coerceAtLeast(1)
    }

    private fun prioritizedVisiblePages(
        visibleRange: IntRange,
        scale: Float,
        movementOffset: PointF,
        viewPortSize: Size
    ): List<Int> {
        if (visibleRange.isEmpty()) return emptyList()
        val pageStep = (pageSize.height + PAGE_MARGIN) * scale
        if (pageStep <= 0f) return visibleRange.toList()

        val centerY = viewPortSize.height / 2f
        val centerPage = (((centerY - movementOffset.y) / pageStep).toInt())
            .coerceIn(visibleRange.first, visibleRange.last)

        val result = mutableListOf<Int>()
        result.add(centerPage)
        var delta = 1
        while (result.size < visibleRange.count()) {
            val left = centerPage - delta
            val right = centerPage + delta
            if (left >= visibleRange.first) result.add(left)
            if (right <= visibleRange.last) result.add(right)
            delta += 1
        }
        return result
    }

    private fun putCachedBitmap(key: PageCacheKey, bitmap: Bitmap) {
        bitmapPages.remove(key)?.let { previous ->
            if (!previous.isRecycled) previous.recycle()
        }
        bitmapPages[key] = bitmap
    }

    private fun recycleCachedBitmaps() {
        bitmapPages.values.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        bitmapPages.clear()
    }
}
