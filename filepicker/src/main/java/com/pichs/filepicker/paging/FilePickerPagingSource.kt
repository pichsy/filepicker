package com.pichs.filepicker.paging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.pichs.filepicker.entity.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FilePickerPagingSource(val onTotalCountChanged: ((Int) -> Unit)? = null) : PagingSource<Int, MediaEntity>() {

    private val STARTING_PAGE_INDEX = 0
    private val FIRST_BATCH_SIZE = 300  // 首批快速加载300条
    private val pageSize: Int = 100

    // 缓存全部数据，确保分页过程中数据稳定
    private var allMediaList: List<MediaEntity>? = null

    // 首批数据加载状态
    private var firstBatchLoaded = false

    // 全量数据加载状态
    private var isLoadingAll = false

    override fun getRefreshKey(state: PagingState<Int, MediaEntity>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaEntity> {
        return try {
            val page = params.key ?: STARTING_PAGE_INDEX

            // 处理首次加载：快速显示前300条数据
            if (allMediaList == null) {
                if (page == 0 && !firstBatchLoaded) {
                    // 首次加载：快速加载前300条数据
                    val firstBatch = loadFirstBatch()
                    firstBatchLoaded = true

                    // 先显示首批数量
                    onTotalCountChanged?.invoke(firstBatch.size)

                    // 启动后台加载全量数据
                    if (!isLoadingAll) {
                        isLoadingAll = true
                        loadRemainingDataInBackground()
                    }

                    return LoadResult.Page(
                        data = firstBatch,
                        prevKey = null,
                        nextKey = if (firstBatch.size >= pageSize) 1 else null
                    )
                } else {
                    // 等待全量数据加载完成
                    waitForAllDataLoaded()
                }
            }

            // 刷新时重新加载全部数据
            if (params is LoadParams.Refresh) {
                allMediaList = loadAllMediaFromDatabase()
            }

            // 正常分页逻辑
            val totalList = allMediaList ?: emptyList()
            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, totalList.size)

            val pageData = if (startIndex < totalList.size) {
                totalList.subList(startIndex, endIndex)
            } else {
                emptyList()
            }

            LoadResult.Page(
                data = pageData,
                prevKey = if (page == STARTING_PAGE_INDEX) null else page - 1,
                nextKey = if (endIndex >= totalList.size) null else page + 1
            )
        } catch (exception: Exception) {
            LoadResult.Error(exception)
        }
    }

    private suspend fun loadFirstBatch(): MutableList<MediaEntity> = withContext(Dispatchers.IO){

        return@withContext mutableListOf()
    }



    private suspend fun loadRemainingDataInBackground(): MutableList<MediaEntity> = withContext(Dispatchers.IO){

        return@withContext mutableListOf()
    }


    private suspend fun waitForAllDataLoaded(): List<MediaEntity> = withContext(Dispatchers.IO) {
        while (allMediaList == null) {
            // 等待全量数据加载完成
            kotlinx.coroutines.delay(100)
        }
        return@withContext allMediaList ?: emptyList()
    }


    private suspend fun loadAllMediaFromDatabase(): List<MediaEntity> = withContext(Dispatchers.IO) {
        // 这里应该实现实际的数据加载逻辑
        // 比如从数据库或文件系统加载所有媒体数据
        return@withContext emptyList()  // 返回空列表作为示例
    }


    fun clearCache() {
        // 清除缓存逻辑
        // 这里可以实现清除缓存的具体操作
    }
}

class FilePickerPagingRepository {
    var pagingSource: FilePickerPagingSource? = null

    fun loadData(onTotalCountChanged: ((Int) -> Unit)? = null): Flow<PagingData<MediaEntity>> {
        // 这里应该实现实际的数据加载逻辑
        return Pager(
            config = PagingConfig(
                pageSize = 100,                 // 每页100个媒体文件，平衡性能和用户体验
                enablePlaceholders = false,   // 禁用占位符，避免闪烁
                initialLoadSize = 100,         // 初始加载100个
                prefetchDistance = 50,        // 提前50个位置开始预加载
                maxSize = PagingConfig.MAX_SIZE_UNBOUNDED  // 最大缓存不限制
            ),
            pagingSourceFactory = {
                FilePickerPagingSource(onTotalCountChanged).apply {
                    pagingSource = this
                }
            }
        ).flow
    }

    fun clearCache() {
        // 清除缓存逻辑
        // 这里可以实现清除缓存的具体操作
        pagingSource?.clearCache()
    }


}