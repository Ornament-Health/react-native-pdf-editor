package com.ornament.pdfeditor

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.ornament.pdfeditor.document.Document

internal class PDFPageSelectionOverlayRenderer(
  private val resources: Resources,
  private val pageIconSizeDp: Float,
  private val pageIconInsetDp: Float,
  private val pageIconEdgePaddingDp: Float,
) {

  // Reusable paints/path: renderPageSelection runs on every pan/zoom frame, so
  // allocating here once avoids per-frame GC churn.
  private val iconRingStrokePx = dpToPx(2f)

  private val shadePaint = Paint().apply {
    color = Color.argb(70, 80, 80, 80)
    style = Paint.Style.FILL
    isAntiAlias = true
  }
  private val circleFillWhitePaint = Paint().apply {
    color = Color.WHITE
    style = Paint.Style.FILL
    isAntiAlias = true
  }
  private val circleRingPaint = Paint().apply {
    color = Color.parseColor("#EFEFEF")
    style = Paint.Style.STROKE
    strokeWidth = iconRingStrokePx
    isAntiAlias = true
  }
  private val accentFillPaint = Paint().apply {
    style = Paint.Style.FILL
    isAntiAlias = true
  }
  private val ringStrokeWhitePaint = Paint().apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = iconRingStrokePx
    isAntiAlias = true
  }
  private val checkPaint = Paint().apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = dpToPx(2.5f)
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    isAntiAlias = true
  }
  private val checkPath = Path()

  fun renderPageSelection(
    bitmap: Bitmap,
    document: Document,
    activeDocumentIndex: Int,
    excludedPagesByDocument: Map<Int, Set<Int>>,
    viewportSize: android.util.Size,
    selectionIconColor: Int,
  ) {
    renderPageSelection(
      bitmap = bitmap,
      pageBounds = document.pageBounds(),
      activeDocumentIndex = activeDocumentIndex,
      excludedPagesByDocument = excludedPagesByDocument,
      viewportSize = viewportSize,
      selectionIconColor = selectionIconColor,
    )
  }

  fun renderPageSelection(
    bitmap: Bitmap,
    pageBounds: Map<Int, RectF>,
    activeDocumentIndex: Int,
    excludedPagesByDocument: Map<Int, Set<Int>>,
    viewportSize: android.util.Size,
    selectionIconColor: Int,
  ) {
    val canvas = Canvas(bitmap)
    val excluded = excludedPagesByDocument[activeDocumentIndex] ?: emptySet()

    // The accent fill of the included-page circle follows the configurable color.
    accentFillPaint.color = selectionIconColor

    val viewportRect = RectF(0f, 0f, viewportSize.width.toFloat(), viewportSize.height.toFloat())

    pageBounds.forEach { (index, rect) ->
      val pageVisibleRect = RectF()
      val isPageVisible = pageVisibleRect.setIntersect(rect, viewportRect)
      if (!isPageVisible || pageVisibleRect.isEmpty) return@forEach

      val isExcluded = excluded.contains(index)
      if (isExcluded) {
        canvas.drawRect(pageVisibleRect, shadePaint)
      }

      drawSelectionIcon(
        canvas = canvas,
        pageRect = rect,
        visibleRect = pageVisibleRect,
        isExcluded = isExcluded,
      )
    }
  }

  fun handleSelectionTap(
    point: PointF,
    document: Document,
    viewportSize: android.util.Size,
  ): Int? {
    val viewportRect = RectF(0f, 0f, viewportSize.width.toFloat(), viewportSize.height.toFloat())

    for ((index, rect) in document.pageBounds()) {
      val pageVisibleRect = RectF()
      val isPageVisible = pageVisibleRect.setIntersect(rect, viewportRect)
      if (!isPageVisible || pageVisibleRect.isEmpty) continue

      val hitRect = iconHitRect(
        pageRect = rect,
        visibleRect = pageVisibleRect,
      )
      if (hitRect.contains(point.x, point.y)) {
        return index
      }
    }

    return null
  }

  private fun iconHitRect(
    pageRect: RectF,
    visibleRect: RectF,
  ): RectF {
    val proposed = baseIconRect(pageRect)
    val clamped = clampedIconRect(proposed, visibleRect)
    val pad = dpToPx(12f)

    return RectF(
      clamped.left - pad,
      clamped.top - pad,
      clamped.right + pad,
      clamped.bottom + pad,
    )
  }

  private fun drawSelectionIcon(
    canvas: Canvas,
    pageRect: RectF,
    visibleRect: RectF,
    isExcluded: Boolean,
  ) {
    val iconRect = clampedIconRect(baseIconRect(pageRect), visibleRect)
    if (iconRect.isEmpty) return

    val cx = iconRect.centerX()
    val cy = iconRect.centerY()
    val radius = iconRect.width() / 2f

    val circleRadius = radius - iconRingStrokePx / 2f

    if (isExcluded) {
      // Excluded page: empty circle (unselected radio-style state).
      canvas.drawCircle(cx, cy, circleRadius, circleFillWhitePaint)
      canvas.drawCircle(cx, cy, circleRadius, circleRingPaint)
    } else {
      // Included page: accent-filled circle with a white ring and checkmark.
      canvas.drawCircle(cx, cy, circleRadius, accentFillPaint)
      canvas.drawCircle(cx, cy, circleRadius, ringStrokeWhitePaint)

      val left = iconRect.left
      val top = iconRect.top
      val width = iconRect.width()
      val height = iconRect.height()
      checkPath.rewind()
      checkPath.moveTo(left + width * 0.287f, top + height * 0.507f)
      checkPath.lineTo(left + width * 0.447f, top + height * 0.66f)
      checkPath.lineTo(left + width * 0.713f, top + height * 0.353f)
      canvas.drawPath(checkPath, checkPaint)
    }
  }

  private fun baseIconRect(pageRect: RectF): RectF {
    val size = dpToPx(pageIconSizeDp)
    val inset = dpToPx(pageIconInsetDp)
    return RectF(
      pageRect.right - size - inset,
      pageRect.top + inset,
      pageRect.right - inset,
      pageRect.top + size + inset,
    )
  }

  private fun clampedIconRect(
    iconRect: RectF,
    visibleRect: RectF,
  ): RectF {
    if (visibleRect.isEmpty) return RectF()

    val iconWidth = iconRect.width()
    val iconHeight = iconRect.height()
    if (iconWidth <= 0f || iconHeight <= 0f) return RectF()

    val horizontalPadding = dpToPx(pageIconEdgePaddingDp)
    val verticalPadding = dpToPx(pageIconInsetDp)
    if (visibleRect.width() <= iconWidth + horizontalPadding * 2f) {
      return RectF()
    }
    if (visibleRect.height() <= iconHeight + verticalPadding * 2f) {
      return RectF()
    }

    val paddedRect = RectF(
      visibleRect.left + horizontalPadding,
      visibleRect.top + verticalPadding,
      visibleRect.right - horizontalPadding,
      visibleRect.bottom - verticalPadding,
    )

    if (paddedRect.width() < iconWidth || paddedRect.height() < iconHeight) {
      return RectF()
    }

    val left = iconRect.left.coerceIn(paddedRect.left, paddedRect.right - iconWidth)
    val top = iconRect.top.coerceIn(paddedRect.top, paddedRect.bottom - iconHeight)
    return RectF(left, top, left + iconWidth, top + iconHeight)
  }

  private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
