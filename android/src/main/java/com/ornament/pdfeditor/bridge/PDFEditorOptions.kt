package com.ornament.pdfeditor.bridge

import android.graphics.Color
import com.facebook.react.bridge.ReadableMap

class PDFEditorOptions(options: ReadableMap) {
    val filePaths = options.getArray("filePath")?.toArrayList()?.map { it as String }
    val isToolBarHidden = options.getBoolean("isToolBarHidden")
    val backgroundColor = Color.parseColor(options.getString("viewBackgroundColor"))
    val lineColor = Color.parseColor(options.getString("lineColor"))
    val lineWidth = options.getDouble("lineWidth")
}
