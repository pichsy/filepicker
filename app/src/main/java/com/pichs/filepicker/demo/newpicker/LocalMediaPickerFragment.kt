package com.pichs.filepicker.demo.newpicker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.GridLayoutManager
import com.pichs.filepicker.FilePickerSelectType
import com.pichs.filepicker.common.ImagePreviewDialog
import com.pichs.filepicker.common.VideoPreviewDialog
import com.pichs.filepicker.demo.R
import com.pichs.filepicker.demo.databinding.FragmentLocalMediaPickerBinding
import com.pichs.filepicker.demo.newpicker.adapter.LocalMediaPagingAdapter
import com.pichs.filepicker.demo.newpicker.data.FolderScanner
import com.pichs.filepicker.demo.newpicker.viewmodel.LocalMediaViewModel
import com.pichs.filepicker.dialog.FolderChooseDialog
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.entity.MediaFolder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 本地媒体选择器 Fragment (使用 Paging3 优化)
 */
class LocalMediaPickerFragment : Fragment() {

    private var _binding: FragmentLocalMediaPickerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LocalMediaViewModel by viewModels()
    private lateinit var adapter: LocalMediaPagingAdapter

    private var currentSelectType = FilePickerSelectType.IMAGE_VIDEO

    companion object {
        fun newInstance(): LocalMediaPickerFragment {
            return LocalMediaPickerFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocalMediaPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews()
        setupRecyclerView()
        observeData()
        loadData()
    }

    private fun initViews() {
        // 设置下拉刷新
        binding.swipeRefreshLayout.setOnRefreshListener {
            adapter.refresh()
        }

        // 设置文件夹选择点击事件
        binding.llFolderSelector.setOnClickListener {
            showFolderChooseDialog()
        }

        // 设置类型切换点击事件
        binding.llTypeAll.setOnClickListener {
            switchSelectType(FilePickerSelectType.IMAGE_VIDEO)
        }

        binding.llTypeImage.setOnClickListener {
            switchSelectType(FilePickerSelectType.IMAGE)
        }

        binding.llTypeVideo.setOnClickListener {
            switchSelectType(FilePickerSelectType.VIDEO)
        }

        // 设置预览按钮点击事件
        binding.tvPreview.setOnClickListener {
            val selectedList = viewModel.selectedMediaList.value
            if (selectedList.isNotEmpty()) {
                // 这里可以打开预览页面
                Toast.makeText(requireContext(), "预览 ${selectedList.size} 个文件", Toast.LENGTH_SHORT).show()
            }
        }

        // 设置确定按钮点击事件
        binding.btnConfirm.setOnClickListener {
            val selectedList = viewModel.selectedMediaList.value
            if (selectedList.isNotEmpty()) {
                // 这里可以回调选择结果
                Toast.makeText(requireContext(), "选择了 ${selectedList.size} 个文件", Toast.LENGTH_SHORT).show()
            }
        }

        // 初始化选中类型UI
        updateSelectTypeUI(currentSelectType)
    }

    private fun setupRecyclerView() {
        adapter = LocalMediaPagingAdapter(
            viewModel = viewModel,
            onItemClick = { mediaEntity ->
                // 单击选择
                handleItemClick(mediaEntity)
            },
            onItemSelect = { mediaEntity ->
                // 选择状态改变
                updateSelectedCountUI()
            },
            onItemPreview = { mediaEntity ->
                // 预览
                showPreview(mediaEntity)
            }
        )

        // 使用网格布局，4列
        val gridLayoutManager = GridLayoutManager(requireContext(), 4)
        binding.recyclerView.layoutManager = gridLayoutManager

        // 优化 RecyclerView 性能
        binding.recyclerView.apply {
            setHasFixedSize(true)
            isNestedScrollingEnabled = true
            setItemViewCacheSize(20)
            recycledViewPool.setMaxRecycledViews(0, 50)
        }

        binding.recyclerView.adapter = adapter

        // 监听加载状态
        adapter.addLoadStateListener { loadState ->
            // 显示/隐藏下拉刷新
            binding.swipeRefreshLayout.isRefreshing = loadState.refresh is LoadState.Loading

            // 处理错误状态
            if (loadState.refresh is LoadState.Error) {
                val error = loadState.refresh as LoadState.Error
                Toast.makeText(requireContext(), "加载失败: ${error.error.message}", Toast.LENGTH_SHORT).show()
            }

            // 显示/隐藏空状态
            val isEmpty = loadState.refresh is LoadState.NotLoading && adapter.itemCount == 0
            binding.llEmpty.isVisible = isEmpty
        }
    }

    private fun observeData() {
        // 观察选中列表变化
        lifecycleScope.launch {
            viewModel.selectedMediaList.collectLatest { selectedList ->
                updateSelectedCountUI()
                updatePreviewButtonState(selectedList)
                updateConfirmButtonState(selectedList)
            }
        }

        // 观察文件总数变化
        lifecycleScope.launch {
            viewModel.totalFileCount.collectLatest { totalCount ->
                updateFileCountUI(totalCount)
            }
        }

        // 观察当前文件夹变化
        lifecycleScope.launch {
            viewModel.currentFolder.collectLatest { folder ->
                updateFolderNameUI(folder)
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            viewModel.getLocalMediaPagingData(requireContext()).collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        // 加载文件夹列表
        loadFolderList()
    }

    private fun loadFolderList() {
        lifecycleScope.launch {
            try {
                val folders = FolderScanner.scanFolders(requireContext(), currentSelectType)
                viewModel.setFolderList(folders)
            } catch (e: Exception) {
                // 处理错误
            }
        }
    }

    private fun switchSelectType(selectType: String) {
        if (currentSelectType == selectType) return

        currentSelectType = selectType
        updateSelectTypeUI(selectType)

        // 切换数据源
        lifecycleScope.launch {
            viewModel.switchSelectType(selectType, requireContext()).collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        // 重新加载文件夹列表
        loadFolderList()

        // 重新加载文件夹列表
        loadFolderList()
    }

    private fun updateSelectTypeUI(selectType: String) {
        // 重置所有标签颜色
        binding.tvTypeAll.setTextColor(ContextCompat.getColor(requireContext(), R.color.filepicker_text_color_gray))
        binding.tvTypeImage.setTextColor(ContextCompat.getColor(requireContext(), R.color.filepicker_text_color_gray))
        binding.tvTypeVideo.setTextColor(ContextCompat.getColor(requireContext(), R.color.filepicker_text_color_gray))

        // 设置当前选中标签颜色
        when (selectType) {
            FilePickerSelectType.IMAGE_VIDEO -> {
                binding.tvTypeAll.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            }
            FilePickerSelectType.IMAGE -> {
                binding.tvTypeImage.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            }
            FilePickerSelectType.VIDEO -> {
                binding.tvTypeVideo.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            }
        }
    }

    private fun handleItemClick(mediaEntity: MediaEntity) {
        // 处理单击事件，这里可以实现单击选择逻辑
        val isSelected = viewModel.isMediaSelected(mediaEntity)
        
        if (isSelected) {
            viewModel.removeSelectedMedia(mediaEntity)
        } else {
            val success = viewModel.addSelectedMedia(mediaEntity)
            if (!success) {
                Toast.makeText(requireContext(), "最多只能选择 ${viewModel.maxSelectCount.value} 个文件", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 刷新当前项
        adapter.notifyItemChanged(adapter.snapshot().indexOfFirst { it?.path == mediaEntity.path })
    }

    private fun showPreview(mediaEntity: MediaEntity) {
        if (mediaEntity.isVideo()) {
            VideoPreviewDialog(requireContext(), mediaEntity.path).showPopupWindow()
        } else {
            ImagePreviewDialog(requireContext(), mediaEntity.path).showPopupWindow()
        }
    }

    private fun updateSelectedCountUI() {
        val selectedCount = viewModel.getSelectedCount()
        val maxCount = viewModel.maxSelectCount.value
        binding.tvSelectedCount.text = "$selectedCount/$maxCount"
    }

    private fun updatePreviewButtonState(selectedList: List<MediaEntity>) {
        binding.tvPreview.isEnabled = selectedList.isNotEmpty()
        binding.tvPreview.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (selectedList.isNotEmpty()) android.R.color.white else R.color.filepicker_text_color_gray
            )
        )
    }

    private fun updateConfirmButtonState(selectedList: List<MediaEntity>) {
        binding.btnConfirm.isEnabled = selectedList.isNotEmpty()
        binding.btnConfirm.alpha = if (selectedList.isNotEmpty()) 1.0f else 0.5f
    }

    private fun updateFileCountUI(totalCount: Int) {
        val countText = when {
            totalCount == 0 -> "0张"
            totalCount < 1000 -> "${totalCount}张"
            totalCount < 10000 -> "${String.format("%.1f", totalCount / 1000.0)}k张"
            else -> "${totalCount / 1000}k张"
        }
        binding.tvFileCount.text = countText
    }

    private fun updateFolderNameUI(folder: MediaFolder?) {
        binding.tvFolderName.text = folder?.name ?: "全部"
    }

    private fun showFolderChooseDialog() {
        val folderList = viewModel.folderList.value.toMutableList()
        if (folderList.isEmpty()) {
            Toast.makeText(requireContext(), "正在加载文件夹列表...", Toast.LENGTH_SHORT).show()
            return
        }

        val currentFolder = viewModel.currentFolder.value
        val allAlbumName = "全部"

        FolderChooseDialog(
            mCtx = requireContext(),
            list = folderList,
            currentFolder = currentFolder,
        ) { selectedFolder ->
            // 选择文件夹回调
            viewModel.selectFolder(selectedFolder)

            // 这里可以根据选中的文件夹过滤数据
            // 由于我们使用的是分页加载，暂时只更新UI显示
            // 实际的文件夹过滤功能需要修改 PagingSource 来支持
            Toast.makeText(
                requireContext(),
                "已选择文件夹: ${selectedFolder?.name ?: allAlbumName}",
                Toast.LENGTH_SHORT
            ).show()
        }.showPopupWindow()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
