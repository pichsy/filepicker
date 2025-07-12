package com.pichs.filepicker.demo.paging.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.pichs.filepicker.demo.R
import com.pichs.filepicker.demo.paging.model.ImageItem

/**
 * 图片列表分页适配器
 */
class ImagePagingAdapter(
    private val onItemClick: (ImageItem) -> Unit = {}
) : PagingDataAdapter<ImageItem, ImagePagingAdapter.ImageViewHolder>(IMAGE_COMPARATOR) {

    companion object {
        private val IMAGE_COMPARATOR = object : DiffUtil.ItemCallback<ImageItem>() {
            override fun areItemsTheSame(oldItem: ImageItem, newItem: ImageItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ImageItem, newItem: ImageItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_grid, parent, false)
        return ImageViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageItem = getItem(position)
        if (imageItem != null) {
            holder.bind(imageItem)
        }
    }

    class ImageViewHolder(
        itemView: View,
        private val onItemClick: (ImageItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val imageView: ImageView = itemView.findViewById(R.id.iv_image)
        private val titleTextView: TextView = itemView.findViewById(R.id.tv_title)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.tv_description)

        fun bind(imageItem: ImageItem) {
            titleTextView.text = imageItem.title
            descriptionTextView.text = imageItem.description
            
            // 使用 Glide 加载缩略图，优化快速滑动性能
            Glide.with(itemView.context)
                .load(imageItem.imageUrl)  // 使用缩略图URL
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background)
                        .dontTransform()     // 不做变换，提升性能
                        .dontAnimate()       // 不做动画，提升性能
                        .override(150, 150)  // 限制图片尺寸
                        .skipMemoryCache(false)  // 启用内存缓存
                        .diskCacheStrategy(DiskCacheStrategy.ALL)  // 缓存所有版本
                )
                .into(imageView)
            
            // 设置点击事件
            itemView.setOnClickListener {
                onItemClick(imageItem)
            }
        }
    }
}
