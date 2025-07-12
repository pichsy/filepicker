package com.pichs.filepicker.demo.newpicker.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.pichs.filepicker.demo.newpicker.data.LocalMediaPagingSource
import com.pichs.filepicker.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

/**
 * 本地媒体数据仓库
 */
class LocalMediaRepository {

    private var currentPagingSource: LocalMediaPagingSource? = null

    /**
     * 获取本地媒体分页数据流
     */
    fun getLocalMediaStream(
        selectType: String,
        context: Context,
        onTotalCountChanged: ((Int) -> Unit)? = null
    ): Flow<PagingData<MediaEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 100,                 // 每页50个媒体文件，平衡性能和用户体验
                enablePlaceholders = false,   // 禁用占位符，避免闪烁
                initialLoadSize = 100,         // 初始加载50个
                prefetchDistance = 50,        // 提前20个位置开始预加载
                maxSize = PagingConfig.MAX_SIZE_UNBOUNDED                 // 最大缓存300个item，避免内存过大
            ),
            pagingSourceFactory = {
                LocalMediaPagingSource(
                    selectType = selectType,
                    context = context,
                    pageSize = 100,
                    onTotalCountChanged = onTotalCountChanged
                ).also {
                    currentPagingSource = it
                }
            }
        ).flow
    }

    /**
     * 清除缓存，用于刷新数据
     */
    fun clearCache() {
        currentPagingSource?.clearCache()
    }
}
