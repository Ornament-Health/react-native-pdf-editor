package com.ornament.pdfeditor

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.Log
import androidx.appcompat.widget.AppCompatTextView

class PDFEditorView(context: Context) : AppCompatTextView(context) {

    private lateinit var options: PDFEditorOptions
    fun setOptions(options: PDFEditorOptions) {
        this.options = options
        background = ColorDrawable(options.backgroundColor)
        text = text.toString() + options.toString()

    }

    private var onSavePDFAction : (filePath: String?) -> Unit = {}

    fun onSavePDF(action: (filePath: String?) -> Unit) {
        onSavePDFAction = action
    }

    private fun savePDF() {
        //perform saving pdf
        onSavePDFAction("path to new pdf")
    }

    fun setScrollMode() {
        Log.i(ACTION_TAG, "SCROLL")
    }

    fun setDrawMode() {
        Log.i(ACTION_TAG, "DRAW")
    }

    fun undo() {
        Log.i(ACTION_TAG, "UNDO")
    }

    fun save() {
        Log.i(ACTION_TAG, "REDO")
    }


    init {
        setOnClickListener {
            savePDF()
        }
    }

    companion object {
        private const val ACTION_TAG = "ACTION"
    }

}
