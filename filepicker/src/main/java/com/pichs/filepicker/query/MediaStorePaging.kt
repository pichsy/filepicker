package com.pichs.filepicker.query

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.pichs.filepicker.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

object MediaStorePaging {

    fun pager(
        context: Context,
        selectType: String,
        minSize: Long,
        maxSize: Long,
        pageSize: Int = 60,
        initialLoadSize: Int = 120,
        prefetchDistance: Int = pageSize
    ): Flow<PagingData<MediaEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = initialLoadSize,
                prefetchDistance = prefetchDistance,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                MediaStorePagingSource(context, selectType, minSize, maxSize)
            }
        ).flow
    }
}


