package com.pichs.filepicker.widget

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.pichs.filepicker.FilePickerPagingViewModel
import com.pichs.filepicker.FilePickerViewModel
import java.util.Collections

class OnFilePickerPagingDragItemTouchHelperCallback(
    private val adapter: RecyclerView.Adapter<*>,
    private val viewModel: FilePickerPagingViewModel,
    val onDragEnd: () -> Unit
) : ItemTouchHelper.Callback() {

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ): Int {
        return makeMovementFlags(ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = viewHolder.absoluteAdapterPosition
        val to = target.absoluteAdapterPosition
        val list = viewModel.getSelectedDataList()
        Collections.swap(list, from, to)
        adapter.notifyItemMoved(from, to)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        onDragEnd()
    }

    override fun isLongPressDragEnabled() = true
}