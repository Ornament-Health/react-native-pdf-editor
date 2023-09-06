package com.ornament.pdfeditor

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout
import com.ornament.pdfeditor.databinding.ViewPdfEditorBinding
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

class PDFEditorView(context: Context) : ConstraintLayout(context) {

    companion object {
        private const val ACTION_TAG = "ACTION"

    }

    private val binding: ViewPdfEditorBinding

    private lateinit var options: PDFEditorOptions
    private lateinit var pageAdapter: PDFPageAdapter

    init {
        PDFBoxResourceLoader.init(context)
        binding = ViewPdfEditorBinding.inflate(LayoutInflater.from(context), this, true)
    }

    fun setOptions(options: PDFEditorOptions) {
        this.options = options
        background = ColorDrawable(options.backgroundColor)
        options.fileName?.let {
            loadPDF(it)
        }
        binding.textViewInfrotmation.text = options.toString()
    }

    private var onSavePDFAction: (filePath: String?) -> Unit = {}

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

    private fun loadPDF(filePath: String) {
        val document = PDDocument.load(File(filePath))
        binding.recyclerViewPages.apply {
            pageAdapter = PDFPageAdapter(document)
            adapter = pageAdapter
            setListener(pageAdapter)
        }
    }
    override fun onInterceptTouchEvent(ev: MotionEvent?) = false

}
