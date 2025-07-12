package com.pichs.filepicker.demo.paging

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.pichs.filepicker.demo.R
import com.pichs.filepicker.demo.paging.adapter.ImagePagingAdapter
import com.pichs.filepicker.demo.paging.model.ImageItem
import com.pichs.filepicker.demo.paging.viewmodel.ImageViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 图片分页加载演示页面 - 无上限加载
 */
class ImagePagingDemoActivity : AppCompatActivity() {

    private val viewModel: ImageViewModel by viewModels()
    private lateinit var adapter: ImagePagingAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_paging_demo)
        
        initViews()
        setupRecyclerView()
        observeData()
    }

    private fun initViews() {
        recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipe_refresh_layout)
        
        // 设置下拉刷新
        swipeRefreshLayout.setOnRefreshListener {
            adapter.refresh()
        }
    }

    private fun setupRecyclerView() {
        adapter = ImagePagingAdapter { imageItem ->
            onImageClick(imageItem)
        }
        
        // 使用网格布局，4列
        val gridLayoutManager = GridLayoutManager(this, 4)
        recyclerView.layoutManager = gridLayoutManager

        // 优化 RecyclerView 性能
        recyclerView.apply {
            // 设置固定大小，避免重复测量
            setHasFixedSize(true)
            // 启用嵌套滑动
            isNestedScrollingEnabled = true
            // 设置缓存大小
            setItemViewCacheSize(20)  // 缓存20个ViewHolder
            // 设置回收池大小
            recycledViewPool.setMaxRecycledViews(0, 50)  // 为type 0设置50个回收池
        }

        // 不使用 withLoadStateFooter，直接设置适配器
        recyclerView.adapter = adapter
        
        // 监听加载状态，但不显示底部加载器
        adapter.addLoadStateListener { loadState ->
            // 显示/隐藏下拉刷新
            swipeRefreshLayout.isRefreshing = loadState.refresh is LoadState.Loading
            
            // 处理错误状态（可以显示 Toast 或其他提示）
            if (loadState.refresh is LoadState.Error) {
                val error = loadState.refresh as LoadState.Error
                Toast.makeText(this, "加载失败: ${error.error.message}", Toast.LENGTH_SHORT).show()
            }
            
            // 可以在这里处理追加加载的错误，但不显示底部加载器
            if (loadState.append is LoadState.Error) {
                val error = loadState.append as LoadState.Error
                Toast.makeText(this, "加载更多失败: ${error.error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.imagePagingData.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }
    }

    private fun onImageClick(imageItem: ImageItem) {
        // 处理图片点击事件
        Toast.makeText(this, "点击了: ${imageItem.title}", Toast.LENGTH_SHORT).show()
        
        // 这里可以打开图片预览页面
        // 例如：ImagePreviewDialog(this, imageItem.imageUrl).showPopupWindow()
    }
}
