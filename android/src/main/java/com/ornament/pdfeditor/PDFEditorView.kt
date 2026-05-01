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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.itextpdf.kernel.utils.XmlProcessorCreator
import com.ornament.pdfeditor.R
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
  private val previewWidthPx =
    resources.getDimensionPixelSize(R.dimen.pdfeditor_preview_thumbnail_width).coerceAtLeast(1)
  private val previewHeightPx =
    resources.getDimensionPixelSize(R.dimen.pdfeditor_preview_thumbnail_height).coerceAtLeast(1)
  private val previewItemWidthPx =
    resources.getDimensionPixelSize(R.dimen.pdfeditor_preview_item_width)
  private val previewItemSpacingPx =
    resources.getDimensionPixelSize(R.dimen.pdfeditor_preview_item_spacing)
  private val previewListSidePaddingPx =
    resources.getDimensionPixelSize(R.dimen.pdfeditor_preview_panel_horizontal_padding)
  private val documentPreviews = mutableListOf<DocumentPreviewItem>()
  private var activeDocumentIndex = 0

  private val excludedPages = mutableMapOf<Int, Set<Int>>()
  private var lastPageBounds: Map<Int, android.graphics.RectF> = emptyMap()
  private var zoomReferencePageBounds: Map<Int, android.graphics.RectF> = emptyMap()

  private lateinit var options: PDFEditorOptions
  private var pendingOptions: PDFEditorOptions? = null

  private var selectionIconColor: Int = Color.WHITE
  private var undoRedoIconColor: Int = Color.WHITE

  private val operationList = mutableListOf<Int>()
  private val redoOperationList = mutableListOf<Int>()

  private var movementDifference = PointF(0f, 0f)
  private var currentFilePaths = listOf<String>()

  private val documents = mutableListOf<Document>()

  private val coroutineScope = CoroutineScope(Dispatchers.Main)
  private var renderingJob: Job? = null
  private var renderQueued = false
  private var pendingRefresh = false
  private var scale: Float = 1f
    set(value) {
      previousScale = field
      field = value.coerceAtLeast(1f).coerceAtMost(PDFEditorConstants.MAX_SCALE)
    }
  private var previousScale = scale
  private var baseLayerBitmap: Bitmap? = null
  private var composedLayerBitmap: Bitmap? = null
  private var zoomReferenceBitmap: Bitmap? = null
  private var zoomReferenceScale: Float = 1f
  private var zoomReferenceOffset: PointF = PointF(0f, 0f)
  private var lastInteractiveHighQualityRenderNs: Long = 0L
  private var lastInteractiveHighQualityScale: Float = 1f
  private val interactiveHighQualityIntervalNs = 80_000_000L
  private val interactiveHighQualityZoomOutIntervalNs = 28_000_000L
  private val zoomOutFullRenderThreshold = 0.08f
  private val previewCoverageThreshold = 0.985f
  private var previewFrames = 0
  private var previewFallbackFrames = 0
  private var previewCoverageAccum = 0f

  private val pageSelectionRenderer = PDFPageSelectionOverlayRenderer(
    resources = resources,
    pageIconSizeDp = PDFEditorConstants.PAGE_ICON_SIZE_DP,
    pageIconInsetDp = PDFEditorConstants.PAGE_ICON_INSET_DP,
    pageIconEdgePaddingDp = PDFEditorConstants.PAGE_ICON_EDGE_PADDING_DP,
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

    if (documents.isNotEmpty() && this::viewPort.isInitialized) {
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
      documentPreviews.add(
        DocumentPreviewItem(
          index = index,
          thumbnail = thumbnail,
          isMultiPage = document.pageCount > 1,
        )
      )
    }
    previewAdapter.submit(documentPreviews.toList(), activeDocumentIndex)
    binding.previewList.isVisible = true

    val totalWidthPx =
      documents.size * (previewItemWidthPx + previewItemSpacingPx) - previewItemSpacingPx
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
        previewListSidePaddingPx,
        binding.previewList.paddingTop,
        previewListSidePaddingPx,
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
    val (minY, maxY) = verticalBoundsFor(contentHeight(), viewPort.height.toFloat())
    movementDifference.x = movementDifference.x.coerceIn(minX, maxX)
    movementDifference.y = movementDifference.y.coerceIn(minY, maxY)
  }

  private fun centerDocuments() {
    if (!::viewPort.isInitialized || documents.isEmpty()) {
      movementDifference = PointF(0f, 0f)
      return
    }
    val (minX, maxX) = boundsFor(contentWidth(), viewPort.width.toFloat())
    val (minY, maxY) = verticalBoundsFor(contentHeight(), viewPort.height.toFloat())
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

  private fun verticalBoundsFor(content: Float, container: Float): Pair<Float, Float> {
    val (minY, maxY) = boundsFor(content, container)
    val spacer = bottomOverlayInsetPx.toFloat()
    return (minY - spacer) to maxY
  }

  private fun render(refresh: Boolean = false) {
    if (!::viewPort.isInitialized || viewPort.width == 0 || viewPort.height == 0) return

    pendingRefresh = pendingRefresh || refresh
    if (renderQueued) return

    renderQueued = true
    postOnAnimation {
      renderQueued = false
      val shouldRefresh = pendingRefresh
      pendingRefresh = false
      performRender(shouldRefresh)
      if (pendingRefresh && !renderQueued) {
        render()
      }
    }
  }

  private fun performRender(refresh: Boolean = false) {
    if (!::viewPort.isInitialized || viewPort.width == 0 || viewPort.height == 0) return
    val document = documents.getOrNull(activeDocumentIndex)
    if (document == null) {
      clearRenderedContent()
      return
    }

    renderingJob?.cancel()
    renderingJob = coroutineScope.launch {
      val renderStartNs = System.nanoTime()
      val width = viewPort.width
      val height = viewPort.height
      val nowNs = System.nanoTime()
      val zoomingOut = isPinching && scale < previousScale
      if (isPinching && !refresh && shouldUseInteractivePreview(nowNs, zoomingOut)) {
        if (renderInteractivePreview(width, height, zoomingOut)) {
          pinchRenderAccumNs += (System.nanoTime() - renderStartNs)
          pinchRenderFrames += 1
          return@launch
        }
      }
      val baseBitmap = obtainLayerBitmap(baseLayerBitmap, width, height, Color.TRANSPARENT)
      baseLayerBitmap = baseBitmap
      val canvas = Canvas(baseBitmap)
      val offset = documentOffset(scale)
      document.render(canvas, scale, offset, viewPort, refresh, isPinching, zoomingOut)
      if (isPinching) {
        renderDrawing(document, baseBitmap)
        lastInteractiveHighQualityRenderNs = nowNs
        lastInteractiveHighQualityScale = scale
      } else {
        val composedBitmap = obtainLayerBitmap(composedLayerBitmap, width, height, Color.TRANSPARENT)
        composedLayerBitmap = composedBitmap
        Canvas(composedBitmap).drawBitmap(baseBitmap, 0f, 0f, null)
        renderDrawing(document, composedBitmap)
      }

      if (isPinching) {
        pinchRenderAccumNs += (System.nanoTime() - renderStartNs)
        pinchRenderFrames += 1
      }
    }
  }

  private fun renderDrawing(document: Document, bitmap: Bitmap) {
    document.renderDrawing(
      bitmap,
      scale,
      viewPort,
      options.lineColor,
      options.lineWidth
    )
    val pageBounds = copyPageBounds(document.pageBounds())
    lastPageBounds = pageBounds
    updateZoomReference(bitmap, pageBounds)
    renderPageSelectionOverlay(bitmap, pageBounds)
    presentBitmap(bitmap)
  }

  private fun renderPageSelectionOverlay(
    bitmap: Bitmap,
    pageBounds: Map<Int, android.graphics.RectF>,
  ) {
    pageSelectionRenderer.renderPageSelection(
      bitmap = bitmap,
      pageBounds = pageBounds,
      activeDocumentIndex = activeDocumentIndex,
      excludedPagesByDocument = excludedPages,
      viewportSize = viewPort,
      selectionIconColor = selectionIconColor,
    )
  }

  private fun presentBitmap(bitmap: Bitmap) {
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
  private var isPinching = false
  private var lastPinchDistance = 0f
  private val minPinchDistancePx = 8f
  private val minScaleFactorPerFrame = 0.9f
  private val maxScaleFactorPerFrame = 1.1f
  private var pinchGestureStartNs = 0L
  private var pinchRenderAccumNs = 0L
  private var pinchRenderFrames = 0
  private var pinchEvents = 0
  private var currentDrawing: BezierCurve? = null
  private var startShapeOnPoint = PointF(0f, 0f)

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
        if (editMode && currentDrawing != null) {
          addShape(startShapeOnPoint)
        }
        if (event.pointerCount >= 2) {
          beginPinch(event)
        }
      }

      MotionEvent.ACTION_POINTER_UP -> {
        if (isPinching && event.pointerCount <= 2) {
          endPinch()
        }
        val remainingIndex = if (event.actionIndex == 0) 1 else 0
        if (remainingIndex in 0 until event.pointerCount) {
          lastPoint = PointF(event.getX(remainingIndex), event.getY(remainingIndex))
        }
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
          if (!isPinching) {
            beginPinch(event)
          }

          val distance = pinchDistance(event)
          if (distance <= minPinchDistancePx) return true

          val rawScaleFactor = (distance / lastPinchDistance).let {
            if (it.isNaN() || it.isInfinite()) 1f else it
          }
          val frameScaleFactor = rawScaleFactor.coerceIn(minScaleFactorPerFrame, maxScaleFactorPerFrame)
          val newScale = (scale * frameScaleFactor).coerceAtLeast(1f).coerceAtMost(PDFEditorConstants.MAX_SCALE)
          val focus = pinchFocus(event)
          applyScaleAroundFocus(newScale, focus)
          lastPinchDistance = distance
          pinchEvents += 1
          render()
        }
      }

      MotionEvent.ACTION_UP -> {
        if (isPinching) {
          endPinch()
        }
        if (editMode) {
          addShape(PointF(event.x, event.y))
        }
      }

      MotionEvent.ACTION_CANCEL -> {
        if (isPinching) {
          endPinch()
        }
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
    recycleLayerBitmaps()
    recycleDocumentPreviews()
    releaseDocuments()
    documents.clear()
    excludedPages.clear()
    lastPageBounds = emptyMap()
    zoomReferencePageBounds = emptyMap()
    activeDocumentIndex = 0
    movementDifference = PointF(0f, 0f)
    lastInteractiveHighQualityRenderNs = 0L
    lastInteractiveHighQualityScale = scale
    previewFrames = 0
    previewFallbackFrames = 0
    previewCoverageAccum = 0f
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
    recycleLayerBitmaps()
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

  private fun recycleLayerBitmaps() {
    baseLayerBitmap?.let { bitmap ->
      if (!bitmap.isRecycled) bitmap.recycle()
    }
    baseLayerBitmap = null

    composedLayerBitmap?.let { bitmap ->
      if (!bitmap.isRecycled) bitmap.recycle()
    }
    composedLayerBitmap = null

    zoomReferenceBitmap?.let { bitmap ->
      if (!bitmap.isRecycled) bitmap.recycle()
    }
    zoomReferenceBitmap = null
    zoomReferencePageBounds = emptyMap()
  }

  private fun obtainLayerBitmap(current: Bitmap?, width: Int, height: Int, clearColor: Int): Bitmap {
    if (current != null && !current.isRecycled && current.width == width && current.height == height) {
      current.eraseColor(clearColor)
      return current
    }
    if (current != null && !current.isRecycled) {
      current.recycle()
    }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
      it.eraseColor(clearColor)
    }
  }

  private fun pinchDistance(event: MotionEvent): Float {
    if (event.pointerCount < 2) return 0f
    val dx = event.getX(0) - event.getX(1)
    val dy = event.getY(0) - event.getY(1)
    return sqrt(dx * dx + dy * dy)
  }

  private fun pinchFocus(event: MotionEvent): PointF {
    if (event.pointerCount < 2) return PointF(event.x, event.y)
    return PointF((event.getX(0) + event.getX(1)) / 2f, (event.getY(0) + event.getY(1)) / 2f)
  }

  private fun beginPinch(event: MotionEvent) {
    val distance = pinchDistance(event).coerceAtLeast(minPinchDistancePx)
    lastPinchDistance = distance
    isPinching = true
    pinchGestureStartNs = System.nanoTime()
    pinchRenderAccumNs = 0L
    pinchRenderFrames = 0
    pinchEvents = 0
    previewFrames = 0
    previewFallbackFrames = 0
    previewCoverageAccum = 0f
    lastInteractiveHighQualityRenderNs = System.nanoTime()
    lastInteractiveHighQualityScale = scale
  }

  private fun endPinch() {
    if (!isPinching) return
    isPinching = false
    lastPinchDistance = 0f
    val durationMs = (System.nanoTime() - pinchGestureStartNs) / 1_000_000f
    val avgRenderMs =
      if (pinchRenderFrames > 0) pinchRenderAccumNs.toFloat() / pinchRenderFrames / 1_000_000f else 0f
    val avgPreviewCoverage =
      if (previewFrames > 0) previewCoverageAccum / previewFrames else 1f
    Log.d(
      PDFEditorConstants.ACTION_TAG,
      "zoom-metrics durationMs=$durationMs events=$pinchEvents frames=$pinchRenderFrames avgRenderMs=$avgRenderMs previewFrames=$previewFrames previewFallbacks=$previewFallbackFrames avgPreviewCoverage=$avgPreviewCoverage",
    )
    render(true)
  }

  private fun applyScaleAroundFocus(newScale: Float, focus: PointF) {
    val oldScale = scale
    if (newScale == oldScale) return

    val oldOffset = movementDifference + PointF(PDFEditorConstants.MARGIN, PDFEditorConstants.MARGIN) * oldScale
    val contentX = (focus.x - oldOffset.x) / oldScale
    val contentY = (focus.y - oldOffset.y) / oldScale

    scale = newScale
    val newOffset = PointF(
      focus.x - contentX * newScale,
      focus.y - contentY * newScale,
    )
    movementDifference = newOffset - PointF(PDFEditorConstants.MARGIN, PDFEditorConstants.MARGIN) * newScale
    clampMovementDifference()
  }

  private fun documentOffset(currentScale: Float): PointF {
    return movementDifference + PointF(PDFEditorConstants.MARGIN, PDFEditorConstants.MARGIN) * currentScale
  }

  private fun shouldUseInteractivePreview(nowNs: Long, zoomingOut: Boolean): Boolean {
    if (zoomReferenceBitmap == null) return false
    val interval = if (zoomingOut) interactiveHighQualityZoomOutIntervalNs else interactiveHighQualityIntervalNs
    if ((nowNs - lastInteractiveHighQualityRenderNs) >= interval) return false
    if (zoomingOut && abs(scale - lastInteractiveHighQualityScale) > zoomOutFullRenderThreshold) return false
    return true
  }

  private fun renderInteractivePreview(width: Int, height: Int, zoomingOut: Boolean): Boolean {
    val referenceBitmap = zoomReferenceBitmap
    if (referenceBitmap == null || referenceBitmap.isRecycled) return false
    val previewBitmap = obtainLayerBitmap(baseLayerBitmap, width, height, Color.TRANSPARENT)
    baseLayerBitmap = previewBitmap
    val previewCanvas = Canvas(previewBitmap)
    previewCanvas.drawColor(Color.TRANSPARENT)

    val referenceScale = zoomReferenceScale.coerceAtLeast(0.0001f)
    val scaleFactor = (scale / referenceScale).coerceAtLeast(0.0001f)
    val targetOffset = documentOffset(scale)
    val referenceOffset = zoomReferenceOffset
    val coverage = previewCoverage(referenceBitmap, scaleFactor, targetOffset, referenceOffset, width, height)
    previewFrames += 1
    previewCoverageAccum += coverage
    val hasCoverageGap = zoomingOut && coverage < previewCoverageThreshold
    if (hasCoverageGap) {
      previewFallbackFrames += 1
      if (previewFallbackFrames % 5 == 0) {
        Log.d(
          PDFEditorConstants.ACTION_TAG,
          "preview-coverage-fallback coverage=$coverage threshold=$previewCoverageThreshold",
        )
      }
      return false
    }

    previewCanvas.save()
    previewCanvas.translate(targetOffset.x, targetOffset.y)
    previewCanvas.scale(scaleFactor, scaleFactor)
    previewCanvas.translate(-referenceOffset.x, -referenceOffset.y)
    previewCanvas.drawBitmap(referenceBitmap, 0f, 0f, null)
    previewCanvas.restore()

    val previewPageBounds = transformPageBounds(
      pageBounds = zoomReferencePageBounds,
      scaleFactor = scaleFactor,
      targetOffset = targetOffset,
      referenceOffset = referenceOffset,
    )
    renderPageSelectionOverlay(previewBitmap, previewPageBounds)
    presentBitmap(previewBitmap)
    return true
  }

  private fun previewCoverage(
    referenceBitmap: Bitmap,
    scaleFactor: Float,
    targetOffset: PointF,
    referenceOffset: PointF,
    width: Int,
    height: Int,
  ): Float {
    if (width <= 0 || height <= 0) return 1f
    val left = targetOffset.x - referenceOffset.x * scaleFactor
    val top = targetOffset.y - referenceOffset.y * scaleFactor
    val right = left + referenceBitmap.width * scaleFactor
    val bottom = top + referenceBitmap.height * scaleFactor
    val intersectionLeft = maxOf(0f, left)
    val intersectionTop = maxOf(0f, top)
    val intersectionRight = minOf(width.toFloat(), right)
    val intersectionBottom = minOf(height.toFloat(), bottom)
    if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) return 0f
    val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
    val viewportArea = width.toFloat() * height.toFloat()
    return if (viewportArea <= 0f) 1f else (intersectionArea / viewportArea).coerceIn(0f, 1f)
  }

  private fun updateZoomReference(
    source: Bitmap?,
    pageBounds: Map<Int, android.graphics.RectF> = lastPageBounds,
  ) {
    val sourceBitmap = source
    if (sourceBitmap == null || sourceBitmap.isRecycled) return
    val referenceBitmap = obtainLayerBitmap(
      zoomReferenceBitmap,
      sourceBitmap.width,
      sourceBitmap.height,
      Color.TRANSPARENT,
    )
    zoomReferenceBitmap = referenceBitmap
    Canvas(referenceBitmap).drawBitmap(sourceBitmap, 0f, 0f, null)
    zoomReferencePageBounds = copyPageBounds(pageBounds)
    zoomReferenceScale = scale
    zoomReferenceOffset = documentOffset(scale)
  }

  private fun transformPageBounds(
    pageBounds: Map<Int, android.graphics.RectF>,
    scaleFactor: Float,
    targetOffset: PointF,
    referenceOffset: PointF,
  ): Map<Int, android.graphics.RectF> =
    pageBounds.mapValues { (_, rect) ->
      android.graphics.RectF(
        targetOffset.x + (rect.left - referenceOffset.x) * scaleFactor,
        targetOffset.y + (rect.top - referenceOffset.y) * scaleFactor,
        targetOffset.x + (rect.right - referenceOffset.x) * scaleFactor,
        targetOffset.y + (rect.bottom - referenceOffset.y) * scaleFactor,
      )
    }

  private fun copyPageBounds(
    pageBounds: Map<Int, android.graphics.RectF>,
  ): Map<Int, android.graphics.RectF> =
    pageBounds.mapValues { (_, rect) -> android.graphics.RectF(rect) }
}
