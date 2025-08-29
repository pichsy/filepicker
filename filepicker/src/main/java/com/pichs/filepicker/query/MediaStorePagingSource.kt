package com.pichs.filepicker.query

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.pichs.filepicker.FilePickerSelectType
import com.pichs.filepicker.entity.MediaEntity

/**
 * 简化版 PagingSource：首轮使用现有 FileQueryHelper 全量查询并缓存，再按页切片返回。
 * 后续可优化为基于 MediaStore 的增量分页（Q+ 使用 QUERY_ARG_LIMIT 等参数）。
 */
class MediaStorePagingSource(
    private val context: Context,
    private val selectType: String,
    private val minSize: Long,
    private val maxSize: Long,
) : PagingSource<Int, MediaEntity>() {

    // 首次查询后的缓存，避免多次全量查询
    private var cached: MutableList<MediaEntity>? = null

    override fun getRefreshKey(state: PagingState<Int, MediaEntity>): Int? {
        return state.anchorPosition?.let { anchorPos ->
            val page = state.closestPageToPosition(anchorPos)
            page?.prevKey?.plus(state.config.pageSize) ?: page?.nextKey?.minus(state.config.pageSize)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaEntity> {
        return try {
            val start = params.key ?: 0
            val pageSize = params.loadSize

            val dataSource = ensureCache()

            val endExclusive = (start + pageSize).coerceAtMost(dataSource.size)
            val sub = if (start < endExclusive) dataSource.subList(start, endExclusive) else emptyList()

            val prevKey = if (start == 0) null else (start - pageSize).coerceAtLeast(0)
            val nextKey = if (endExclusive >= dataSource.size) null else endExclusive

            LoadResult.Page(
                data = sub,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    private suspend fun ensureCache(): MutableList<MediaEntity> {
        val cachedLocal = cached
        if (cachedLocal != null) return cachedLocal

        val queryTypes = when (selectType) {
            FilePickerSelectType.IMAGE_VIDEO -> mutableSetOf(QueryType.IMAGE, QueryType.VIDEO)
            FilePickerSelectType.IMAGE -> mutableSetOf(QueryType.IMAGE)
            FilePickerSelectType.VIDEO -> mutableSetOf(QueryType.VIDEO)
            FilePickerSelectType.IMAGE_VIDEO_GIF -> mutableSetOf(QueryType.IMAGE, QueryType.VIDEO, QueryType.GIF)
            FilePickerSelectType.GIF -> mutableSetOf(QueryType.GIF)
            FilePickerSelectType.AUDIO -> mutableSetOf(QueryType.AUDIO)
            else -> mutableSetOf(QueryType.NONE)
        }

        val result = FileQueryHelper.queryAlbums(
            context = context,
            queryTypes = queryTypes,
            minSize = minSize,
            maxSize = maxSize,
            queryBuilder = { /* 其他过滤在原有逻辑中处理 */ },
            fastNumber = 60,
            onFastCallBack = { /* 忽略快速回调 */ }
        )

        // 汇总为单列表，按 addTime 倒序
        val list = result.mediaFolders.flatMap { it.mediaEntityList }.sortedByDescending { it.addTime ?: 0L }.toMutableList()
        cached = list
        return list
    }
}


