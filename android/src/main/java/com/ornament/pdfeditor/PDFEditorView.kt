package com.ornament.pdfeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
  private var bottomOverlayInsetPx: Int = 0
  private var systemBottomInsetPx: Int = 0

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
  private var lastPageBounds: Map<Int, android.graphics.RectF> = emptyMap()

  private lateinit var options: PDFEditorOptions
  private var pendingOptions: PDFEditorOptions? = null

  private var selectionIconColor: Int = Color.WHITE
  private var undoRedoIconColor: Int = Color.WHITE

  private val operationList = mutableListOf<Int>()
  private val redoOperationList = mutableListOf<Int>()

  private var movementDifference = PointF(0f, 0f)
  private var currentFilePaths = listOf<String>()

  private lateinit var layerBitmap: Bitmap

  private val documents = mutableListOf<Document>()

  private val coroutineScope = CoroutineScope(Dispatchers.Main)
  private var renderingJob: Job? = null
  private var scale: Float = 1f
    set(value) {
      previousScale = field
      field = value.coerceAtLeast(1f).coerceAtMost(PDFEditorConstants.MAX_SCALE)
    }
  private var previousScale = scale

  private val pageSelectionRenderer = PDFPageSelectionOverlayRenderer(
    resources = resources,
    pageIconSizeDp = PDFEditorConstants.PAGE_ICON_SIZE_DP,
    pageIconInsetDp = PDFEditorConstants.PAGE_ICON_INSET_DP,
    pageIconEdgePaddingDp = PDFEditorConstants.PAGE_ICON_EDGE_PADDING_DP,
    pageIconBottomHideThresholdDp = PDFEditorConstants.PAGE_ICON_BOTTOM_HIDE_THRESHOLD_DP,
  )

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

    binding.btnUndo.setOnClickListener {
      undo()
    }
    binding.btnRedo.setOnClickListener {
      redo()
    }

    applyUndoRedoColor()
    updateBottomControlsVisibility()
    updateUndoRedoButtons()
    installInsetsAndOverlayObservers()
  }

  fun setEditMode(isEdit: Boolean) {
    val enteringEditMode = !editMode && isEdit
    editMode = isEdit
    updateBottomControlsVisibility()
    if (enteringEditMode) {
      clearHistoryStacks()
    }
    updateUndoRedoButtons()
  }

  private fun updateBottomControlsVisibility() {
    binding.editControlsContainer.isVisible = editMode
    binding.previewList.isVisible = !editMode
    binding.bottomControls.isVisible = true

    binding.bottomControls.requestLayout()

    if (editMode) {
      val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(
        binding.bottomControls.width,
        android.view.View.MeasureSpec.EXACTLY
      )
      val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(
        binding.bottomControls.height,
        android.view.View.MeasureSpec.EXACTLY
      )
      binding.editControlsContainer.measure(widthSpec, heightSpec)
      binding.editControlsContainer.layout(0, 0, binding.bottomControls.width, binding.bottomControls.height)
    }

    binding.bottomControls.post {
      updateViewportBottomInset()
    }
  }

  private fun clearHistoryStacks() {
    operationList.clear()
    redoOperationList.clear()
    updateUndoRedoButtons()
  }

  private fun updateUndoRedoButtons() {
    val canUndo = editMode && operationList.isNotEmpty()
    val canRedo = editMode && redoOperationList.isNotEmpty()
    setButtonState(binding.btnUndo, canUndo)
    setButtonState(binding.btnRedo, canRedo)
  }

  private fun setButtonState(button: AppCompatImageButton, enabled: Boolean) {
    button.isEnabled = enabled
    button.alpha = if (enabled) 1f else PDFEditorConstants.DISABLED_ALPHA
  }

  private fun applyUndoRedoColor() {
    try {
      binding.btnUndo.setColorFilter(undoRedoIconColor)
      binding.btnRedo.setColorFilter(undoRedoIconColor)
    } catch (e: Exception) {
      Log.e("PDFEditorView", "Error applying color: ${e.message}")
    }
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

  private fun updateViewportBottomInset() {
    val controlsHeight = binding.bottomControls.height
    val targetInset = (controlsHeight + systemBottomInsetPx).coerceAtLeast(0)
    if (bottomOverlayInsetPx == targetInset) return

    bottomOverlayInsetPx = targetInset

    val params = binding.viewPort.layoutParams as LayoutParams
    if (params.bottomMargin != targetInset) {
      params.bottomMargin = targetInset
      binding.viewPort.layoutParams = params
    }

    if (documents.isNotEmpty() && this::viewPort.isInitialized) {
      recalculateDocumentLayout()
      clampMovementDifference()
      render(true)
    }
  }

  private fun installInsetsAndOverlayObservers() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
      val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
      systemBottomInsetPx = navBars.bottom
      updateViewportBottomInset()
      insets
    }

    binding.bottomControls.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      updateViewportBottomInset()
    }

    post {
      requestApplyInsets()
      updateViewportBottomInset()
    }
  }

  private fun reset() {
    scale = 1f
    movementDifference = PointF(0f, 0f)
    clearHistoryStacks()
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
    updateViewportBottomInset()
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
    undoRedoIconColor = options.undoRedoIconColor
    applyUndoRedoColor()
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
    if (!editMode) {
      updateUndoRedoButtons()
      return
    }
    operationList.removeLastOrNull()?.let {
      documents[it].undo()
      redoOperationList.add(it)
      updateUndoRedoButtons()
      render()
    }
  }

  fun redo() {
    if (!editMode) {
      updateUndoRedoButtons()
      return
    }
    redoOperationList.removeLastOrNull()?.let {
      documents[it].redo()
      operationList.add(it)
      updateUndoRedoButtons()
      render()
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
    documents.forEach { it.clear() }
    clearHistoryStacks()
    render()
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
    val availableWidth = (viewPort.width.toFloat() - 2 * PDFEditorConstants.MARGIN).coerceAtLeast(1f)
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

    val itemWidthDp = 88 + 12
    val totalWidthDp = documents.size * itemWidthDp - 12
    val density = resources.displayMetrics.density
    val totalWidthPx = (totalWidthDp * density).toInt()
    val screenWidth = resources.displayMetrics.widthPixels
    if (totalWidthPx < screenWidth) {
      val paddingPx = (screenWidth - totalWidthPx) / 2
      binding.previewList.setPadding(
        paddingPx,
        binding.previewList.paddingTop,
        paddingPx,
        binding.previewList.paddingBottom
      )
    } else {
      binding.previewList.setPadding(
        (16 * density).toInt(),
        binding.previewList.paddingTop,
        (16 * density).toInt(),
        binding.previewList.paddingBottom
      )
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
    val margin = PDFEditorConstants.MARGIN * scale
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
      val offset = movementDifference + PointF(PDFEditorConstants.MARGIN, PDFEditorConstants.MARGIN) * scale
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
    pageSelectionRenderer.renderPageSelection(
      bitmap = bitmap,
      document = document,
      activeDocumentIndex = activeDocumentIndex,
      excludedPagesByDocument = excludedPages,
      viewportSize = viewPort,
      selectionIconColor = selectionIconColor,
    )
    binding.viewPort.setImageBitmap(bitmap)
    binding.viewPort.invalidate()
    invalidate()
  }

  private fun handleSelectionTap(point: PointF): Boolean {
    val document = documents.getOrNull(activeDocumentIndex) ?: return false
    val pageIndex = pageSelectionRenderer.handleSelectionTap(
      point = point,
      document = document,
      viewportSize = viewPort,
    ) ?: return false

    toggleExcludedPage(pageIndex)
    return true
  }

  private fun toggleExcludedPage(pageIndex: Int) {
    val document = documents.getOrNull(activeDocumentIndex) ?: return
    val maxPageIndex = (document.pageCount - 1).coerceAtLeast(0)
    if (pageIndex !in 0..maxPageIndex) return
    val current = excludedPages[activeDocumentIndex]?.toMutableSet() ?: mutableSetOf()
    if (!current.add(pageIndex)) {
      current.remove(pageIndex)
    }
    excludedPages[activeDocumentIndex] = current
    render()
  }

  private var lastPoint = PointF(0f, 0f)
  private var isAfterScale = false
  private var currentDrawing: BezierCurve? = null
  private var startShapeOnPoint = PointF(0f, 0f)
  private var lastDifference = PointF(0f, 0f)

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (documents.isEmpty()) return false

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val p = PointF(event.x, event.y)
        if (!editMode && handleSelectionTap(p)) {
          parent?.requestDisallowInterceptTouchEvent(true)
          return true
        }

        lastPoint = p
        startShapeOnPoint = p
        currentDrawing = null
      }

      MotionEvent.ACTION_POINTER_DOWN -> {
        isAfterScale = true
      }

      MotionEvent.ACTION_MOVE -> {
        if (event.pointerCount == 1) {
          val currentPoint = PointF(event.x, event.y)

          if (editMode) {
            val document = documents[activeDocumentIndex]
            if (currentDrawing == null) {
              val curve = BezierCurve(options.lineWidth.toFloat(), options.lineColor)
              document.addDrawing(startShapeOnPoint, curve)
              currentDrawing = curve
              val offset = movementDifference + PointF(PDFEditorConstants.MARGIN, PDFEditorConstants.MARGIN) * scale
              document.addPointToDrawing(startShapeOnPoint, offset, scale)
            }
            val offset = movementDifference + PointF(PDFEditorConstants.MARGIN, PDFEditorConstants.MARGIN) * scale
            document.addPointToDrawing(currentPoint, offset, scale)
            startShapeOnPoint = currentPoint
            render()
          } else {
            val difMove = currentPoint - lastPoint
            movementDifference = movementDifference + difMove
            clampMovementDifference()
            render()
          }

          lastPoint = currentPoint
        } else if (event.pointerCount == 2) {
          val p1 = PointF(event.getX(0), event.getY(0))
          val p2 = PointF(event.getX(1), event.getY(1))
          val currentDifference = differentBetweenPoints(p1, p2)

          val difScale = abs(currentDifference.y / lastDifference.y).let {
            if (it.isNaN() || it.isInfinite()) 1f else it
          }
          val difMove = currentDifference.x - lastDifference.x
          val oldScale = scale
          scale *= difScale
          val scaleRatio = if (oldScale != 0f) scale / oldScale else 1f

          movementDifference = PointF(
            movementDifference.x * scaleRatio + difMove / 2f,
            movementDifference.y * scaleRatio
          )
          clampMovementDifference()
          render()

          if (isAfterScale) {
            isAfterScale = false
          }
          lastDifference = currentDifference
        }
      }

      MotionEvent.ACTION_UP -> {
        if (editMode) {
          addShape(PointF(event.x, event.y))
        }
      }

      MotionEvent.ACTION_CANCEL -> {
        val document = documents.getOrNull(activeDocumentIndex)
        val curve = currentDrawing
        if (document != null && curve != null && !curve.isClosed) {
          document.undo()
          currentDrawing = null
          render()
        }
      }
    }

    return true
  }

  private fun differentBetweenPoints(firstPoint: PointF, secondPoint: PointF): PointF {
    return PointF(secondPoint.x - firstPoint.x, secondPoint.y - firstPoint.y)
  }

  private fun addShape(point: PointF) {
    val document = documents.getOrNull(activeDocumentIndex) ?: return
    val curve = currentDrawing ?: return
    val offset = movementDifference + PointF(PDFEditorConstants.MARGIN, PDFEditorConstants.MARGIN) * scale
    document.addPointToDrawing(point, offset, scale)
    curve.close()
    operationList.add(activeDocumentIndex)
    redoOperationList.clear()
    currentDrawing = null
    updateUndoRedoButtons()
    render()
  }

  private fun clearRenderedContent() {
    binding.viewPort.setImageBitmap(null)
    binding.viewPort.invalidate()
    recycleDocumentPreviews()
    releaseDocuments()
    documents.clear()
    excludedPages.clear()
    lastPageBounds = emptyMap()
    activeDocumentIndex = 0
    movementDifference = PointF(0f, 0f)
    operationList.clear()
    redoOperationList.clear()
    updateUndoRedoButtons()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    updateViewPortSize()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    renderingJob?.cancel()
    renderingJob = null
    binding.viewPort.setImageBitmap(null)
    recycleDocumentPreviews()
    releaseDocuments()
  }

  private fun releaseDocuments() {
    documents.forEach { document ->
      try {
        document.dispose()
      } catch (error: Throwable) {
        Log.w(PDFEditorConstants.ACTION_TAG, "Error releasing document", error)
      }
    }
    documents.clear()
  }
}
