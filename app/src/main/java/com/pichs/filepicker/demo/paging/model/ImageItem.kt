package com.pichs.filepicker.demo.paging.model

/**
 * 图片数据模型
 */
data class ImageItem(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val thumbnailUrl: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val width: Int = 300,
    val height: Int = 200
)
