package com.pichs.filepicker.demo.newpicker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pichs.filepicker.demo.R
import com.pichs.filepicker.demo.databinding.ItemLocalMediaBinding
import com.pichs.filepicker.demo.newpicker.viewmodel.LocalMediaViewModel
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.loader.MediaLoader
import com.pichs.filepicker.utils.FilePickerTimeFormatUtils

/**
 * 本地媒体分页适配器
 */
class LocalMediaPagingAdapter(
    private val viewModel: LocalMediaViewModel,
    private val onItemClick: (MediaEntity) -> Unit = {},
    private val onItemSelect: (MediaEntity) -> Unit = {},
    private val onItemPreview: (MediaEntity) -> Unit = {}
) : PagingDataAdapter<MediaEntity, LocalMediaPagingAdapter.MediaViewHolder>(MEDIA_COMPARATOR) {

    companion object {
        private val MEDIA_COMPARATOR = object : DiffUtil.ItemCallback<MediaEntity>() {
            override fun areItemsTheSame(oldItem: MediaEntity, newItem: MediaEntity): Boolean {
                return oldItem.path == newItem.path
            }

            override fun areContentsTheSame(oldItem: MediaEntity, newItem: MediaEntity): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemLocalMediaBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return MediaViewHolder(binding, viewModel, onItemClick, onItemSelect, onItemPreview)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val mediaEntity = getItem(position)
        if (mediaEntity != null) {
            holder.bind(mediaEntity)
        }
    }

    class MediaViewHolder(
        private val binding: ItemLocalMediaBinding,
        private val viewModel: LocalMediaViewModel,
        private val onItemClick: (MediaEntity) -> Unit,
        private val onItemSelect: (MediaEntity) -> Unit,
        private val onItemPreview: (MediaEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(mediaEntity: MediaEntity) {
            // 加载媒体缩略图
            MediaLoader.loadImage(mediaEntity.uri, mediaEntity.mimeType, binding.ivCoverImage)
            
            // 设置时长（仅视频显示）
            if (mediaEntity.isVideo() && mediaEntity.duration > 0) {
                binding.tvDuration.isVisible = true
                binding.tvDuration.text = FilePickerTimeFormatUtils.formatTimeMillSeconds(mediaEntity.duration)
            } else {
                binding.tvDuration.isVisible = false
            }
            
            // 设置选中状态
            updateSelectState(mediaEntity)
            
            // 设置点击事件
            binding.root.setOnClickListener {
                onItemClick(mediaEntity)
            }
            
            // 设置选择区域点击事件
            binding.clSelectArea.setOnClickListener {
                handleSelectClick(mediaEntity)
            }
            
            // 设置预览点击事件
            binding.ivPreviewImage.setOnClickListener {
                onItemPreview(mediaEntity)
            }
        }
        
        private fun updateSelectState(mediaEntity: MediaEntity) {
            val isSelected = viewModel.isMediaSelected(mediaEntity)
            val selectedIndex = viewModel.getMediaSelectedIndex(mediaEntity)
            
            if (isSelected && selectedIndex > 0) {
                binding.tvSelectIndex.isVisible = true
                binding.tvSelectIndex.text = selectedIndex.toString()
                binding.tvSelectIndex.setBackgroundColor(
                    binding.root.context.getColor(R.color.filepicker_select_bg_color)
                )
            } else {
                binding.tvSelectIndex.isVisible = false
                binding.tvSelectIndex.setBackgroundColor(
                    binding.root.context.getColor(android.R.color.transparent)
                )
            }
        }
        
        private fun handleSelectClick(mediaEntity: MediaEntity) {
            val isSelected = viewModel.isMediaSelected(mediaEntity)
            
            if (isSelected) {
                // 取消选择
                viewModel.removeSelectedMedia(mediaEntity)
                updateSelectState(mediaEntity)
            } else {
                // 添加选择
                val success = viewModel.addSelectedMedia(mediaEntity)
                if (success) {
                    updateSelectState(mediaEntity)
                    onItemSelect(mediaEntity)
                } else {
                    // 选择失败（可能超过最大数量）
                    // 可以在这里显示提示
                }
            }
        }
    }
}
