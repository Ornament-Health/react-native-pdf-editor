package com.ornament.pdfeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import com.itextpdf.kernel.utils.XmlProcessorCreator
import com.ornament.pdfeditor.bridge.PDFEditorOptions
import com.ornament.pdfeditor.databinding.ViewPdfEditorBinding
import com.ornament.pdfeditor.document.Document
import com.ornament.pdfeditor.drawing.BezierCurve
import com.ornament.pdfeditor.extenstions.minus
import com.ornament.pdfeditor.extenstions.plus
import com.ornament.pdfeditor.extenstions.times
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

    companion object {
        private const val ACTION_TAG = "ACTION"
        private const val MARGIN = 20f
        private const val MAX_SCALE = 5f
    }

    private val outputDirectory =
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.absolutePath

    private val binding: ViewPdfEditorBinding
    private lateinit var viewPort: Size

    private lateinit var options: PDFEditorOptions
    private var pendingOptions: PDFEditorOptions? = null

    private val operationList = mutableListOf<Int>()

    private var movementDifference = PointF(0f, 0f)
    private var currentFilePaths = listOf<String>()

    private lateinit var layerBitmap: Bitmap

    private val documents = mutableListOf<Document>()
    private var documentsHeight = 0f

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
    }

    private fun reset() {
        scale = 1f
        movementDifference = PointF(0f, 0f)
        operationList.clear()
        documents.forEach { it.reset() }
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
        background = ColorDrawable(options.backgroundColor)
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
            centerDocuments()
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
            documents.forEach {
                outputs?.add(
                    saveDocument(it) ?: run {
                        outputs = null
                        return@forEach
                    }
                )
            }
            onSavePDFAction(outputs)
        }
    }

    private fun saveDocument(document: Document): String? {
        return outputDirectory?.let {
            document.save(it, options)
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
            documentsHeight = 0f
            return
        }

        val firstDocument = Document.create(filePaths.first(), context.contentResolver).also { documents.add(it) }
        val documentsWidth = firstDocument.size.width
        var totalDocumentsHeight = firstDocument.size.height
        for (index in 1 until filePaths.size) {
            val document = Document.create(filePaths[index], context.contentResolver).also { documents.add(it) }
            val factor = documentsWidth / document.size.width
            totalDocumentsHeight += document.size.height * factor
            document.minScale *= factor
        }
        val availableWidth = (viewPort.width.toFloat() - 2 * MARGIN).coerceAtLeast(1f)
        val additionalScale = (documentsWidth / availableWidth).coerceAtLeast(0.0001f)
        documentsHeight = totalDocumentsHeight / additionalScale + MARGIN * (documents.size - 1)
        documents.forEach { it.minScale /= additionalScale }
    }

    private fun recalculateDocumentLayout() {
        if (documents.isEmpty()) {
            documentsHeight = 0f
            return
        }
        val documentsWidth = documents.first().size.width
        documents.first().minScale = 1f
        var totalDocumentsHeight = documents.first().size.height
        for (index in 1 until documents.size) {
            val document = documents[index]
            val factor = documentsWidth / document.size.width
            totalDocumentsHeight += document.size.height * factor
            document.minScale = factor
        }
        val availableWidth = (viewPort.width.toFloat() - 2 * MARGIN).coerceAtLeast(1f)
        val additionalScale = (documentsWidth / availableWidth).coerceAtLeast(0.0001f)
        documentsHeight = totalDocumentsHeight / additionalScale + MARGIN * (documents.size - 1)
        documents.forEach { it.minScale /= additionalScale }
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
        if (documents.isEmpty()) return 0f
        return documents.first().size.width * documents.first().minScale * scale
    }

    private fun contentHeight(): Float {
        if (documents.isEmpty()) return 0f
        var totalHeight = 0f
        documents.forEach { document ->
            totalHeight += document.size.height * document.minScale
        }
        val gaps = (documents.size - 1).coerceAtLeast(0) * MARGIN * scale
        return totalHeight * scale + gaps
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
        if (documents.isEmpty()) {
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
            documents.forEach {
                it.render(canvas, scale, offset, viewPort, refresh)
                offset.y += it.size.height * scale * it.minScale + MARGIN * scale
            }
            renderDrawing()
        }
    }

    private fun renderDrawing() {
        val bitmap = layerBitmap.copy(Bitmap.Config.ARGB_8888, true)
        documents.forEach {
            it.renderDrawing(
                bitmap,
                scale,
                viewPort,
                options.lineColor,
                options.lineWidth
            )
        }
        binding.viewPort.setImageBitmap(bitmap)
        binding.viewPort.invalidate()
        invalidate()
    }


    private var lastPoint: PointF? = null

    private var isAfterScale = false
    private var currentDrawing: BezierCurve? = null
    private var startShapeOnPoint: PointF? = null
    private var lastDifference: Float? = null
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startShapeOnPoint = PointF(event.x, event.y)
                render()
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
                startShapeOnPoint?.let {
                    addShape(it)
                    startShapeOnPoint = null
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
                } else if (!isAfterScale) drawOnDocuments(currentPoint)


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
        val offset = movementDifference + PointF(MARGIN, MARGIN) * scale
        documents.forEachIndexed { index, document ->
            if (document.contains(point)) {
                currentDrawing = BezierCurve(
                    options.lineWidth.toFloat(),
                    options.lineColor
                ).also {
                    document.addDrawing(point, it)
                }
                operationList.add(index)
                return@forEachIndexed
            }
            offset.y += document.size.height * scale * document.minScale + MARGIN * scale
        }

    }

    private fun drawOnDocuments(point: PointF) {
        val offset = movementDifference + PointF(MARGIN, MARGIN) * scale
        documents.forEach { document ->
            if (document.contains(point)) {
                document.addPointToDrawing(point,offset, scale)
                renderDrawing()
                return@forEach
            }
            offset.y += document.size.height * scale * document.minScale + MARGIN * scale
        }
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
        if (w <= 0 || h <= 0) return
        viewPort = Size(w, h)
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
        documentsHeight = 0f
    }

}
