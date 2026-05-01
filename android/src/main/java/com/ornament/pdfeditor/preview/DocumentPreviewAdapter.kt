package com.ornament.pdfeditor.preview

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ornament.pdfeditor.R
import com.ornament.pdfeditor.databinding.ItemDocumentPreviewBinding

data class DocumentPreviewItem(
    val index: Int,
    val thumbnail: Bitmap?,
    val isMultiPage: Boolean
)

class DocumentPreviewAdapter(
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<DocumentPreviewAdapter.ViewHolder>() {

    private val items = mutableListOf<DocumentPreviewItem>()
    private var selectedIndex: Int = 0

    fun submit(newItems: List<DocumentPreviewItem>, selectedIndex: Int) {
        items.clear()
        items.addAll(newItems)
        this.selectedIndex = selectedIndex
        notifyDataSetChanged()
    }

    fun updateSelection(newIndex: Int) {
        if (newIndex == selectedIndex) return
        val previousIndex = selectedIndex
        selectedIndex = newIndex
        if (previousIndex in items.indices) notifyItemChanged(previousIndex)
        if (newIndex in items.indices) notifyItemChanged(newIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDocumentPreviewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position == selectedIndex)
    }

    override fun getItemCount(): Int = items.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.clear()
    }

    inner class ViewHolder(
        private val binding: ItemDocumentPreviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                items.getOrNull(adapterPosition)?.let { item ->
                    onItemClick(item.index)
                }
            }
        }

        fun bind(item: DocumentPreviewItem, isSelected: Boolean) {
            binding.thumbnail.setImageBitmap(item.thumbnail)
            val stackVisibility = if (item.isMultiPage) View.VISIBLE else View.GONE
            binding.backSheetFar.visibility = stackVisibility
            binding.backSheetNear.visibility = stackVisibility
            val frontTranslation = if (item.isMultiPage) {
                -binding.root.resources.getDimension(R.dimen.pdfeditor_preview_stack_offset)
            } else {
                0f
            }
            binding.frontSheet.translationX = frontTranslation
            binding.frontSheet.translationY = frontTranslation
            val scale = if (isSelected) 1f else 0.88f
            binding.previewContent.scaleX = scale
            binding.previewContent.scaleY = scale
        }

        fun clear() {
            binding.thumbnail.setImageBitmap(null)
            binding.backSheetFar.visibility = View.GONE
            binding.backSheetNear.visibility = View.GONE
            binding.frontSheet.translationX = 0f
            binding.frontSheet.translationY = 0f
            binding.previewContent.scaleX = 1f
            binding.previewContent.scaleY = 1f
        }
    }
}
