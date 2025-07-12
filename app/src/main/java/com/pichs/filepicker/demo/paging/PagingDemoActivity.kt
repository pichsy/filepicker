package com.pichs.filepicker.demo.paging

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.pichs.filepicker.demo.R
import com.pichs.filepicker.demo.paging.adapter.UserLoadStateAdapter
import com.pichs.filepicker.demo.paging.adapter.UserPagingAdapter
import com.pichs.filepicker.demo.paging.viewmodel.UserViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Paging3 分页加载演示页面
 */
class PagingDemoActivity : AppCompatActivity() {

    private val viewModel: UserViewModel by viewModels()
    private lateinit var adapter: UserPagingAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paging_demo)
        
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
        adapter = UserPagingAdapter()
        
        // 设置加载状态适配器
        val loadStateAdapter = UserLoadStateAdapter { adapter.retry() }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter.withLoadStateFooter(footer = loadStateAdapter)
        
        // 监听加载状态
        adapter.addLoadStateListener { loadState ->
            // 显示/隐藏下拉刷新
            swipeRefreshLayout.isRefreshing = loadState.refresh is LoadState.Loading
            
            // 处理错误状态
            if (loadState.refresh is LoadState.Error) {
                // 可以在这里显示错误提示
            }
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.userPagingData.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }
    }
}
