package com.ornament.pdfeditor

import android.graphics.Color
import com.facebook.react.bridge.ReadableMap

class PDFEditorOptions(options: ReadableMap) {
    val fileName = options.getString("fileName")
    val isToolBarHidden = options.getBoolean("isToolBarHidden")
    val backgroundColor = Color.parseColor(options.getString("viewBackgroundColor"))
    val lineColor = Color.parseColor(options.getString("lineColor"))
    val lineWidth = options.getDouble("lineWidth")
    val startWithEdit = options.getBoolean("startWithEdit")

    override fun toString() = "Options:\n" +
            "fileName: " + fileName + "\n" +
            "isToolBarHidden: " + isToolBarHidden + "\n" +
            "backgroundColor: " + backgroundColor + "\n" +
            "lineColor: " + lineColor + "\n" +
            "lineWidth: " + lineWidth + "\n" +
            "startWithEdit: " + startWithEdit

}
