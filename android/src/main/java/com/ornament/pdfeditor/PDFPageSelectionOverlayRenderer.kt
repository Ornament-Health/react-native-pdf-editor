package com.ornament.pdfeditor

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import com.ornament.pdfeditor.document.Document

internal class PDFPageSelectionOverlayRenderer(
  private val resources: Resources,
  private val pageIconSizeDp: Float,
  private val pageIconInsetDp: Float,
  private val pageIconEdgePaddingDp: Float,
  private val pageIconBottomHideThresholdDp: Float,
) {

  fun renderPageSelection(
    bitmap: Bitmap,
    document: Document,
    activeDocumentIndex: Int,
    excludedPagesByDocument: Map<Int, Set<Int>>,
    viewportSize: android.util.Size,
    selectionIconColor: Int,
  ) {
    val canvas = Canvas(bitmap)
    val excluded = excludedPagesByDocument[activeDocumentIndex] ?: emptySet()

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

    val viewportRect = RectF(0f, 0f, viewportSize.width.toFloat(), viewportSize.height.toFloat())

    document.pageBounds().forEach { (index, rect) ->
      val pageVisibleRect = RectF()
      val isPageVisible = pageVisibleRect.setIntersect(rect, viewportRect)
      if (!isPageVisible || pageVisibleRect.isEmpty) return@forEach

      val isExcluded = excluded.contains(index)
      if (isExcluded) {
        canvas.drawRect(pageVisibleRect, shadePaint)
      }

      iconPaint.color = if (isExcluded) selectionIconColor else Color.WHITE
      drawSelectionIcon(
        canvas = canvas,
        pageRect = rect,
        visibleRect = pageVisibleRect,
        iconPaint = iconPaint,
        iconBgPaint = iconBgPaint,
        isExcluded = isExcluded,
        viewportSize = viewportSize,
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
        viewportSize = viewportSize,
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
    viewportSize: android.util.Size,
  ): RectF {
    val proposed = baseIconRect(pageRect)
    val clamped = clampedIconRect(proposed, visibleRect, viewportSize)
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
    iconPaint: Paint,
    iconBgPaint: Paint,
    isExcluded: Boolean,
    viewportSize: android.util.Size,
  ) {
    val iconRect = clampedIconRect(baseIconRect(pageRect), visibleRect, viewportSize)
    if (iconRect.isEmpty) return

    val cx = iconRect.centerX()
    val cy = iconRect.centerY()
    val radius = iconRect.width() / 2f
    val circleRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

    canvas.drawOval(circleRect, iconBgPaint)
    canvas.drawOval(circleRect, iconPaint)

    val iconInset = radius * 0.38f
    val left = circleRect.left + iconInset
    val top = circleRect.top + iconInset
    val right = circleRect.right - iconInset
    val bottom = circleRect.bottom - iconInset

    if (isExcluded) {
      canvas.drawLine(left, top, right, bottom, iconPaint)
      canvas.drawLine(right, top, left, bottom, iconPaint)
    } else {
      val midX = (left + right) / 2f
      val tickStartX = left + (right - left) * 0.12f
      val tickStartY = top + (bottom - top) * 0.58f
      val tickMidX = left + (right - left) * 0.42f
      val tickMidY = top + (bottom - top) * 0.82f
      val tickEndX = right - (right - left) * 0.10f
      val tickEndY = top + (bottom - top) * 0.20f

      canvas.drawLine(tickStartX, tickStartY, tickMidX, tickMidY, iconPaint)
      canvas.drawLine(tickMidX, tickMidY, tickEndX, tickEndY, iconPaint)
      canvas.drawLine(midX, top + (bottom - top) * 0.1f, midX, top + (bottom - top) * 0.3f, iconPaint)
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
    viewportSize: android.util.Size,
  ): RectF {
    if (visibleRect.isEmpty) return RectF()

    val threshold = dpToPx(pageIconBottomHideThresholdDp)
    if (visibleRect.bottom >= viewportSize.height - threshold) {
      return RectF()
    }

    val iconWidth = iconRect.width()
    val iconHeight = iconRect.height()
    if (iconWidth <= 0f || iconHeight <= 0f) return RectF()

    val edgePadding = dpToPx(pageIconEdgePaddingDp)
    val paddedRect = RectF(
      visibleRect.left + edgePadding,
      visibleRect.top + edgePadding,
      visibleRect.right - edgePadding,
      visibleRect.bottom - edgePadding,
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
