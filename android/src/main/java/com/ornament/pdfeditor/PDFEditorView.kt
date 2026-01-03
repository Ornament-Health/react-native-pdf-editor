package com.ornament.pdfeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.itextpdf.kernel.utils.XmlProcessorCreator
import com.ornament.pdfeditor.bridge.PDFEditorOptions
import com.ornament.pdfeditor.databinding.ViewPdfEditorBinding
import com.ornament.pdfeditor.document.Document
import com.ornament.pdfeditor.drawing.BezierCurve
import com.ornament.pdfeditor.extenstions.minus
import com.ornament.pdfeditor.extenstions.plus
import com.ornament.pdfeditor.extenstions.times
import com.ornament.pdfeditor.preview.DocumentPreviewAdapter
import com.ornament.pdfeditor.preview.DocumentPreviewItem
import com.ornament.pdfeditor.utils.XmlParserFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class PDFEditorView(context: Context) : ConstraintLayout(context) {
    private var editMode: Boolean = false
    fun setEditMode(isEdit: Boolean) {
        editMode = isEdit
    }

    fun setExcludedPages(documentIndex: Int, pages: List<Int>) {
        val document = documents.getOrNull(documentIndex) ?: return
        val filteredPages = if (document.pageCount > 0) {
            pages.filter { it >= 0 && it < document.pageCount }.toSet()
        } else {
            pages.filter { it >= 0 }.toSet()
        }
        excludedPages[documentIndex] = filteredPages
    }

    companion object {
        private const val ACTION_TAG = "ACTION"
        private const val MARGIN = 20f
        private const val MAX_SCALE = 5f
    }

    private val outputDirectory =
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.absolutePath

    private val binding: ViewPdfEditorBinding
    private lateinit var viewPort: Size

    private val previewAdapter = DocumentPreviewAdapter(::onPreviewSelected)
    private val previewWidthPx = (context.resources.displayMetrics.density * 80f).toInt().coerceAtLeast(1)
    private val previewHeightPx = (context.resources.displayMetrics.density * 96f).toInt().coerceAtLeast(1)
    private val documentPreviews = mutableListOf<DocumentPreviewItem>()
    private var activeDocumentIndex = 0

    private val excludedPages = mutableMapOf<Int, Set<Int>>()
    private var lastPageBounds: Map<Int, RectF> = emptyMap()

    private lateinit var options: PDFEditorOptions
    private var pendingOptions: PDFEditorOptions? = null

    private var selectionIconColor: Int = Color.WHITE

    private val operationList = mutableListOf<Int>()

    private var movementDifference = PointF(0f, 0f)
    private var currentFilePaths = listOf<String>()

    private lateinit var layerBitmap: Bitmap

    private val documents = mutableListOf<Document>()

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var renderingJob: Job? = null
    private var scale: Float = 1f
        set(value) {
            previousScale = field
            field = value.coerceAtLeast(1f).coerceAtMost(MAX_SCALE)
        }
    private var previousScale = scale

    init {
        XmlProcessorCreator.setXmlParserFactory(XmlParserFactory())
        binding = ViewPdfEditorBinding.inflate(LayoutInflater.from(context), this, true)
        binding.previewList.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = previewAdapter
            setHasFixedSize(true)
        }
        binding.viewPort.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateViewPortSize()
        }
    }

    private fun reset() {
        scale = 1f
        movementDifference = PointF(0f, 0f)
        operationList.clear()
        documents.forEach { it.reset() }
        lastPageBounds = emptyMap()
    }

    private fun updateViewPortSize() {
        val width = binding.viewPort.width
        val height = binding.viewPort.height
        if (width <= 0 || height <= 0) return
        val shouldUpdate = !this::viewPort.isInitialized || viewPort.width != width || viewPort.height != height
        if (!shouldUpdate) return
        viewPort = Size(width, height)
        pendingOptions?.let {
            applyOptions(it, refresh = true)
        } ?: run {
            if (documents.isNotEmpty()) {
                recalculateDocumentLayout()
                clampMovementDifference()
                render(true)
            }
        }
    }

    fun setOptions(options: PDFEditorOptions) {
        pendingOptions = options
        this.options = options
        if (!this::viewPort.isInitialized || viewPort.width == 0 || viewPort.height == 0) {
            return
        }
        applyOptions(options, refresh = true)
    }

    private fun applyOptions(options: PDFEditorOptions, refresh: Boolean) {
        if (!this::viewPort.isInitialized || viewPort.width == 0 || viewPort.height == 0) return
        background = ColorDrawable(Color.TRANSPARENT)
        selectionIconColor = options.selectionIconColor
        val filePaths = options.filePaths
        if (filePaths.isNullOrEmpty()) {
            clearRenderedContent()
            return
        }
        val shouldReload = documents.isEmpty() || currentFilePaths != filePaths
        if (shouldReload) {
            renderingJob?.cancel()
            renderingJob = null
            buildDocuments(filePaths)
            reset()
            activeDocumentIndex = 0
            excludedPages.clear()
            lastPageBounds = emptyMap()
            centerDocuments()
            prepareDocumentPreviews()
        } else {
            recalculateDocumentLayout()
        }
        clampMovementDifference()
        render(refresh || shouldReload)
    }

    private var onSavePDFAction: (paths: List<String>?) -> Unit = {}

    fun onSavePDF(action: (paths: List<String>?) -> Unit) {
        onSavePDFAction = action
    }

    fun undo() {
        operationList.removeLastOrNull()?.let {
            documents[it].undo()
            render()
            Log.d(ACTION_TAG, "UNDO")
        }
    }

    fun save() {
        coroutineScope.launch(Dispatchers.IO) {
            var outputs: MutableList<String>? = mutableListOf()
            documents.forEachIndexed { index, document ->
                val excluded = excludedPages[index] ?: emptySet()
                saveDocument(document, excluded)?.let { outputs?.add(it) }
            }
            onSavePDFAction(outputs)
        }
    }

    private fun saveDocument(document: Document, excluded: Set<Int>): String? {
        return outputDirectory?.let {
            document.save(it, options, excluded)
        }
    }

    fun clear() {
        operationList.clear()
        documents.forEach { it.clear() }
        render()
        Log.d(ACTION_TAG, "CLEAR")
    }

    private fun buildDocuments(filePaths: List<String>) {
        currentFilePaths = filePaths
        releaseDocuments()
        if (filePaths.isEmpty()) {
            return
        }
        filePaths.forEach { path ->
            Document.create(path, context.contentResolver).also { documents.add(it) }
        }
        recalculateDocumentLayout()
    }

    private fun recalculateDocumentLayout() {
        if (!this::viewPort.isInitialized || viewPort.width == 0) return
        val availableWidth = (viewPort.width.toFloat() - 2 * MARGIN).coerceAtLeast(1f)
        documents.forEach { document ->
            val minScale = (availableWidth / document.size.width).coerceAtLeast(0.0001f)
            document.minScale = minScale
        }
    }

    private fun prepareDocumentPreviews() {
        recycleDocumentPreviews()

        documents.forEachIndexed { index, document ->
            val thumbnail = document.generateThumbnail(previewWidthPx, previewHeightPx)
            if (thumbnail == null) {
                Log.w("RNPDFEditor", "Thumbnail is null for index=$index type=${document.javaClass.simpleName}")
            }
            documentPreviews.add(DocumentPreviewItem(index, thumbnail))
        }
        previewAdapter.submit(documentPreviews.toList(), activeDocumentIndex)
        binding.previewList.isVisible = true
        // Center previews if they fit
        val itemWidthDp = 88 + 12 // width + marginEnd
        val totalWidthDp = documents.size * itemWidthDp - 12 // subtract last margin
        val density = resources.displayMetrics.density
        val totalWidthPx = (totalWidthDp * density).toInt()
        val screenWidth = resources.displayMetrics.widthPixels
        if (totalWidthPx < screenWidth) {
            val paddingPx = (screenWidth - totalWidthPx) / 2
            binding.previewList.setPadding(paddingPx, binding.previewList.paddingTop, paddingPx, binding.previewList.paddingBottom)
        } else {
            binding.previewList.setPadding((16 * density).toInt(), binding.previewList.paddingTop, (16 * density).toInt(), binding.previewList.paddingBottom)
        }
    }

    private fun recycleDocumentPreviews() {
        previewAdapter.submit(emptyList(), 0)
        documentPreviews.forEach { it.thumbnail?.recycle() }
        documentPreviews.clear()
    }

    private fun onPreviewSelected(index: Int) {
        if (index == activeDocumentIndex || index !in documents.indices) return
        activeDocumentIndex = index
        lastPageBounds = emptyMap()
        scale = 1f
        movementDifference = PointF(0f, 0f)
        centerDocuments()
        previewAdapter.updateSelection(index)
        render(true)
    }

    private fun clampMovementDifference() {
        if (!::viewPort.isInitialized || documents.isEmpty()) {
            movementDifference = PointF(0f, 0f)
            return
        }
        val (minX, maxX) = boundsFor(contentWidth(), viewPort.width.toFloat())
        val (minY, maxY) = boundsFor(contentHeight(), viewPort.height.toFloat())
        movementDifference.x = movementDifference.x.coerceIn(minX, maxX)
        movementDifference.y = movementDifference.y.coerceIn(minY, maxY)
    }

    private fun centerDocuments() {
        if (!::viewPort.isInitialized || documents.isEmpty()) {
            movementDifference = PointF(0f, 0f)
            return
        }
        val (minX, maxX) = boundsFor(contentWidth(), viewPort.width.toFloat())
        val (minY, maxY) = boundsFor(contentHeight(), viewPort.height.toFloat())
        movementDifference.x = if (minX == maxX) minX else maxX
        movementDifference.y = if (minY == maxY) minY else maxY
    }

    private fun contentWidth(): Float {
        val document = documents.getOrNull(activeDocumentIndex) ?: return 0f
        return document.size.width * document.minScale * scale
    }

    private fun contentHeight(): Float {
        val document = documents.getOrNull(activeDocumentIndex) ?: return 0f
        return document.size.height * document.minScale * scale
    }

    private fun boundsFor(content: Float, container: Float): Pair<Float, Float> {
        if (content <= 0f || container <= 0f) return 0f to 0f
        val margin = MARGIN * scale
        val total = content + margin * 2
        return if (total <= container) {
            val centered = (container - total) / 2f
            centered to centered
        } else {
            val min = container - total
            min to 0f
        }
    }

    private fun render(refresh: Boolean = false) {
        if (!::viewPort.isInitialized || viewPort.width == 0 || viewPort.height == 0) return
        val document = documents.getOrNull(activeDocumentIndex)
        if (document == null) {
            clearRenderedContent()
            return
        }
        renderingJob?.cancel()
        renderingJob = coroutineScope.launch {
            layerBitmap = Bitmap.createBitmap(
                viewPort.width,
                viewPort.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(layerBitmap)
            val offset = movementDifference + PointF(MARGIN, MARGIN) * scale
            document.render(canvas, scale, offset, viewPort, refresh)
            renderDrawing(document)
        }
    }

    private fun renderDrawing(document: Document) {
        val bitmap = layerBitmap.copy(Bitmap.Config.ARGB_8888, true)
        document.renderDrawing(
            bitmap,
            scale,
            viewPort,
            options.lineColor,
            options.lineWidth
        )
        lastPageBounds = document.pageBounds()
        renderPageSelection(bitmap, document)
        binding.viewPort.setImageBitmap(bitmap)
        binding.viewPort.invalidate()
        invalidate()
    }

    private fun renderPageSelection(bitmap: Bitmap, document: Document) {
        val canvas = Canvas(bitmap)
        val excluded = excludedPages[activeDocumentIndex] ?: emptySet()
        val shadePaint = Paint().apply {
            color = Color.argb(70, 80, 80, 80)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
            val iconPaint = Paint().apply {
            color = selectionIconColor
            strokeWidth = dpToPx(2f)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
            val iconBgPaint = Paint().apply {
                color = Color.argb(90, 0, 0, 0)
                style = Paint.Style.FILL
                isAntiAlias = true
            }

        document.pageBounds().forEach { (index, rect) ->
            if (excluded.contains(index)) {
                canvas.drawRect(rect, shadePaint)
            }
                drawSelectionIcon(canvas, rect, iconPaint, iconBgPaint, excluded.contains(index))
        }
    }

    private fun dpToPx(value: Float): Float = value * resources.displayMetrics.density

    private fun iconHitRect(pageRect: RectF): RectF {
        val size = dpToPx(28f)
        val inset = dpToPx(8f)
        return RectF(
            pageRect.right - inset - size,
            pageRect.top + inset,
            pageRect.right - inset,
            pageRect.top + inset + size
        )
    }

    private fun handleSelectionTap(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) return false
        if (lastPageBounds.isEmpty()) return false
        lastPageBounds.forEach { (index, rect) ->
            if (iconHitRect(rect).contains(event.x, event.y)) {
                toggleExcludedPage(index)
                render(true)
                return true
            }
        }
        return false
    }

    private fun toggleExcludedPage(pageIndex: Int) {
        val document = documents.getOrNull(activeDocumentIndex) ?: return
        if (pageIndex < 0 || pageIndex >= document.pageCount) return
        val current = excludedPages[activeDocumentIndex]?.toMutableSet() ?: mutableSetOf()
        if (current.contains(pageIndex)) {
            current.remove(pageIndex)
        } else {
            current.add(pageIndex)
        }
        excludedPages[activeDocumentIndex] = current
    }

    private fun drawSelectionIcon(canvas: Canvas, pageRect: RectF, paint: Paint, bgPaint: Paint, isExcluded: Boolean) {
        val size = dpToPx(24f)
        val inset = dpToPx(8f)
        val iconRect = RectF(
            pageRect.right - inset - size,
            pageRect.top + inset,
            pageRect.right - inset,
            pageRect.top + inset + size
        )
        val circleRect = RectF(iconRect).apply { inset(dpToPx(2f), dpToPx(2f)) }
        canvas.drawOval(circleRect, bgPaint)
        canvas.drawOval(circleRect, paint)

        if (isExcluded) {
            canvas.drawLine(
                circleRect.left + dpToPx(4f),
                circleRect.bottom - dpToPx(4f),
                circleRect.right - dpToPx(4f),
                circleRect.top + dpToPx(4f),
                paint
            )
        } else {
            canvas.drawLines(
                floatArrayOf(
                    circleRect.left + dpToPx(4f),
                    circleRect.centerY(),
                    circleRect.centerX() - dpToPx(2f),
                    circleRect.bottom - dpToPx(6f),
                    circleRect.centerX() - dpToPx(2f),
                    circleRect.bottom - dpToPx(6f),
                    circleRect.right - dpToPx(4f),
                    circleRect.top + dpToPx(6f)
                ),
                paint
            )
        }
    }


    private var lastPoint: PointF? = null

    private var isAfterScale = false
    private var currentDrawing: BezierCurve? = null
    private var startShapeOnPoint: PointF? = null
    private var lastDifference: Float? = null
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (handleSelectionTap(event)) {
                // Consume the tap and re-render to reflect icon change
                return true
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (editMode) {
                    startShapeOnPoint = PointF(event.x, event.y)
                    render()
                } else {
                    startShapeOnPoint = null
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                startShapeOnPoint = null
                if (event.pointerCount == 2) {
                    lastPoint = PointF(
                        (event.getX(0) + event.getX(1)) / 2,
                        (event.getY(0) + event.getY(1)) / 2
                    )
                    lastDifference = differentBetweenPoints(
                        event.getX(0),
                        event.getY(0),
                        event.getX(1),
                        event.getY(1)
                    )
                }
            }

            MotionEvent.ACTION_MOVE -> {
                var currentPoint = PointF(
                    event.x,
                    event.y
                )
                if (lastPoint == null) {
                    lastPoint = currentPoint
                    return true
                }
                if (event.pointerCount == 2) {
                    currentPoint = PointF(
                        (event.getX(0) + event.getX(1)) / 2,
                        (event.getY(0) + event.getY(1)) / 2
                    )
                    val currentDifference = differentBetweenPoints(
                        event.getX(0),
                        event.getY(0),
                        event.getX(1),
                        event.getY(1)
                    )
                    val difScale = currentDifference / lastDifference!!
                    val difMove = currentPoint - lastPoint!!
                    if (abs(difScale - 1) < 0.001 || abs(difMove.x) < 1 || abs(difMove.y) < 1) return true
                    scale *= difScale
                    //processing scaling
                    movementDifference *= scale / previousScale
                    movementDifference += currentPoint * (1 - scale / previousScale)

                    //processing movement
                    movementDifference += difMove
                    clampMovementDifference()
                    render()
                    lastPoint = currentPoint
                    lastDifference = currentDifference
                } else {
                    if (editMode) {
                        startShapeOnPoint?.let {
                            addShape(it)
                            startShapeOnPoint = null
                        }
                        if (!isAfterScale) drawOnDocuments(currentPoint)
                    } else {
                        val difMove = currentPoint - (lastPoint ?: currentPoint)
                        movementDifference += difMove
                        clampMovementDifference()
                        render()
                        lastPoint = currentPoint
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) isAfterScale = true
                currentDrawing?.close()
                currentDrawing = null
                lastPoint = null
            }

            MotionEvent.ACTION_UP -> {
                currentDrawing?.close()
                currentDrawing = null
                isAfterScale = false
                lastPoint = null
                render(true)
            }
        }
        return true
    }

    private fun differentBetweenPoints(x1: Float, y1: Float, x2: Float, y2: Float) = sqrt(
        (x2 - x1).pow(2) + (y2 - y1).pow(2)
    )

    private fun addShape(point: PointF) {
        val document = documents.getOrNull(activeDocumentIndex) ?: return
        if (!document.contains(point)) return
        currentDrawing = BezierCurve(
            options.lineWidth.toFloat(),
            options.lineColor
        ).also {
            document.addDrawing(point, it)
        }
        operationList.add(activeDocumentIndex)
    }

    private fun drawOnDocuments(point: PointF) {
        val document = documents.getOrNull(activeDocumentIndex) ?: return
        if (!document.contains(point)) return
        val offset = movementDifference + PointF(MARGIN, MARGIN) * scale
        document.addPointToDrawing(point, offset, scale)
        renderDrawing(document)
    }

    private fun clearRenderedContent() {
        renderingJob?.cancel()
        renderingJob = null
        releaseDocuments()
        currentFilePaths = emptyList()
        movementDifference = PointF(0f, 0f)
        if (::layerBitmap.isInitialized && !layerBitmap.isRecycled) {
            layerBitmap.recycle()
        }
        layerBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        binding.viewPort.setImageBitmap(null)
        binding.viewPort.invalidate()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateViewPortSize()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderingJob?.cancel()
        renderingJob = null
        releaseDocuments()
        currentFilePaths = emptyList()
        pendingOptions = null
    }

    private fun releaseDocuments() {
        documents.forEach {
            try {
                it.dispose()
            } catch (_: Exception) {
            }
        }
        documents.clear()
        operationList.clear()
        activeDocumentIndex = 0
        recycleDocumentPreviews()
    }

}
