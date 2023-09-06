package com.ornament.pdfeditor

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.ornament.pdfeditor.databinding.ItemPdfPageBinding
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer

class PDFPageAdapter(
    private val pdfDocument: PDDocument
) : RecyclerView.Adapter<PDFPageAdapter.PDFPageViewHolder>(), PDFRecyclerView.Listener {
    private val viewHolders: MutableList<PDFPageViewHolder> = mutableListOf()

    inner class PDFPageViewHolder(private val binding: ItemPdfPageBinding) :
        ViewHolder(binding.root) {
        private var pageNumber: Int = 0
        fun setPageNumber(pageNumber: Int) {
            this.pageNumber = pageNumber
        }

        fun renderPage() {
            binding.pdfPage.setImageBitmap(PDFRenderer(pdfDocument).renderImage(pageNumber, scale, ImageType.RGB))
        }
    }

    private var scale: Float = 1f
        set(value) {
            field = value.coerceAtLeast(0f).coerceAtMost(5f)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PDFPageViewHolder(
        ItemPdfPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    ).also {
        viewHolders.add(it)
    }

    override fun onViewRecycled(holder: PDFPageViewHolder) {
        viewHolders.remove(holder)
        super.onViewRecycled(holder)
    }

    override fun getItemCount() = pdfDocument.pages.count

    override fun onBindViewHolder(holder: PDFPageViewHolder, position: Int) {
        holder.setPageNumber(position)
        holder.renderPage()
        Log.i("PDF_DOCUMENT", "Render ${position}th page")
    }

    init {
        Log.i("PDF_DOCUMENT", "Count of pages: ${pdfDocument.pages.count}")
    }

    override fun onScaleChanged(newScale: Float) {
        scale = newScale
        updatePages()
    }

    fun updatePages() = viewHolders.forEach { it.renderPage() }
}