package com.pichs.filepicker.demo.paging.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.pichs.filepicker.demo.paging.data.UserPagingSource
import com.pichs.filepicker.demo.paging.model.User
import kotlinx.coroutines.flow.Flow

/**
 * 用户数据仓库
 */
class UserRepository {

    fun getUserStream(): Flow<PagingData<User>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
            pagingSourceFactory = { UserPagingSource() }
        ).flow
    }
}
