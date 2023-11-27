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
import android.view.ViewTreeObserver.OnGlobalLayoutListener
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

    private var optionsHandled = false
    init {
        XmlProcessorCreator.setXmlParserFactory(XmlParserFactory())
        binding = ViewPdfEditorBinding.inflate(LayoutInflater.from(context), this, true)

        val listener = object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                viewPort = with(binding.root) { Size(width, height) }
                if (!optionsHandled) setOptions(options)
            }
        }
        viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun reset() {
        scale = 1f
        movementDifference = PointF(0f, 0f)
        documents.forEach { it.reset() }
    }

    fun setOptions(options: PDFEditorOptions) {
        this.options = options
        if (!this::viewPort.isInitialized) {
            optionsHandled = false
            return
        }
        optionsHandled = true
        background = ColorDrawable(options.backgroundColor)
        options.filePaths?.let {
            load(it)
        }
        reset()
        render()
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

    private fun load(filePaths: List<String>) {
        currentFilePaths = filePaths
        documents.clear()
        val firstDocument = Document.create(filePaths.first()).also { documents.add(it) }
        val documentsWidth = firstDocument.size.width
        documentsHeight = firstDocument.size.height
        for (index in 1 until filePaths.size) {
            val document = Document.create(filePaths[index]).also { documents.add(it) }
            val factor = documentsWidth / document.size.width
            documentsHeight += document.size.height * factor
            document.minScale *= factor
        }
        val additionalScale = min(documentsHeight / (viewPort.height.toFloat() - 2 * MARGIN), documentsWidth / ( viewPort.width.toFloat() - 2 * MARGIN))
        documentsHeight = documentsHeight / additionalScale + MARGIN * (documents.size - 1)
        documents.forEach { it.minScale /= additionalScale }
    }

    private fun render(refresh: Boolean = false) {
        if (!::viewPort.isInitialized) return
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
                    movementDifference.x = movementDifference.x
                        .coerceAtLeast(viewPort.width - (with(documents.first()) { size.width * minScale } + MARGIN * 2) * scale)
                        .coerceAtMost(0f)
                    movementDifference.y = movementDifference.y
                        .coerceAtMost(0f)
                        .coerceAtLeast(viewPort.height - (documentsHeight + MARGIN * 2) * scale)
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
                    options.lineWidth.toFloat() / scale,
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

}
