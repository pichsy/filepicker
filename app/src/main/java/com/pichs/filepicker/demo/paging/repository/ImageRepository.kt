package com.pichs.filepicker.demo.paging.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.pichs.filepicker.demo.paging.data.ImagePagingSource
import com.pichs.filepicker.demo.paging.model.ImageItem
import kotlinx.coroutines.flow.Flow

/**
 * 图片数据仓库
 */
class ImageRepository {

    fun getImageStream(): Flow<PagingData<ImageItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = 100,                // 每页100张图片
                enablePlaceholders = false,
                initialLoadSize = 100,         // 初始加载100张
                prefetchDistance = 50,         // 提前50个位置开始预加载（快速滑动关键）
                maxSize = 800                  // 最大缓存800个item，避免内存过大
                // 移除 jumpThreshold，因为我们的 PagingSource 不支持跳跃
            ),
            pagingSourceFactory = { ImagePagingSource() }
        ).flow
    }
}
