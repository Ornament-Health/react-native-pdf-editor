package com.ornament.pdfeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Size
import android.util.SizeF
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.constraintlayout.widget.ConstraintLayout
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.utils.XmlProcessorCreator
import com.itextpdf.layout.Document
import com.ornament.pdfeditor.bridge.PDFEditorOptions
import com.ornament.pdfeditor.databinding.ViewPdfEditorBinding
import com.ornament.pdfeditor.drawing.BezierCurve
import com.ornament.pdfeditor.extenstions.minus
import com.ornament.pdfeditor.extenstions.plus
import com.ornament.pdfeditor.extenstions.times
import com.ornament.pdfeditor.utils.XmlParserFactory
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class PDFEditorView(context: Context) : ConstraintLayout(context) {

    companion object {
        private const val ACTION_TAG = "ACTION"
        private const val OUTPUT_FILE_NAME = "output"
        private const val PAGE_MARGIN = 10f
        private const val MAX_SCALE = 5f
        private fun getOutputPath(context: Context, contentType: PDFEditorOptions.ContentType) =
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.absolutePath?.plus("/$OUTPUT_FILE_NAME.")?.plus(
                if (contentType == PDFEditorOptions.ContentType.PDF) "pdf" else "png"
            )
    }

    private val binding: ViewPdfEditorBinding
    private lateinit var viewPort: Size

    private lateinit var options: PDFEditorOptions

    private var outputStream = ByteArrayOutputStream()
    private lateinit var pdfDocument: PdfDocument
    private lateinit var renderer: PdfRenderer
    private var currentPage: PdfRenderer.Page? = null
    private lateinit var pageSize: SizeF
    private var movementDifference = PointF(0f, 0f)
    private var currentFilePath = ""
    private var pageCount = 1

    private lateinit var imageBitmap: Bitmap
    private lateinit var layerBitmap: Bitmap

    private var minScale = 1f
        set(value) {
            field = value
            scale = scale.coerceAtLeast(value)
            previousScale = scale
        }

    private val boundsOfPages = mutableMapOf<Int, RectF>()
    private val bitmapPages = mutableMapOf<Int, Bitmap>()
    private val drawing = mutableListOf<BezierCurve>()

    private var scale: Float = minScale
        set(value) {
            previousScale = field
            field = value.coerceAtLeast(minScale).coerceAtMost(MAX_SCALE * minScale)
        }
    private var previousScale = scale

    init {
        XmlProcessorCreator.setXmlParserFactory(XmlParserFactory())
        binding = ViewPdfEditorBinding.inflate(LayoutInflater.from(context), this, true)

        val listener = object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                viewPort = with(binding.root) { Size(width, height) }
                when(options.contentType) {
                    PDFEditorOptions.ContentType.PDF -> {
                        currentPage?.close()
                        currentPage = renderer.openPage(0)
                        pageSize = with(currentPage!!) { SizeF(width.toFloat(), height.toFloat()) }
                        minScale = viewPort.width.toFloat() / (pageSize.width + 2 * PAGE_MARGIN)
                    }
                    PDFEditorOptions.ContentType.IMAGE -> {
                        pageSize = with(imageBitmap) { SizeF(width.toFloat(), height.toFloat()) }
                        minScale = max(viewPort.height.toFloat() / pageSize.height, viewPort.width.toFloat() / pageSize.width)
                    }
                }

                render()
            }
        }
        viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    fun setOptions(options: PDFEditorOptions) {
        this.options = options
        background = ColorDrawable(options.backgroundColor)
        options.fileName?.let {
            load(it)
        }
    }

    private var onSavePDFAction: (filePath: String?) -> Unit = {}

    fun onSavePDF(action: (filePath: String?) -> Unit) {
        onSavePDFAction = action
    }

    fun undo() {
        drawing.removeLastOrNull()
        render()
        Log.i(ACTION_TAG, "UNDO")
    }

    fun save() {
        if (options.contentType == PDFEditorOptions.ContentType.PDF) savePdf()
        else saveImage()
        onSavePDFAction(
            getOutputPath(context, options.contentType)?.also { path ->
                FileOutputStream(path).also { outputFileStream ->
                    outputStream.writeTo(outputFileStream)
                    outputFileStream.close()
                    outputStream.close()
                }
                Log.i(ACTION_TAG, "SAVE")
            }
        )
        load(currentFilePath)
    }
    private fun savePdf() {
        val document = Document(pdfDocument)
        for (pageNumber in 1..pdfDocument.numberOfPages) {
            PdfCanvas(pdfDocument.getPage(pageNumber)).let { pdfCanvas ->
                drawing.filter { it.pageIndex == pageNumber - 1 }.forEach {
                    it.drawOnPdfCanvas(pdfCanvas)
                }
            }
        }
        document.close()
        pdfDocument.close()
    }
    private fun saveImage() {
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
            drawing.filter { it.pageIndex == 0 }.forEach {
                it.drawOnCanvas(
                    canvas,
                    pagePaint,
                    RectF(
                        0f,
                        0f,
                        drawingBitmap.width.toFloat(),
                        drawingBitmap.height.toFloat()
                    ),
                    1f
                )
            }
        }
        val copy = imageBitmap.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(copy).drawBitmap(drawingBitmap, 0f, 0f, Paint())
        copy.compress(Bitmap.CompressFormat.PNG, 100, outputStream)

    }

    fun clear() {
        drawing.clear()
        render()
        Log.i(ACTION_TAG, "CLEAR")
    }

    private fun load(filePath: String) {
        currentFilePath = filePath
        outputStream = ByteArrayOutputStream()
        if (options.contentType == PDFEditorOptions.ContentType.PDF) loadPDF(filePath)
        else loadImage(filePath)
        render()
    }

    private fun loadPDF(filePath: String) {
        pdfDocument = PdfDocument(PdfReader(filePath), PdfWriter(outputStream))
        renderer = PdfRenderer(
            ParcelFileDescriptor.open(
                File(filePath),
                ParcelFileDescriptor.MODE_READ_ONLY
            )
        )
        pageCount = renderer.pageCount
    }

    private fun loadImage(filePath: String) {
        imageBitmap = if (filePath.lowercase().endsWith(".heic"))
            HeifCoder().decode(File(filePath).readBytes())
        else
            BitmapFactory.decodeFile(filePath)
        pageCount = 1
    }

    private fun render(refresh: Boolean = false) {
        if (!::viewPort.isInitialized) return
        layerBitmap = Bitmap.createBitmap(
            viewPort.width,
            viewPort.height,
            Bitmap.Config.ARGB_8888
        )
        when(options.contentType) {
            PDFEditorOptions.ContentType.PDF -> renderPdf(refresh)
            else -> renderImage()
        }
        renderDrawing()

    }

    private fun renderImage() {
        val imageRect = findPdfPageRect(0)
        Canvas(layerBitmap).drawBitmap(imageBitmap, null, imageRect, Paint())
        bitmapPages[0] = imageBitmap
    }

    private fun renderPdf(refresh: Boolean = false) {
        for (pageIndex in 0 until pdfDocument.numberOfPages) {
            showPdfPage(pageIndex, refresh)
        }
    }

    private fun renderPdfPage(index: Int): Bitmap? {
        val pageRect = findPdfPageRect(index)
        if (pageRect.top > viewPort.height || pageRect.bottom < 0) return null
        val bitmap = Bitmap.createBitmap(
            (pageSize.width * scale).toInt(),
            (pageSize.height * scale).toInt(),
            Bitmap.Config.ARGB_8888
        )
        Canvas(bitmap).drawColor(Color.WHITE)
        currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        Log.i("PDF_DOCUMENT", "Rendered ${index + 1} page")
        bitmapPages[index] = bitmap
        return bitmap
    }

    private fun showPdfPage(index: Int, refresh: Boolean = false) {
        val pageRect = findPdfPageRect(index)
        val pageBitmap = (
                if (refresh) renderPdfPage(index)
                else bitmapPages[index] ?: renderPdfPage(index)) ?: return
        Canvas(layerBitmap).drawBitmap(pageBitmap, null, pageRect, Paint())
        Log.i("PDF_DOCUMENT", "Showed ${index + 1} page")
    }

    private fun findPdfPageRect(index: Int): RectF {
        if (options.contentType == PDFEditorOptions.ContentType.PDF) {
            currentPage?.close()
            currentPage = renderer.openPage(index)
        }

        val offset = PointF(
            PAGE_MARGIN * scale + movementDifference.x,
            (PAGE_MARGIN + (pageSize.height + PAGE_MARGIN) * index) * scale + movementDifference.y
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


    private fun renderDrawing() {
        val bitmap = layerBitmap.copy(Bitmap.Config.ARGB_8888, true)
        for (pageIndex in 0 until pageCount) {
            renderDrawingOnPage(pageIndex, bitmap)
        }
        binding.viewPort.setImageBitmap(bitmap)
    }

    private fun renderDrawingOnPage(index: Int, bitmap: Bitmap) {
        val pageRect = boundsOfPages[index] ?: return
        val drawClip = RectF(
            max(pageRect.left, 0f),
            max(pageRect.top, 0f),
            min(pageRect.right, viewPort.width.toFloat()),
            min(pageRect.bottom, viewPort.height.toFloat()),
        )
        if (drawClip.height() < 0) return
        val pagePaint = Paint().apply {
            color = options.lineColor
            strokeWidth = options.lineWidth.toFloat()
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        val pageDrawing = drawing.filter { it.pageIndex == index }
        val drawingBitmap = Bitmap.createBitmap(
            drawClip.width().toInt(),
            drawClip.height().toInt(),
            Bitmap.Config.ARGB_8888
        )
        Canvas(drawingBitmap).let { canvas ->
            pageDrawing.forEach {
                it.drawOnCanvas(
                    canvas,
                    pagePaint,
                    RectF(
                        pageRect.left - drawClip.left,
                        pageRect.top - drawClip.top,
                        drawClip.width(),
                        drawClip.height()
                    ),
                    scale,
                    if (it == currentDrawing) 128 else 255
                )
            }
        }
        Canvas(bitmap).drawBitmap(drawingBitmap, null, drawClip, Paint())
    }


    private var lastPoint: PointF? = null

    private var isAfterScale = false
    private var currentDrawing: BezierCurve? = null
    private var startShapeOnPoint: PointF? = null
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount == 2) {
            scaleDetector.onTouchEvent(event)
        }
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
                    addShapeOnPage(it)
                    startShapeOnPoint = null
                }
                if (event.pointerCount == 2) {
                    currentPoint = PointF(
                        (event.getX(0) + event.getX(1)) / 2,
                        (event.getY(0) + event.getY(1)) / 2
                    )
                    movementDifference *= scale / previousScale
                    movementDifference += currentPoint * (2 - scale / previousScale) - lastPoint!!
                    movementDifference.x = movementDifference.x
                        .coerceAtLeast(viewPort.width - (pageSize.width + PAGE_MARGIN * 2) * scale)
                        .coerceAtMost(0f)
                    movementDifference.y = movementDifference.y
                        .coerceAtMost(0f)
                        .coerceAtLeast(viewPort.height - (pageCount * pageSize.height + PAGE_MARGIN * (pageCount + 1)) * scale)
                    render()
                    lastPoint = currentPoint
                } else if (!isAfterScale) drawOnPage(currentPoint)


            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) isAfterScale = true
                currentDrawing = null
                lastPoint = null
            }

            MotionEvent.ACTION_UP -> {
                currentDrawing = null
                isAfterScale = false
                lastPoint = null
                render(true)
            }
        }
        return true
    }

    private fun addShapeOnPage(point: PointF) {
        var pageIndex: Int? = null
        boundsOfPages.forEach { (index, rect) ->
            if (rect.contains(point.x, point.y)) {
                pageIndex = index
            }
        }
        pageIndex?.let { index ->
            currentDrawing =
                BezierCurve(
                    index,
                    pageSize,
                    options.lineWidth.toFloat() * minScale / scale,
                    options.lineColor
                ).also {
                    drawing.add(it)
                }
        }
    }

    private fun drawOnPage(point: PointF) {
        currentDrawing?.addPoint(point, boundsOfPages[currentDrawing!!.pageIndex]!!)
        renderDrawing()
    }


    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scale *= detector.scaleFactor
                return true
            }
        }
    )

}
