package com.ornament.pdfeditor.bridge

import android.graphics.Color
import com.facebook.react.bridge.ReadableMap

class PDFEditorOptions(options: ReadableMap) {
    val filePaths = options.getArray("filePath")?.toArrayList()?.map { it as String }
    val lineColor: Int = Color.parseColor(options.getString("lineColor"))
    val lineWidth: Double = options.getDouble("lineWidth")
    val selectionIconColor: Int = options.getString("selectionIconColor")?.let { Color.parseColor(it) } ?: Color.WHITE
}
