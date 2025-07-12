package com.pichs.filepicker.demo.paging.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.pichs.filepicker.demo.paging.model.ImageItem
import kotlinx.coroutines.delay

/**
 * 图片分页数据源 - 无上限加载
 */
class ImagePagingSource : PagingSource<Int, ImageItem>() {

    companion object {
        private const val STARTING_PAGE_INDEX = 1
        private const val PAGE_SIZE = 100
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ImageItem> {
        return try {
            val page = params.key ?: STARTING_PAGE_INDEX
            
            // 模拟网络延迟 - 减少延迟提升快速滑动体验
            delay(200)  // 从800ms减少到200ms
            
            // 生成假数据
            val images = generateFakeImages(page, PAGE_SIZE)
            
            // 无上限加载，总是有下一页
            LoadResult.Page(
                data = images,
                prevKey = if (page == STARTING_PAGE_INDEX) null else page - 1,
                nextKey = page + 1  // 总是有下一页
            )
        } catch (exception: Exception) {
            LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ImageItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    /**
     * 生成假的图片数据
     */
    private fun generateFakeImages(page: Int, pageSize: Int): List<ImageItem> {
        val startIndex = (page - 1) * pageSize
        val categories = listOf("nature", "city", "food", "animals", "technology", "art", "people", "travel")
        val adjectives = listOf("美丽的", "壮观的", "精致的", "迷人的", "神秘的", "优雅的", "震撼的", "温馨的")
        val nouns = listOf("风景", "建筑", "美食", "动物", "科技", "艺术", "人物", "旅行")
        
        return (1..pageSize).map { index ->
            val imageId = startIndex + index
            val category = categories[imageId % categories.size]
            val adjective = adjectives[imageId % adjectives.size]
            val noun = nouns[imageId % nouns.size]
            
            // 使用不同的图片服务来获得更多样化的图片
            val imageUrl = "http://gips0.baidu.com/it/u=3560029307,576412274&fm=3028&app=3028&f=JPEG&fmt=auto?w=960&h=1280"
            
            val thumbnailUrl = "http://gips0.baidu.com/it/u=3560029307,576412274&fm=3028&app=3028&f=JPEG&fmt=auto?w=960&h=1280"
            
            ImageItem(
                id = imageId,
                title = "${adjective}${noun} #$imageId",
                imageUrl = imageUrl,
                thumbnailUrl = thumbnailUrl,
                description = "这是第 $imageId 张图片的描述，展示了${adjective}${noun}的魅力。",
                tags = listOf(category, adjective, noun),
                width = 150,
                height = 120
            )
        }
    }
}
