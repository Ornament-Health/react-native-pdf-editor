package com.ornament.pdfeditor.bridge

import android.graphics.Color
import com.facebook.react.bridge.ReadableMap

class PDFEditorOptions(options: ReadableMap) {
  val filePaths = options.getArray("files")?.toArrayList()?.map { it as String }
  private val drawLine = options.getMap("drawLine")
  val lineColor: Int =
    drawLine?.getString("color")?.let { Color.parseColor(it) } ?: Color.parseColor("#FF0000")
  val lineWidth: Double =
    if (drawLine != null && drawLine.hasKey("width") && !drawLine.isNull("width")) {
      drawLine.getDouble("width")
    } else {
      5.0
    }
  private val icons = options.getMap("icons")
  val selectionIconColor: Int =
    icons?.getString("unselectedColor")?.let { Color.parseColor(it) } ?: Color.WHITE
  val undoRedoIconColor: Int =
    icons?.getString("undoRedoColor")?.let { Color.parseColor(it) } ?: Color.WHITE
}
