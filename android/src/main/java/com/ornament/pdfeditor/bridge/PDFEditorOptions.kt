package com.ornament.pdfeditor.bridge

import android.graphics.Color
import com.facebook.react.bridge.ReadableMap

class PDFEditorOptions(options: ReadableMap) {
  val filePaths = options.getArray("files")?.toArrayList()?.map { it as String }
  private val drawLine = options.getMap("drawLine")
  val lineColor: Int = parseColor(drawLine?.getString("color"), DEFAULT_LINE_COLOR)
  val lineWidth: Double =
    if (drawLine != null && drawLine.hasKey("width") && !drawLine.isNull("width")) {
      drawLine.getDouble("width")
    } else {
      DEFAULT_LINE_WIDTH
    }
  private val icons = options.getMap("icons")
  val selectionIconColor: Int = parseColor(icons?.getString("unselectedColor"), Color.WHITE)
  val undoRedoIconColor: Int = parseColor(icons?.getString("undoRedoColor"), Color.WHITE)

  // Color.parseColor throws on a malformed hex string; fall back instead of
  // crashing the native view when a consumer passes an invalid value.
  private fun parseColor(value: String?, fallback: Int): Int =
    value?.let {
      try {
        Color.parseColor(it)
      } catch (_: IllegalArgumentException) {
        fallback
      }
    } ?: fallback

  companion object {
    // Keep in sync with DEFAULT_OPTIONS in src/PDFEditorView.tsx — these only
    // apply if a consumer drives the native view without the JS wrapper.
    private val DEFAULT_LINE_COLOR = Color.parseColor("#555555")
    private const val DEFAULT_LINE_WIDTH = 10.0
  }
}
