package com.pichs.filepicker.paging

import android.content.Context
import android.os.Build.VERSION_CODES.Q
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.query.FileQueryHelper
import com.pichs.filepicker.query.QueryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.math.min

class FilePickerPagingSource(val context: Context, val onTotalCountChanged: ((Int) -> Unit)? = null) : PagingSource<Int, MediaEntity>() {

    private val STARTING_PAGE_INDEX = 0
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
        val page = params.key ?: 0
        return try {
            val result = FileQueryHelper.queryAlbums(context, mutableSetOf(QueryType.IMAGE))
            val allList = result.mediaFolders.flatMap { it.mediaEntityList }
            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, allList.size)
            val pageData = if (startIndex < allList.size) allList.subList(startIndex, endIndex) else emptyList()
            LoadResult.Page(
                data = pageData,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (endIndex >= allList.size) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private suspend fun loadFirstBatch(): MutableList<MediaEntity> = withContext(Dispatchers.IO) {
        val result = FileQueryHelper.queryAlbums(
            context, mutableSetOf(
                QueryType.IMAGE
            )
        )
        // 这里可以根据需要调整加载的数量
        val allList = result.mediaFolders.flatMap { it.mediaEntityList }
        val fl = allList.subList(0, min(300, allList.size)).toMutableList()
        return@withContext fl
    }


    private suspend fun loadRemainingDataInBackground(): MutableList<MediaEntity> = withContext(Dispatchers.IO) {
        val result = FileQueryHelper.queryAlbums(
            context, mutableSetOf(
                QueryType.IMAGE
            )
        )
        // 这里可以根据需要调整加载的数量
        val allList = result.mediaFolders.flatMap { it.mediaEntityList }
        val fl = allList.subList(0, min(300, allList.size)).toMutableList()
        return@withContext fl
    }


    private suspend fun waitForAllDataLoaded(): List<MediaEntity> = withContext(Dispatchers.IO) {
        while (allMediaList == null) {
            // 等待全量数据加载完成
            kotlinx.coroutines.delay(100)
        }
        return@withContext allMediaList ?: emptyList()
    }


    private suspend fun loadAllMediaFromDatabase(): List<MediaEntity> = withContext(Dispatchers.IO) {
        val result = FileQueryHelper.queryAlbums(
            context, mutableSetOf(
                QueryType.IMAGE
            )
        )
        // 这里可以根据需要调整加载的数量
        val allList = result.mediaFolders.flatMap { it.mediaEntityList }
        val fl = allList.subList(0, min(300, allList.size)).toMutableList()
        return@withContext fl
    }


    fun clearCache() {
        // 清除缓存逻辑
        // 这里可以实现清除缓存的具体操作
    }
}

class FilePickerPagingRepository {
    var pagingSource: FilePickerPagingSource? = null

    fun loadData(context: Context, onTotalCountChanged: ((Int) -> Unit)? = null): Flow<PagingData<MediaEntity>> {
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
                FilePickerPagingSource(context, onTotalCountChanged).apply {
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