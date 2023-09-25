package com.ornament.pdfeditor.bridge

import android.graphics.Color
import com.facebook.react.bridge.ReadableMap

class PDFEditorOptions(options: ReadableMap) {
    val fileName = options.getString("fileName")
    val contentType = ContentType.parseType(options.getString("canvasType")) ?: ContentType.PDF
    val isToolBarHidden = options.getBoolean("isToolBarHidden")
    val backgroundColor = Color.parseColor(options.getString("viewBackgroundColor"))
    val lineColor = Color.parseColor(options.getString("lineColor"))
    val lineWidth = options.getDouble("lineWidth")
    val startWithEdit = options.getBoolean("startWithEdit")
    enum class ContentType {
        PDF, IMAGE;

        companion object {
            fun parseType(value: String?) = when (value) {
                "image" -> IMAGE
                "pdf" -> PDF
                else -> null
            }
        }
    }

}
