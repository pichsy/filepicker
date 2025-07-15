package com.pichs.filepicker.demo.paging.data

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.pichs.filepicker.demo.paging.model.User
import kotlinx.coroutines.delay

/**
 * 用户数据分页源
 */
class UserPagingSource : PagingSource<Int, User>() {

    companion object {
        private const val STARTING_PAGE_INDEX = 1
        private const val PAGE_SIZE = 20
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, User> {
        return try {
            Log.d("UserPagingSource", "Loading page: ${params.key ?: STARTING_PAGE_INDEX}, Load size: ${params.loadSize}")
            val page = params.key ?: STARTING_PAGE_INDEX

            // 模拟网络延迟
            delay(1000)

            // 生成假数据
            val users = generateFakeUsers(page, PAGE_SIZE)

            // 模拟总共有 200 条数据
            val totalCount = 200
            val hasNextPage = (page * PAGE_SIZE) < totalCount

            LoadResult.Page(
                data = users,
                prevKey = if (page == STARTING_PAGE_INDEX) null else page - 1,
                nextKey = if (hasNextPage) page + 1 else null
            )
        } catch (exception: Exception) {
            LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, User>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    /**
     * 生成假数据
     */
    private fun generateFakeUsers(page: Int, pageSize: Int): List<User> {
        val startIndex = (page - 1) * pageSize
        val cities = listOf("北京", "上海", "广州", "深圳", "杭州", "南京", "成都", "武汉", "西安", "重庆")
        val avatars = listOf(
            "https://picsum.photos/100/100?random=1",
            "https://picsum.photos/100/100?random=2",
            "https://picsum.photos/100/100?random=3",
            "https://picsum.photos/100/100?random=4",
            "https://picsum.photos/100/100?random=5"
        )

        return (1..pageSize).map { index ->
            val userId = startIndex + index
            User(
                id = userId,
                name = "用户$userId",
                email = "user$userId@example.com",
                avatar = avatars[userId % avatars.size],
                age = 18 + (userId % 50),
                city = cities[userId % cities.size]
            )
        }
    }
}
