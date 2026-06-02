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
    private var attachedRecyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (attachedRecyclerView === recyclerView) {
            attachedRecyclerView = null
        }
    }

    fun submit(newItems: List<DocumentPreviewItem>, selectedIndex: Int) {
        val oldSize = items.size
        val newSize = newItems.size
        items.clear()
        items.addAll(newItems)
        this.selectedIndex = selectedIndex
        // Use targeted notifications. Under Fabric, plain notifyDataSetChanged
        // sometimes does not trigger a fresh layout pass when the host's
        // parent already considers its size fixed, leaving newly-appended
        // items un-bound. Range-specific notifications force the RecyclerView
        // to insert/remove children, which is honored even in that case.
        when {
            oldSize == 0 && newSize > 0 -> notifyItemRangeInserted(0, newSize)
            oldSize > 0 && newSize == 0 -> notifyItemRangeRemoved(0, oldSize)
            newSize > oldSize -> {
                if (oldSize > 0) notifyItemRangeChanged(0, oldSize)
                notifyItemRangeInserted(oldSize, newSize - oldSize)
            }
            newSize < oldSize -> {
                if (newSize > 0) notifyItemRangeChanged(0, newSize)
                notifyItemRangeRemoved(newSize, oldSize - newSize)
            }
            else -> notifyItemRangeChanged(0, newSize)
        }
    }

    fun updateSelection(newIndex: Int) {
        if (newIndex == selectedIndex) return
        val previousIndex = selectedIndex
        selectedIndex = newIndex
        // Rebind the two affected view holders directly. notifyItemChanged
        // alone is unreliable here: under Fabric the host's parent often
        // considers its size fixed and the rebind layout pass never lands,
        // so the selection-scale stays stuck on whatever was bound at initial
        // render. See prepareDocumentPreviews() for the matching workaround
        // on the insert path.
        rebindSelectionAt(previousIndex)
        rebindSelectionAt(newIndex)
    }

    private fun rebindSelectionAt(position: Int) {
        if (position !in items.indices) return
        val rv = attachedRecyclerView
        val holder = rv?.findViewHolderForAdapterPosition(position) as? ViewHolder
        if (holder != null) {
            holder.bind(items[position], position == selectedIndex)
        } else {
            // Off-screen or not yet attached — let the standard bind cycle
            // pick up the new selectedIndex when the view scrolls in.
            notifyItemChanged(position)
        }
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
            // Only the current item stays bright; the rest are partially faded.
            binding.previewContent.alpha = if (isSelected) 1f else 0.8f
        }

        fun clear() {
            binding.thumbnail.setImageBitmap(null)
            binding.backSheetFar.visibility = View.GONE
            binding.backSheetNear.visibility = View.GONE
            binding.frontSheet.translationX = 0f
            binding.frontSheet.translationY = 0f
            binding.previewContent.scaleX = 1f
            binding.previewContent.scaleY = 1f
            binding.previewContent.alpha = 1f
        }
    }
}
