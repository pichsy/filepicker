package com.pichs.filepicker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drake.brv.listener.ItemDifferCallback
import com.drake.brv.utils.bindingAdapter
import com.drake.brv.utils.grid
import com.drake.brv.utils.linear
import com.drake.brv.utils.models
import com.drake.brv.utils.setup
import com.google.android.material.tabs.TabLayout
import com.pichs.filepicker.camera.CameraEntryModel
import com.pichs.filepicker.crop.FilePickerCropActivity
import com.pichs.filepicker.crop.FilePickerEditDialog
import com.pichs.filepicker.camera.FilePickerCaptureHelper
import com.pichs.filepicker.databinding.FilePickerItemRvAlbumBinding
import com.pichs.filepicker.databinding.FilePickerItemRvAlbumSelectedBinding
import com.pichs.filepicker.databinding.FilePickerItemRvAudioAlbumBinding
import com.pichs.filepicker.databinding.FilePickerItemRvCameraBinding
import com.pichs.filepicker.databinding.FragmentFilepickerHomeBinding
import com.pichs.filepicker.dialog.FilePickerFinalPreviewDialog
import com.pichs.filepicker.dialog.FilePickerPreviewDialog
import com.pichs.filepicker.dialog.FolderChooseDialog
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.loader.MediaLoader
import com.pichs.filepicker.query.FileQueryHelper
import com.pichs.filepicker.utils.FilePickerClickHelper
import com.pichs.filepicker.utils.FilePickerIconUtils
import com.pichs.filepicker.utils.FilePickerLog
import com.pichs.filepicker.utils.FilePickerTimeFormatUtils
import com.pichs.filepicker.widget.OnFilePickerDragItemTouchHelperCallback
import com.pichs.filepicker.widget.OnFilePickerItemSelectionChangedListener
import com.pichs.filepicker.widget.SelectedThumbnailStrip
import com.pichs.filepicker.widget.StripMetrics
import com.pichs.filepicker.select.QuotaType
import com.pichs.filepicker.select.SelectCheck
import com.pichs.xwidget.utils.XDisplayHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import razerdp.basepopup.BasePopupWindow
import java.io.File

class FilePickerFragment : Fragment(), View.OnClickListener {

    private val viewModel by activityViewModels<FilePickerViewModel>()

    private lateinit var binding: FragmentFilepickerHomeBinding

    private var currentTabType = FilePickerSelectType.IMAGE_VIDEO

    private var isTouchSelectStart = false

    // 主列表是否为网格布局（网格的最后一行 margin 刷新范围与线性不同）
    private var isGridMode = false

    // 相机入口是否真的挂到了网格上：决定空列表时"暂无素材"要不要让位给相机格
    private var isCameraEntryAttached = false

    // 一次拍摄在途时的落地信息。必须能被重建认领（见 onSaveInstanceState），
    // 否则宿主被回收后回来会对不上这次输出，那条 pending 记录就永远留在相册里了
    private var pendingCapture: FilePickerCaptureHelper.PendingCapture? = null

    /** 权限申请期间要记住用户点的是哪半边（拍照 / 录像） */
    private var pendingCaptureIsVideo = false
    /** "进入即拍照"是否已触发过（避免重建时重复拉起相机） */
    private var autoCameraStarted = false

    /**
     * CAMERA 权限。库自己不声明这个权限，只在**宿主声明了却没授权**时才会走到这里 ——
     * 那种情况下 ACTION_IMAGE_CAPTURE / ACTION_VIDEO_CAPTURE 会直接抛 SecurityException。
     *
     * 华为 BRE-AL80 / Android 12 上实测的原话（demo 声明了 CAMERA 但未授权时）：
     * `Permission Denial: starting Intent { act=android.media.action.VIDEO_CAPTURE ... } with revoked permission android.permission.CAMERA`
     * 所以这条路径不是防御性编程，是必需的。
     */
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val ctx = context ?: return@registerForActivityResult
            if (granted) {
                launchSystemCamera(pendingCaptureIsVideo)
            } else {
                Toast.makeText(ctx, viewModel.uiConfig.cameraEntryFailedToastContent, Toast.LENGTH_SHORT).show()
            }
        }

    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onCaptureResult(result.resultCode, result.data)
        }

    companion object {
        private const val STATE_CAPTURE_URI = "filepicker_capture_uri"
        private const val STATE_CAPTURE_IS_VIDEO = "filepicker_capture_is_video"
        private const val STATE_CAPTURE_PRE_Q_FILE = "filepicker_capture_pre_q_file"

        /**
         * @param bundle 传递参数
         */
        fun newInstance(): FilePickerFragment {
            return FilePickerFragment()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentFilepickerHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentTabType = viewModel.selectType.value

        // 拍摄途中宿主被回收再回来时，认领回那次未完成的输出（回执已经由 ActivityResult 框架补发）
        restorePendingCapture(savedInstanceState)
        maybeStartCameraOnLaunch()

        if (!isSelectTypeEqualsAll(viewModel.selectType.value) || viewModel.uiConfig.isHideSelectTab) {
            binding.selectTypeTabLayout.isVisible = false
        } else {
            binding.selectTypeTabLayout.isVisible = true
            binding.selectTypeTabLayout.apply {
                addTab(newTab().apply {
                    text = "全部"
                    tag = "all"
                })
                addTab(newTab().apply {
                    text = "视频"
                    tag = "video"
                })
                addTab(newTab().apply {
                    text = "图片"
                    tag = "image"
                })
                if (viewModel.selectType.value == FilePickerSelectType.IMAGE_VIDEO_GIF) {
                    addTab(newTab().apply {
                        text = "GIF"
                        tag = "gif"
                    })
                }
            }
        }

        binding.tvAlbum.text = viewModel.uiConfig.allAlbumName

        initConfigUI()

        initTab()
        if (isSelectTypeEqualsAll(viewModel.selectType.value) || viewModel.selectType.value == FilePickerSelectType.IMAGE || viewModel.selectType.value == FilePickerSelectType.VIDEO || viewModel.selectType.value == FilePickerSelectType.GIF) {
            initGridRecycler()
        } else {
            initLinearRecycler()
        }
        refreshCameraEntry()
        if (viewModel.isCanSlideChoose()) {
            initRecyclerSlideChoose()
        }

        initSelectedRecyclerView()

        initDataFlow()
        initListener()

        if (viewModel.isCanShowBottomSelectRecyclerView()) {
            binding.rvSelected.isVisible = true
        } else {
            binding.rvSelected.isVisible = false
        }

        updateBottomMenuSelectNumberUI()

        loadData()
    }


    private fun initListener() {
        FilePickerClickHelper.clicks(binding.llPreview) {
            // 预览按钮点击事件
            FilePickerLog.d("FilePickerFragment", "Preview clicked, selectedDataList size: ${viewModel.getSelectedDataList().size}")
            if (viewModel.getSelectedDataList().isEmpty()) {
                Toast.makeText(requireContext(), viewModel.uiConfig.atLeastSelectOneToastContent, Toast.LENGTH_SHORT).show()
                return@clicks
            }

            // todo 进入 展示界面弹窗，这里仅展示固定个数，不参与展示。
            FilePickerFinalPreviewDialog(requireContext(), viewModel, onDismissDataDelete = { deleteList ->
                viewModel.takeSelectionSnapshot()
                viewModel.removeSelectedDataAll(deleteList)
//                Toast.makeText(requireContext(), "删除了 ${deleteList.size} 个文件", Toast.LENGTH_SHORT).show()
                updateSelectDataUI(
                    viewModel.getSelectionChangedPaths(),
                    viewModel.getSelectedCount() + viewModel.tempSelectData.size == 0
                )
                updateBottomMenuSelectNumberUI()
            }, onDragEnd = {
                updateSelectDataUI()
            }, onConfirm = { resultList ->
//                Toast.makeText(requireContext(), "确定了 ${resultList?.path}", Toast.LENGTH_SHORT).show()
                FilePickerLog.d("FilePickerFragment", "onConfirm: resultList size:${resultList.size}")
                if (resultList.isEmpty()) {
                    FilePickerLog.d("FilePickerFragment", "item is null, return")
                    Toast.makeText(requireContext(), "未选择文件", Toast.LENGTH_SHORT).show()
                    return@FilePickerFinalPreviewDialog
                }
                callbackToChooser(resultList)
            }, onEditApplied = { edited ->
                // 只刷被编辑的那一格：缩略图换成编辑后的图 + 左下角"已编辑"角标
                val position = viewModel.getCurrentFolderDataList().indexOf(edited)
                if (position >= 0) {
                    binding.recyclerView.bindingAdapter?.notifyItemChanged(position)
                }
                binding.rvSelected.bindingAdapter?.notifyDataSetChanged()
                updateBottomMenuSelectNumberUI()
            }).showPopupWindow()
        }

        FilePickerClickHelper.clicks(binding.llOriginal) {
            viewModel.originalCheckedFlow.update { !viewModel.originalCheckedFlow.value }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initConfigUI() {
        if (viewModel.getSelectedCount() <= 0) {
            binding.btnConfirm.text = viewModel.uiConfig.confirmBtnText
            binding.tvPreview.text = viewModel.uiConfig.previewText
            binding.btnConfirm.isEnabled = false
            binding.btnConfirm.alpha = 0.4f
            binding.llPreview.isEnabled = false
            binding.tvPreview.isEnabled = false
        } else {
            binding.btnConfirm.isEnabled = true
            // 强制数量没选够时不高亮（但仍可点击，点了给"必须选 N 个"的提示）
            binding.btnConfirm.alpha = if (viewModel.selectRulesSatisfied()) 1f else 0.4f
            binding.llPreview.isEnabled = true
            binding.tvPreview.isEnabled = true
            binding.btnConfirm.text = "${viewModel.uiConfig.confirmBtnText}(${viewModel.getSelectedCount()})"
            binding.tvPreview.text = "${viewModel.uiConfig.previewText}(${viewModel.getSelectedCount()})"
        }

        binding.llOriginal.isVisible = viewModel.uiConfig.isShowOriginal && SelectTypeUtil.isCanOriginal(viewModel.selectType.value)
        binding.llPreview.isVisible = viewModel.uiConfig.isShowBottomPreviewText && SelectTypeUtil.isCanPreview(viewModel.selectType.value)
        binding.tvOriginal.text = viewModel.uiConfig.originalText
        binding.cboxOriginal.isChecked = viewModel.originalCheckedFlow.value

        binding.llBottomBar.isVisible = !viewModel.isCanSingleClickSelect()
    }

    private fun initTab() {
        binding.ivBack.setOnClickListener {
            activity?.apply {
                setResult(RESULT_CANCELED, Intent().apply {
                    FilePickerViewModel.finalSelectedDataList.clear()
                })
                finish()
            }
        }

        binding.tvAlbum.setOnClickListener(this)
        binding.ivArrowDown.setOnClickListener(this)

        binding.selectTypeTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // 四个分支原本是一模一样的四段拷贝，这里收敛成"先算目标类型"，
                // 顺便给相机入口留出跟着 tab 走的挂点
                val targetType = when (tab?.tag) {
                    "all" -> viewModel.selectType.value
                    "video" -> FilePickerSelectType.VIDEO
                    "image" -> FilePickerSelectType.IMAGE
                    "gif" -> FilePickerSelectType.GIF
                    else -> return
                }
                if (currentTabType == targetType) {
                    return
                }
                currentTabType = targetType
                // 相机入口跟着 tab 走：在"图片"页不该给录像入口，GIF 页干脆不给入口
                refreshCameraEntry()
                resetListDataWithSelectData()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })

        binding.btnConfirm.setOnClickListener {
            FilePickerLog.d("FilePickerFragment", "selectedData:${viewModel.getSelectedDataList().size},selectType:${viewModel.selectType.value}")
            // 必选条件没满足时拦住，并明确告诉用户还差什么
            val notSatisfiedMessage = viewModel.selectRulesNotSatisfiedMessage()
            if (notSatisfiedMessage != null) {
                Toast.makeText(requireContext(), notSatisfiedMessage, Toast.LENGTH_SHORT).show()
                FilePickerLog.d("FilePickerFragment", "必选未满足：$notSatisfiedMessage")
                return@setOnClickListener
            }
            callbackToChooser(ArrayList(viewModel.getSelectedDataList()))
        }


        if (isSelectTypeEqualsAll(currentTabType)) {
            if (binding.selectTypeTabLayout.selectedTabPosition != 0) {
                binding.selectTypeTabLayout.getTabAt(0)?.select()
            }
        }
    }

    /**
     * 判断类型是否是全部。
     */
    private fun isSelectTypeEqualsAll(selectType: String): Boolean {
        return selectType == FilePickerSelectType.IMAGE_VIDEO || selectType == FilePickerSelectType.IMAGE_VIDEO_GIF
    }

    private fun initDataFlow() {
        lifecycleScope.launch {
//            launch {
//                viewModel.refreshIndexChannel.receiveAsFlow().collect { size ->
//                    FilePickerLog.d("FilePickerFragment", "initDataFlow refreshIndexChannel: size:$size")
//
//                    binding.recyclerView.models = viewModel.currentFolderDataList.value
//                }
//            }
            launch {
                viewModel.allFolderDataList.collectLatest { folderList ->
                    FilePickerLog.d("FilePickerFragment8848", "initDataFlow: allFolderDataList.collectLatest-folderList-${folderList.size}")
                    viewModel.updateAllDataList(folderList)
                    viewModel.initUserSelectDataList(folderList)
                    resetListDataWithSelectData()
                }
            }

            launch {
                viewModel.currentFolderDataList.collectLatest { list ->
                    FilePickerLog.d("FilePickerFragment8848", "initDataFlow currentFolderDataList: size:${list.size}, currentTabType:$currentTabType")
                    // 性能优化：原来是循环内每个选中项都 getAllDataEntityList().contains() 一次
                    // （每次都 flatMap 全库并新建列表，O(选中数×全库)），改为每次发射只建一次
                    // 全库 path 集合再 O(1) 查询，集合在 Default 线程构建。
                    // contains 语义与 MediaEntity.equals（仅比 path，含 null）完全一致
                    val allPathSet: Set<String?> = withContext(Dispatchers.Default) {
                        val set = HashSet<String?>()
                        for (folder in viewModel.getAllDataList()) {
                            for (entity in folder.mediaEntityList) {
                                set.add(entity.path)
                            }
                        }
                        set
                    }
                    for (item in viewModel.selectedData) {
                        val isContains = allPathSet.contains(item.path)
                        if (!isContains) {
                            FilePickerLog.d("FilePickerFragment8848", "initDataFlow: item:${item.path}, isContains:$isContains")
                            viewModel.removeSelectedData(item)
                        }
                    }
                    // 这里处理数据
                    binding.recyclerView.models = list
                    // llEmpty 在布局里盖在 recyclerView 之上，挂着相机入口时不能让它遮住相机格
                    binding.llEmpty.isVisible = list.isEmpty() && !isCameraEntryAttached
                    updateBottomMenuSelectNumberUI()
                }
            }

            launch {
                viewModel.originalCheckedFlow.collectLatest {
                    FilePickerLog.d("FilePickerFragment", "initDataFlow originalCheckedFlow: $it")
                    binding.cboxOriginal.isChecked = it
                }
            }
        }
    }

    private fun loadData() {
        FilePickerLog.d("FilePickerFragment", "loadData: selectType:${viewModel.selectType.value}")
        viewModel.loadData(requireContext())
    }

    // todo
    // 过期结果守卫：多次 reset 并发时保证"后调用者必胜"，
    // 与原实现（主线程串行执行，最后一次计算的结果生效）的最终状态一致
    private var resetListSeq = 0L

    private fun resetListDataWithSelectData() {
        val seq = ++resetListSeq
        lifecycleScope.launch {
            // 性能优化：flatMap/filter/sortedByDescending 是 O(n log n) 的重计算，
            // 万级图库在主线程执行会卡帧，统一下沉到 Default 线程计算，主线程只负责应用结果
            val currentFolder = viewModel.currentFolder.value
            val resultList: List<MediaEntity>? = withContext(Dispatchers.Default) {
                if (isSelectTypeEqualsAll(viewModel.selectType.value)) {
                    when (currentTabType) {
                        FilePickerSelectType.IMAGE_VIDEO, FilePickerSelectType.IMAGE_VIDEO_GIF -> {
                            FilePickerLog.d(
                                "FilePickerFragment8848",
                                "resetListDataWithSelectData: allFolderDataList collectLatest-viewModel.selectType.value=${viewModel.selectType.value}"
                            )

                            if (currentFolder != null) {
                                FilePickerLog.d("FilePickerFragment8848", "resetListDataWithSelectData: currentFolder=有了- ----")
                                currentFolder.mediaEntityList?.sortedByDescending { it.addTime } ?: mutableListOf()
                            } else {
                                FilePickerLog.d("FilePickerFragment8848", "resetListDataWithSelectData: currentFolder=全部 ----")
                                viewModel.getAllDataList().flatMap { it.mediaEntityList }.sortedByDescending { it.addTime }.toMutableList()
                            }
                        }

                        FilePickerSelectType.IMAGE -> {
                            if (currentFolder != null) {
                                currentFolder.mediaEntityList?.filter { it.isImage() }?.sortedByDescending { it.addTime }?.toMutableList()
                                    ?: mutableListOf()
                            } else {
                                viewModel.getAllDataList().flatMap { it.mediaEntityList.filter { it.isImage() } }.sortedByDescending { it.addTime }
                                    .toMutableList()
                            }
                        }

                        FilePickerSelectType.VIDEO -> {
                            if (currentFolder != null) {
                                currentFolder.mediaEntityList?.filter { it.isVideo() }?.sortedByDescending { it.addTime }?.toMutableList()
                                    ?: mutableListOf()
                            } else {
                                viewModel.getAllDataList().flatMap { it.mediaEntityList.filter { it.isVideo() } }.sortedByDescending { it.addTime }
                                    .toMutableList()
                            }
                        }

                        FilePickerSelectType.GIF -> {
                            if (currentFolder != null) {
                                currentFolder.mediaEntityList?.filter { it.isGif() }?.sortedByDescending { it.addTime }?.toMutableList()
                                    ?: mutableListOf()
                            } else {
                                viewModel.getAllDataList().flatMap { it.mediaEntityList.filter { it.isGif() } }.sortedByDescending { it.addTime }
                                    .toMutableList()
                            }
                        }

                        // 与原实现一致：未列出的 tab 类型不做任何更新
                        else -> null
                    }
                } else {
                    if (currentFolder != null) {
                        currentFolder.mediaEntityList?.toMutableList()?.sortedByDescending { it.addTime } ?: mutableListOf()
                    } else {
                        viewModel.getAllDataList().flatMap { it.mediaEntityList }.sortedByDescending { it.addTime }.toMutableList()
                    }
                }
            }
            if (resultList != null && seq == resetListSeq) {
                viewModel.updateCurrentFolderDataList(resultList)
            }
        }
    }

    /**
     * 滑动选择
     */
    private fun initRecyclerSlideChoose() {
        binding.recyclerView.setOnItemSelectionChangedListener(object : OnFilePickerItemSelectionChangedListener {
            override fun onItemSelectionChanged(startPosition: Int, currentPosition: Int, isSelected: Boolean) {
                if (viewModel.slideChooseEnable.value.not()) {
                    // 不支持手滑。
                    return
                }
                FilePickerLog.d("FilePickerFragment6665", "startPosition:$startPosition, currentPosition:$currentPosition, isSelected:$isSelected")
                if (!isTouchSelectStart) {
                    return
                }
                // 我应该怎么实现
                val rvData = binding.recyclerView.models as? MutableList<MediaEntity> ?: return
                val from = minOf(startPosition, currentPosition)
                val to = maxOf(startPosition, currentPosition)
                // 如果是从上到下选，
                val tempList = if (currentPosition >= startPosition) {
                    rvData.subList(startPosition, currentPosition + 1)
                } else {
                    rvData.subList(currentPosition, startPosition + 1).reversed()
                }
                FilePickerLog.d("FilePickerFragment6665", "from:$from, to:$to, tempList.size:${tempList.size}, isSelected:$isSelected")
                viewModel.takeSelectionSnapshot()
                val oldMergeCount = viewModel.getSelectedCount() + viewModel.tempSelectData.size
                if (isSelected) {
                    // 这里需要区分 viewModel.maxSelectNumber.value==0 的情况。
                    // 如果是选中模式，那么 经过的都要选中。
                    viewModel.clearTempSelectData()
                    val list = tempList.filter { it !in viewModel.getSelectedDataList() }

                    if (viewModel.isSelectRulesActive()) {
                        // 新模型：按类型配额逐项贪心筛选（图片/视频各自额度、互斥锁定）
                        val accepted = viewModel.filterSelectableIndices(list).map { list[it] }
                        if (accepted.size < list.size) {
                            FilePickerLog.d(
                                "FilePickerFragment",
                                "滑动连选被规则截断：accepted=${accepted.size}, total=${list.size}, 规则=${viewModel.describeSelectRules()}"
                            )
                        }
                        viewModel.addAllTempSelectData(accepted)
                    } else if (viewModel.maxSelectNumber.value == 0 || viewModel.maxSelectNumber.value == Int.MAX_VALUE) {
                        // 如果没有限制选择数量，那么直接添加到临时选择数据中。
                        viewModel.addAllTempSelectData(list)
                    } else {
                        val dx = list.size + viewModel.getSelectedCount() - viewModel.maxSelectNumber.value

                        FilePickerLog.d(
                            "FilePickerFragment",
                            "dx:$dx, list.size:${list.size}, selectedData.size:${viewModel.getSelectedCount()}, ----x=${viewModel.maxSelectNumber.value - viewModel.getSelectedCount()}"
                        )

                        if (dx > 0) {
                            viewModel.addAllTempSelectData(list.subList(0, list.size - dx))
                        } else {
                            viewModel.addAllTempSelectData(list)
                        }
                    }
                } else {
                    // 如果是取消选中模式，那么经过的都要取消选中。
                    viewModel.removeAllTempSelectData(tempList)
                    viewModel.removeSelectedDataAll(tempList)
                    // 这里需要刷新移除的 条目
                    // updateUnselectDataUI(tempList)
                }

                // 只刷新角标发生变化的格子；已选数量跨过 0 时同步最后一行的 margin
                updateSelectDataUI(
                    viewModel.getSelectionChangedPaths(),
                    (oldMergeCount == 0) != ((viewModel.getSelectedCount() + viewModel.tempSelectData.size) == 0)
                )
                updateBottomMenuSelectNumberUI()
            }

            override fun onToucheSelectStart() {
                isTouchSelectStart = true
            }

            override fun onTouchSelectEnd() {
                isTouchSelectStart = false
                if (viewModel.tempSelectData.isNotEmpty()) {
                    // 如果临时选择数据不为空，那么就添加到已选择数据中。
                    // 合并前后综合下标不变，角标无需刷新。
                    viewModel.mergeTempSelectDataIntoSelected()
//                    updateSelectDataUI()
//                    updateBottomMenuSelectNumberUI()
                }
            }

            override fun onSelectionMaxStopped(maxCount: Int) {
                // 达到最大选择数量，提示用户可以弹窗。
                Toast.makeText(requireContext(), viewModel.uiConfig.selectMaxNumberOverToastContent, Toast.LENGTH_SHORT).show()
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun initLinearRecycler() {
        isGridMode = false
        // 其他文件选择时使用样式和组件。
        binding.recyclerView.itemAnimator = null
        context?.let { ctx ->
            binding.recyclerView.addItemDecoration(
                DividerItemDecoration(
                    ctx, RecyclerView.VERTICAL
                ).apply {
                    ContextCompat.getDrawable(ctx, R.drawable.filepicker_item_decroration_line)?.let {
                        setDrawable(it)
                    }
                })
        }

        binding.recyclerView.linear(RecyclerView.VERTICAL).setup {
            addType<MediaEntity>(R.layout.file_picker_item_rv_audio_album)

            itemDifferCallback = BrvItemDifferCallback()

            onBind {
                val item = getModel<MediaEntity>()
                val itemBinding = getBinding<FilePickerItemRvAudioAlbumBinding>()
                itemBinding.tvName.text = item.name ?: ""

                itemBinding.ivCoverImage.setImageResource(FilePickerIconUtils.getFileIcon(item))

                if (item.isAudio()) {
                    itemBinding.tvInfo.text =
                        FilePickerTimeFormatUtils.formatTimeMillSeconds(item.duration) + " - " + FilePickerTimeFormatUtils.formatFileSize(item.size)
                    // todo 加载对用图标
                } else {
                    itemBinding.tvInfo.text = FilePickerTimeFormatUtils.formatFileSize(item.size)
                }

                itemBinding.tvSelectIndex.isVisible = !viewModel.isCanSingleClickSelect()

                val indexOfSelect = viewModel.indexOfSelected(item)

                if (indexOfSelect != -1) {
                    itemBinding.tvSelectIndex.text = "${indexOfSelect + 1}"
                    itemBinding.tvSelectIndex.isChecked = true
//                    itemBinding.tvSelectIndex.setNormalBackgroundColor(ContextCompat.getColor(context, R.color.file_picker_index_bg_color))
//                    itemBinding.ivCoverImage.foreground = ContextCompat.getDrawable(context, R.drawable.item_filepicker_select_mask)
                    itemBinding.clRoot.isChecked = true
                    itemBinding.root.isSelected = true
                } else {
                    itemBinding.tvSelectIndex.text = ""
                    itemBinding.tvSelectIndex.isChecked = false
//                    itemBinding.tvSelectIndex.setNormalBackgroundColor(Color.TRANSPARENT)
//                    itemBinding.ivCoverImage.foreground = null
                    itemBinding.clRoot.isChecked = false
                    itemBinding.root.isSelected = false
                }

                // 判断是否是最后一行
                if (modelPosition == (itemCount - 1) && (viewModel.getSelectedCount() + viewModel.tempSelectData.size) > 0) {
                    itemBinding.clRoot.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = XDisplayHelper.dp2px(requireContext(), 80f)
                    }
                } else {
                    itemBinding.clRoot.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = XDisplayHelper.dp2px(requireContext(), 1f)
                    }
                }

                itemBinding.root.setOnClickListener {
                    if (viewModel.isCanSingleClickSelect()) {
                        handleSingleClick(item)
                        return@setOnClickListener
                    }

                    FilePickerLog.d("FilePickerFragment", "item.path:${item.path},mimeType:${item.mimeType}")
                    if (viewModel.containsSelectedData(item)) {
                        viewModel.takeSelectionSnapshot()
                        viewModel.removeSelectedData(item)
                        notifyItemChanged(modelPosition)
                        updateBottomMenuSelectNumberUI()
                        // 更新角标（只刷新角标变化的格子）
                        updateSelectDataUI(
                            viewModel.getSelectionChangedPaths(),
                            viewModel.getSelectedCount() + viewModel.tempSelectData.size == 0
                        )
                    } else {
                        val selectCheck = viewModel.canSelect(item)
                        if (selectCheck !is SelectCheck.Allowed) {
                            toastSelectCheck(selectCheck)
                            return@setOnClickListener
                        }
                        viewModel.addSelectedData(item)
                        notifyItemChanged(modelPosition)
                        if (modelPosition != itemCount - 1) {
                            notifyItemChanged(itemCount - 1) // 刷新最后一行
                        }
                        updateBottomMenuSelectNumberUI()
                    }
                }
            }
        }
    }


    class BrvItemDifferCallback : ItemDifferCallback {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            if (oldItem is MediaEntity && newItem is MediaEntity) {
                return oldItem.path == newItem.path
            }
            return false
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            if (oldItem is MediaEntity && newItem is MediaEntity) {
                return oldItem.path == newItem.path && oldItem.selectedIndex == newItem.selectedIndex
            }
            return false
        }
    }

    /**
     * 图库选择使用的样式和组件
     */
    @SuppressLint("SetTextI18n")
    private fun initGridRecycler() {
        isGridMode = true
//        binding.tvMaxSelectNumber.text = "${viewModel.maxSelectNumber.value}"
        binding.recyclerView.itemAnimator = null

        binding.recyclerView.grid(4).setup {
            addType<MediaEntity>(R.layout.file_picker_item_rv_album)
            // 相机入口走 BRV 的 header 通道，刻意不进 models（原因见 CameraEntryModel 的注释）
            addType<CameraEntryModel>(R.layout.file_picker_item_rv_camera)

            itemDifferCallback = BrvItemDifferCallback()

            onPayload {
                FilePickerLog.d("FilePickerFragment5656", "onPayload: modelPosition:$modelPosition, payloads:${it.joinToString(",")}")
            }

            onBind {
                // BRV 的 onBind 是全局唯一的一个（BindingAdapter.onBind，不按 type 分发），
                // 多类型必须在这里先分流，否则相机项走到下面的 getModel<MediaEntity>() 直接 CCE
                val cameraModel = _data as? CameraEntryModel
                if (cameraModel != null) {
                    val cameraBinding = getBinding<FilePickerItemRvCameraBinding>()
                    val config = viewModel.uiConfig
                    cameraBinding.ivCameraPhoto.setImageResource(
                        config.cameraEntryIconResId.takeIf { it != 0 }
                            ?: R.drawable.filepicker_ic_camera_entry
                    )
                    cameraBinding.ivCameraVideo.setImageResource(
                        config.cameraEntryVideoIconResId.takeIf { it != 0 }
                            ?: R.drawable.filepicker_ic_video_entry
                    )
                    cameraBinding.tvCameraPhoto.text = config.cameraEntryPhotoText
                    cameraBinding.tvCameraVideo.text = config.cameraEntryVideoText

                    // 只允许一种模式时把另一半和分隔线收掉：LinearLayout 会跳过 GONE 的子 view，
                    // 剩下那一半（weight=1）自然撑满整格，图标也顺势放大
                    val isSplit = cameraModel.allowPhoto && cameraModel.allowVideo
                    cameraBinding.llHalfPhoto.isVisible = cameraModel.allowPhoto
                    cameraBinding.llHalfVideo.isVisible = cameraModel.allowVideo
                    cameraBinding.vCameraDivider.isVisible = isSplit

                    // 图标尺寸跟着"劈不劈"变：劈开时半边只有屏宽/8 宽（360dp 机器上约 44dp），
                    // 用整格的尺寸会把图标和下面那行文字一起挤出去；单模式时整格空着，
                    // 就放大到 44dp 让它更有存在感（矢量图放大会自动按比例重绘，不会糊）
                    val iconSize = XDisplayHelper.dp2px(requireContext(), if (isSplit) 22f else 30f)
                    cameraBinding.ivCameraPhoto.updateLayoutParams {
                        width = iconSize
                        height = iconSize
                    }
                    cameraBinding.ivCameraVideo.updateLayoutParams {
                        width = iconSize
                        height = iconSize
                    }

                    // 点击挂在整半边（图标 + 文字）上，热区更大
                    cameraBinding.llHalfPhoto.setOnClickListener { startCapture(isVideo = false) }
                    cameraBinding.llHalfVideo.setOnClickListener { startCapture(isVideo = true) }
                    return@onBind
                }

                val item = getModel<MediaEntity>()

                val itemBinding = getBinding<FilePickerItemRvAlbumBinding>()

//                itemBinding.clSelectArea.updateLayoutParams {
//                    width = screenWidth / 7
//                    height = width
//                }

                // 编辑过的图片展示编辑产物（身份仍是原图），并显示左下角"已编辑"角标
                MediaLoader.loadThumbnail(viewModel.displayEntity(item), itemBinding.ivCoverImage)
                itemBinding.ivEditedBadge.isVisible = viewModel.isEdited(item)

                itemBinding.clSelectArea.isVisible = !viewModel.isCanSingleClickSelect()
                itemBinding.ivPreviewImage.isVisible = !viewModel.isCanSingleClickSelect()

                if (item.isVideo()) {
                    itemBinding.tvDuration.visibility = View.VISIBLE
                    itemBinding.tvDuration.text = FilePickerTimeFormatUtils.formatTimeMillSeconds(item.duration)
                } else if (item.isGif()) {
                    itemBinding.tvDuration.visibility = View.VISIBLE
                    itemBinding.tvDuration.text = "GIF"
                } else {
                    itemBinding.tvDuration.visibility = View.GONE
                    itemBinding.tvDuration.text = ""
                }

                val indexOfSelect = viewModel.indexOfSelected(item)
                FilePickerLog.d("FilePickerFragment", "item:${item.path}, selectedIndex:${indexOfSelect}, modelPosition:$modelPosition")
                if (indexOfSelect != -1) {
                    itemBinding.tvSelectIndex.text = "${indexOfSelect + 1}"
//                    itemBinding.tvSelectIndex.setNormalBackgroundColor(ContextCompat.getColor(context, R.color.file_picker_index_bg_color))
                    itemBinding.tvSelectIndex.isChecked = true
                    itemBinding.ivCoverImage.foreground = ContextCompat.getDrawable(context, R.drawable.item_filepicker_select_mask)
                    itemBinding.root.isSelected = true
                } else {
                    itemBinding.tvSelectIndex.text = ""
//                    itemBinding.tvSelectIndex.setNormalBackgroundColor(Color.TRANSPARENT)
                    itemBinding.tvSelectIndex.isChecked = false
                    itemBinding.ivCoverImage.foreground = null
                    itemBinding.root.isSelected = false
                }

                // 判断是否是最后一行。
                // 行首必须在 **adapter 坐标**里算：相机入口是网格里的普通一格（占 adapter position 0），
                // 它后面的媒体格整体右移一格，所以"按 4 取模"只有在 adapter 下标上才对应真实视觉行。
                // 算出行首之后，再换算回 models 下标，才能和 modelPosition 比大小。
                // （反过来先用 modelCount 取模会错：header + 8 张图会算出 m4 那行是"最后一行"，
                //   网格中间凭空多出一道 80dp 的空隙，而真正最后一行的 m7 反而没有。）
                val adapterLastRowStart = itemCount - (itemCount % 4).let { if (it == 0) 4 else it }
                val lastRowStart = adapterLastRowStart - adapter.headerCount

                FilePickerLog.d("FilePickerFragment7777", "modelPosition:$modelPosition, lastRowStart:$lastRowStart, itemCount:$itemCount")

                if (modelPosition >= lastRowStart && (viewModel.getSelectedCount() + viewModel.tempSelectData.size) > 0) {
                    itemBinding.clRoot.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = XDisplayHelper.dp2px(requireContext(), 80f)
                    }
                } else {
                    itemBinding.clRoot.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = XDisplayHelper.dp2px(requireContext(), 1f)
                    }
                }

                itemBinding.ivCoverImage.setOnClickListener {
                    if (viewModel.isCanSingleClickSelect()) {
                        handleSingleClick(item)
                        return@setOnClickListener
                    }
                    FilePickerLog.d("FilePickerFragment", "item.path:${item.path},mimeType:${item.mimeType}")
                    if (viewModel.containsSelectedData(item)) {
                        viewModel.takeSelectionSnapshot()
                        viewModel.removeSelectedData(item)
                        itemBinding.root.isSelected = false
                        // notifyItemChanged 收的是 adapter position，而 modelPosition 是 models 下标，
                        // 相机入口作为 header 占了 position 0，所以要补 headerCount（没挂 header 时是 0）
                        notifyItemChanged(modelPosition + adapter.headerCount)
                        updateBottomMenuSelectNumberUI()
                        // 更新角标（只刷新角标变化的格子）
                        updateSelectDataUI(
                            viewModel.getSelectionChangedPaths(),
                            viewModel.getSelectedCount() + viewModel.tempSelectData.size == 0
                        )
                    } else {
                        val selectCheck = viewModel.canSelect(item)
                        if (selectCheck !is SelectCheck.Allowed) {
                            toastSelectCheck(selectCheck)
                            return@setOnClickListener
                        }
                        viewModel.addSelectedData(item)
                        itemBinding.root.isSelected = true
                        notifyItemChanged(modelPosition + adapter.headerCount)
                        updateBottomMenuSelectNumberUI()
                        // 刷新最后一行（adapterLastRowStart 本来就是 adapter 坐标，不用再换算）
                        notifyItemRangeChanged(
                            adapterLastRowStart,
                            itemCount - adapterLastRowStart
                        )
//                        updateSelectDataUI()
                    }
                }

                itemBinding.ivPreviewImage.setOnClickListener {
                    // 进入弹窗（头像是单击即裁剪）
                    if (viewModel.isCanSingleClickSelect()) {
                        handleSingleClick(item)
                        return@setOnClickListener
                    }
                    showFilePickerPreviewDialog(item)
                }
            }
        }

        // 新模型：滑动连选的上限由逐项按类型筛选（filterSelectableIndices）负责，
        // 这里传一个大值，避免 RecyclerView 在合计层面提前截断
        binding.recyclerView.maxSelectNumber =
            if (viewModel.isSelectRulesActive()) Int.MAX_VALUE else viewModel.maxSelectNumber.value
        binding.recyclerView.currentSelectedCountProvider = { viewModel.getSelectedCount() + viewModel.tempSelectData.size }
    }

    /** 底部缩略图间距（按"一屏 5 个半"算出来），null = 还没测量/保持原样 */
    private var stripLayout: StripMetrics.Layout? = null

    private fun initSelectedRecyclerView() {
        binding.rvSelected.linear(RecyclerView.HORIZONTAL).setup {
            addType<MediaEntity>(R.layout.file_picker_item_rv_album_selected)

            onBind {
                val item = getModel<MediaEntity>()
                val itemBinding = getBinding<FilePickerItemRvAlbumSelectedBinding>()

                if (item.isVideo() || item.isImage() || item.isGif()) {
                    MediaLoader.loadThumbnail(viewModel.displayEntity(item), itemBinding.ivCoverImage)
                } else {
                    itemBinding.ivCoverImage.setImageResource(FilePickerIconUtils.getFileIcon(item))
                }

                itemBinding.clSelectDelete.isVisible = viewModel.uiConfig.isShowSelectedListDeleteIcon
                itemBinding.ivDelete.setImageResource(if (viewModel.uiConfig.selectedListDeleteIconResId != 0) viewModel.uiConfig.selectedListDeleteIconResId else R.drawable.filepicker_ic_delete_item)
                itemBinding.ivDelete.setBackgroundColor(viewModel.uiConfig.selectedListDeleteIconBackgroundColor)
                // 只改间距（格子尺寸、图片 50dp、删除按钮都不动），让一屏露出 5 个半
                SelectedThumbnailStrip.applyLayout(itemBinding.root, stripLayout)

                if (item.isVideo()) {
                    itemBinding.tvDuration.visibility = View.VISIBLE
                    itemBinding.tvDuration.text = FilePickerTimeFormatUtils.formatTimeMillSeconds(item.duration)
                } else if (item.isGif()) {
                    itemBinding.tvDuration.visibility = View.VISIBLE
                    itemBinding.tvDuration.text = "GIF"
                } else {
                    itemBinding.tvDuration.visibility = View.GONE
                    itemBinding.tvDuration.text = ""
                }

                itemBinding.clSelectDelete.setOnClickListener {
                    // 删除选中项
                    viewModel.takeSelectionSnapshot()
                    viewModel.removeSelectedData(item)
                    // 更新角标（只刷新角标变化的格子）
                    updateSelectDataUI(
                        viewModel.getSelectionChangedPaths(),
                        viewModel.getSelectedCount() + viewModel.tempSelectData.size == 0
                    )
                    updateBottomMenuSelectNumberUI()
                }
            }
        }.models = viewModel.getSelectedDataList()

        OnFilePickerDragItemTouchHelperCallback(binding.rvSelected.bindingAdapter, viewModel, onDragEnd = {
            updateSelectDataUI()
        }).let { callback ->
            ItemTouchHelper(callback).attachToRecyclerView(binding.rvSelected)
        }

        // 算间距（格子尺寸不动）：等 rv 测量完、子项绑出来之后再算，算不出就隔 80ms 重试
        binding.rvSelected.post { applyStripSpacing() }
    }

    /**
     * 按"一屏 5 个半"算一次底部条间距并应用。
     * 只改间距、不改格子尺寸；间距没变就不重绑，避免缩略图闪烁。
     */
    private fun applyStripSpacing(attempt: Int = 0) {
        if (!::binding.isInitialized) return
        val rv = binding.rvSelected
        val itemWidth = rv.getChildAt(0)?.width ?: 0
        if (rv.width <= 0 || itemWidth <= 0) {
            if (attempt < 10) rv.postDelayed({ applyStripSpacing(attempt + 1) }, 80)
            return
        }
        val layout = SelectedThumbnailStrip.resolveLayout(rv, itemWidth)
        FilePickerLog.d(
            "FilePickerFragment",
            "缩略图条：可视宽=${rv.width} 格子宽=$itemWidth 算出=$layout 当前=$stripLayout"
        )
        if (layout != null && layout != stripLayout) {
            stripLayout = layout
            rv.bindingAdapter?.notifyDataSetChanged()
        }
    }

    /** 最普通的平滑滚动：交给 RecyclerView 自己算；目标已完全可见就不动（省掉无意义的动画） */
    fun scrollItemToCenter(recyclerView: RecyclerView, position: Int) {
        SelectedThumbnailStrip.smoothScrollToItem(recyclerView, position)
    }

    /**
     * 展示文件预览对话框
     */
    @OptIn(UnstableApi::class)
    private fun showFilePickerPreviewDialog(item: MediaEntity) {
        FilePickerPreviewDialog(requireContext(), viewModel, item, onSelect = { item, isSelect, position ->
            // 选择
            FilePickerLog.d("FilePickerFragment", "item.path:${item.path},isSelect:$isSelect")
            if (isSelect) {
//                if (isOverMaxSelectNumber(viewModel.getSelectedDataList().size + viewModel.tempSelectData.size)) {
//                    return@FilePickerPreviewDialog
//                }
                // position 是弹窗按 currentFolderDataList 算出来的下标（即 models 下标），
                // 换到 adapter position 要补上相机 entry 占的 header
                val adapter = binding.recyclerView.bindingAdapter
                adapter.notifyItemChanged(position + adapter.headerCount)
            } else {
                // 更新角标（变更发生在弹窗内部，这里兜底刷新所有带角标的格子 + 被取消的这一格）
                updateSelectDataUI(
                    viewModel.getBadgedPathSet() + setOfNotNull(item.path),
                    viewModel.getSelectedCount() + viewModel.tempSelectData.size == 0
                )
            }
            updateBottomMenuSelectNumberUI()
        }, onDragEnd = {
            updateSelectDataUI()
            updateBottomMenuSelectNumberUI()
        }, onSelectListScrollChanged = { index, type, dx ->
            if (type == 1) {
                scrollItemToCenter(binding.rvSelected, index)
            } else if (type == 2) {
                binding.rvSelected.scrollBy(dx, 0)
            }
        }, onConfirm = { pos ->
            val finalList = ArrayList(viewModel.getSelectedDataList())
            // 确认
            if (finalList.isEmpty()) {
                viewModel.getCurrentFolderDataList().getOrNull(pos)?.let { item ->
                    finalList.add(item)
                }
            }
            FilePickerLog.d("FilePickerFragment", "onConfirm: finalList=${finalList.size}")
            callbackToChooser(finalList)
        }, onEditApplied = { edited ->
            // 只刷被编辑的那一格：缩略图换成编辑后的图 + 左下角"已编辑"角标
            val position = viewModel.getCurrentFolderDataList().indexOf(edited)
            if (position >= 0) {
                binding.recyclerView.bindingAdapter?.notifyItemChanged(position)
            }
            // 底部已选缩略图条同步换成编辑后的图
            binding.rvSelected.bindingAdapter?.notifyDataSetChanged()
            updateBottomMenuSelectNumberUI()
        }).showPopupWindow()

        var indexOfSelected = viewModel.indexOfSelected(item)
        if (indexOfSelected < 0) {
            indexOfSelected = 0
        }
        if (indexOfSelected > viewModel.getSelectedCount()) {
            indexOfSelected = viewModel.getSelectedCount() - 1
        }
        scrollItemToCenter(binding.rvSelected, indexOfSelected)
    }

    fun getScrollX(recyclerView: RecyclerView): Int {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return 0
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val firstView = layoutManager.findViewByPosition(firstVisible) ?: return 0
        val itemWidth = firstView.width
        val marginLeft = (firstView.layoutParams as? ViewGroup.MarginLayoutParams)?.leftMargin ?: 0
        val scrollX = firstVisible * (itemWidth + marginLeft) - (firstView.left - marginLeft)
        return scrollX.coerceAtLeast(0)
    }

    /**
     * 选择数据返回
     */
    /**
     * 单选单击的统一入口：
     * - 配置了固定比例（头像场景）→ 先进裁剪页，确认后把编辑产物挂到原图实体上返回；
     * - 否则维持原行为：直接回调选择器。
     */
    private fun handleSingleClick(item: MediaEntity) {
        if (viewModel.cropAspect.value > 0f && item.isImage()) {
            launchAvatarCrop(item)
            return
        }
        // 单图点击直接编辑：点图就弹编辑弹窗，完成后返回成品；取消则留在选择器继续选
        if (viewModel.editOnClick.value && item.isImage()) {
            launchEditDialogForSingleClick(item)
            return
        }
        callbackToChooser(arrayListOf(item))
    }

    /**
     * 头像场景：点图直接进固定比例裁剪页。
     * 裁剪结果只挂到原图实体的 [MediaEntity.editedPath] 上（相册原图不动），然后把原图实体返回给宿主。
     */
    /**
     * 单图点击直接编辑：弹出和预览页「编辑」相同的编辑弹窗。
     *
     * - 编辑弹窗「完成」→ attachEdit + 直接返回（结果 path = 编辑后成品）
     * - 编辑弹窗「取消」/返回 → 只关闭弹窗，用户继续在选择器里选（不返回）
     */
    private fun launchEditDialogForSingleClick(item: MediaEntity) {
        val ctx = context ?: return
        val source = viewModel.displayEntity(item) ?: item
        FilePickerLog.d("FilePickerFragment", "点击即编辑: ${item.path}")
        FilePickerEditDialog(
            context = ctx,
            entity = source,
            outputMaxSize = viewModel.cropOutputMaxSize.value,
            quality = viewModel.cropQuality.value,
            uiConfig = viewModel.uiConfig,
        ) { editedPath ->
            if (editedPath.isNullOrEmpty()) {
                FilePickerLog.d("FilePickerFragment", "点击即编辑已取消，继续选择")
                return@FilePickerEditDialog
            }
            FilePickerLog.d("FilePickerFragment", "点击即编辑完成: ${item.path} -> $editedPath")
            viewModel.attachEdit(item, editedPath)
            callbackToChooser(arrayListOf(item))
        }.showPopupWindow()
    }

    private fun launchAvatarCrop(item: MediaEntity) {
        FilePickerLog.d("FilePickerFragment", "launchAvatarCrop: ${item.path}")
        val aspect = viewModel.cropAspect.value
        if (aspect <= 0f) {
            callbackToChooser(arrayListOf(item))
            return
        }
        val ctx = context ?: return
        FilePickerCropActivity.start(
            context = ctx,
            entity = item,
            aspect = aspect,
            outputMaxSize = viewModel.cropOutputMaxSize.value,
            quality = viewModel.cropQuality.value,
            title = "裁剪",
        ) { editedPath ->
            if (editedPath.isNullOrEmpty()) {
                FilePickerLog.d("FilePickerFragment", "头像裁剪已取消")
                return@start
            }
            viewModel.attachEdit(item, editedPath)
            FilePickerLog.d("FilePickerFragment", "头像裁剪完成: ${item.editedPath}")
            callbackToChooser(arrayListOf(item))
        }
    }

    private fun callbackToChooser(selectList: ArrayList<MediaEntity>) {
        FilePickerLog.d("FilePickerFragment", "1111callbackToChooser: selectList size:${selectList.size}, selectType:${viewModel.selectType.value}")
        // 这里可以回调到选择器，通知选择完成。
        if (selectList.isEmpty()) {
            Toast.makeText(context, viewModel.uiConfig.atLeastSelectOneToastContent, Toast.LENGTH_SHORT).show()
            return
        }
        // 返回给调用方时，编辑过的条目**用编辑后的图片作为真实地址**
        // （editedPath 只是会话内的临时显示地址，点确定/完成后已固化到 Pictures/filepicker，成品就是正式地址）
        val resultList = ArrayList(selectList.map { entity ->
            if (viewModel.isEdited(entity)) {
                val edited = viewModel.editedPathOf(entity)
                if (!edited.isNullOrEmpty()) {
                    FilePickerLog.d("FilePickerFragment", "结果用编辑后地址: ${entity.path} -> $edited")
                    entity.copy(path = edited, uri = null, editedPath = null)
                } else entity
            } else entity
        })
        activity?.apply {
            setResult(RESULT_OK, Intent().apply {
                FilePickerViewModel.finalSelectedDataList.clear()
                FilePickerViewModel.finalSelectedDataList.addAll(resultList)
                putParcelableArrayListExtra("selectedDataList", resultList)
                putExtra("isUseOriginal", viewModel.originalCheckedFlow.value)
            })
            // 异步清理编辑临时缓存（中间产物/会话缓存）。
            // 注意：只清缓存目录，Pictures/filepicker 下的成品永久保留。
            val appContext = context?.applicationContext
            if (appContext != null) {
                val keep = selectList.mapNotNull { it.editedPath }
                Thread {
                    val size = com.pichs.filepicker.crop.CropEngine.cacheSize(appContext)
                    com.pichs.filepicker.crop.CropEngine.clearCacheExcept(appContext, keep)
                    FilePickerLog.d("FilePickerFragment", "返回调用方：已异步清理编辑缓存 ${size}B，保留${keep.size}个结果")
                }.start()
            }
            finish()
        }
    }

    fun isOverMaxSelectNumber(listSize: Int): Boolean {
        return viewModel.isOverMaxSelectNumber(listSize)
    }

    /** 把"不能选"的原因翻译成给用户的提示 */
    private fun toastSelectCheck(check: SelectCheck) {
        val message = viewModel.selectRejectedMessage(check) ?: return
        val ctx = context ?: return
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        FilePickerLog.d("FilePickerFragment", "选择被拦下：$message（$check），规则=${viewModel.describeSelectRules()}")
    }

    @SuppressLint("SetTextI18n")
    private fun updateBottomMenuSelectNumberUI() {
        val selectedMergeSize = viewModel.getSelectedCount() + viewModel.tempSelectData.size
        if (selectedMergeSize > 0) {
            binding.llPreview.isEnabled = true
            binding.tvPreview.isEnabled = true

            binding.btnConfirm.isEnabled = true
            // 强制数量没选够时不高亮（仍可点击，点了给提示）
            binding.btnConfirm.alpha = if (viewModel.selectRulesSatisfied()) 1f else 0.4f

            binding.btnConfirm.text = "${viewModel.uiConfig.confirmBtnText}(${selectedMergeSize})"
            binding.tvPreview.text = "${viewModel.uiConfig.previewText}(${selectedMergeSize})"


            // 底部列表
            if (viewModel.isCanShowBottomSelectRecyclerView()) {
                binding.rvSelected.isVisible = true
            }
            // 内容没变不要重置 models：BRV 的赋值是 notifyDataSetChanged，
            // 会把整条缩略图重新 bind 一遍（Glide 重新请求），表现为闪烁。
            SelectedThumbnailStrip.syncModels(
                binding.rvSelected,
                viewModel.getSelectedDataList() + viewModel.tempSelectData
            )
        } else {
            binding.llPreview.isEnabled = false
            binding.tvPreview.isEnabled = false
            binding.btnConfirm.isEnabled = false
            binding.btnConfirm.alpha = 0.4f

            binding.btnConfirm.text = viewModel.uiConfig.confirmBtnText
            binding.tvPreview.text = viewModel.uiConfig.previewText

            // 底部选择列表
            SelectedThumbnailStrip.syncModels(binding.rvSelected, emptyList())
            binding.rvSelected.isVisible = false
        }
    }

    /**
     * 更新选择数据的UI。 主要是角标。
     * 只刷新受影响的格子：affectedPaths 为角标发生变化的 path 集合；为 null 时兜底刷新所有带角标的格子。
     * marginChanged 表示已选数量是否跨过 0（最后一行/最后一项的 bottomMargin 依赖 已选数量 > 0）。
     * 刷新时机不变：依然是 post 到下一帧，跨过/点到的格子立即更新角标。
     */
    private fun updateSelectDataUI(affectedPaths: Set<String>? = null, marginChanged: Boolean = false) {
        binding.recyclerView.post {
            val adapter = binding.recyclerView.bindingAdapter
            // models 下标 → adapter position 差一个相机 header（没挂 header 时是 0）
            val headerCount = adapter.headerCount
            val entityList = binding.recyclerView.models as? List<MediaEntity>
            if (entityList == null) {
                // 拿不到当前列表时，保持原有全量刷新行为（范围放到 adapter 全长，把 header 也带上）
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
                return@post
            }
            if (entityList.isEmpty()) {
                return@post
            }
            // 全部长度都用 adapter 坐标（含相机 header），行首才对应真实视觉行，
            // 否则会漏刷/多刷一行 —— 原因与网格 onBind 里 lastRowStart 的注释同源
            val total = adapter.itemCount
            val targets = affectedPaths ?: viewModel.getBadgedPathSet()
            // 最后一行（网格）/最后一项（线性）的 bottomMargin 依赖 已选数量 > 0，数量跨过 0 时需要同步
            var marginStart = total
            if (marginChanged) {
                marginStart = if (isGridMode) {
                    val remainder = total % 4
                    total - if (remainder == 0) 4 else remainder
                } else {
                    total - 1
                }
            }
            if (targets.isEmpty() && marginStart >= total) {
                return@post
            }
            if (targets.isNotEmpty()) {
                for (position in entityList.indices) {
                    val path = entityList[position].path
                    if (path != null && path in targets) {
                        adapter.notifyItemChanged(position + headerCount)
                    }
                }
            }
            if (marginStart < total) {
                adapter.notifyItemRangeChanged(marginStart, total - marginStart)
            }
        }
    }

    // ------------------------------------------------------------------
    // 拍照 / 录像入口
    // ------------------------------------------------------------------

    /**
     * 挂载 / 更新 / 摘除网格第一格的相机入口。
     *
     * 走 BRV 的 header 通道：它只是 adapter 里多出来的 position 0，models 本身不受影响，
     * 所以预览弹窗、滑动连选、选中角标那几套下标体系都不用动（详见 CameraEntryModel 的注释）。
     *
     * 类型取 currentTabType 而不是 selectType：有 tab 时入口要跟着 tab 走 ——
     * 在"图片"页给出录像入口属于骗用户（录出来的东西不会出现在这个页里）；
     * 没有 tab 时 currentTabType 恒等于 selectType，所以统一用它就对了。
     */
    private fun refreshCameraEntry() {
        if (!isGridMode) {
            return
        }
        val adapter = binding.recyclerView.bindingAdapter
        val model = viewModel.cameraModesFor(currentTabType)
        if (model == null) {
            if (isCameraEntryAttached) {
                adapter.removeHeaderAt(0)
                isCameraEntryAttached = false
            }
            return
        }
        if (isCameraEntryAttached) {
            // 同一个 viewType 只换数据；headers 的 setter 内部会 notifyDataSetChanged
            adapter.headers = listOf(model)
        } else {
            // addHeader 是 BindingAdapter 的成员（BRV 没提供 RecyclerView 侧的扩展函数）
            adapter.addHeader(model)
            isCameraEntryAttached = true
        }
    }

    /** 宿主配置了"进入即拍照/录像"：页面准备好后直接拉起系统相机（只触发一次） */
    private fun maybeStartCameraOnLaunch() {
        if (autoCameraStarted) return
        if (!viewModel.openCameraOnStart.value) return
        autoCameraStarted = true
        val isVideo = viewModel.selectType.value == FilePickerSelectType.VIDEO
        FilePickerLog.d("FilePickerFragment", "进入即拍照: isVideo=$isVideo")
        view?.postDelayed({
            if (!isAdded || isDetached) return@postDelayed
            startCapture(isVideo)
        }, 250)
    }

    private fun startCapture(isVideo: Boolean) {
        // 已经有一次拍摄在途（相机 App 还没起来又点了一下）：忽略，
        // 否则会造出第二条 pending 记录，而回执只能认领一条，另一条就永远留在相册里了
        if (pendingCapture != null) {
            return
        }
        pendingCaptureIsVideo = isVideo
        if (needRequestCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        launchSystemCamera(isVideo)
    }

    /**
     * 宿主 App 声明了 CAMERA 却未授权时，ACTION_IMAGE_CAPTURE 会直接抛 SecurityException
     * （官方 javadoc 明写）。库自己不声明这个权限，所以只在宿主声明过的前提下才去申请。
     */
    private fun needRequestCameraPermission(): Boolean {
        val ctx = context ?: return false
        if (!isPermissionDeclared(ctx, Manifest.permission.CAMERA)) {
            return false
        }
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
    }

    private fun isPermissionDeclared(context: Context, permission: String): Boolean {
        return try {
            val info = context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_PERMISSIONS
            )
            info.requestedPermissions?.contains(permission) == true
        } catch (e: Exception) {
            // 查不到就当没声明：顶多失败时弹个 toast，好过每次点相机都弹权限框
            false
        }
    }

    private fun launchSystemCamera(isVideo: Boolean) {
        val ctx = context ?: return
        val capture = if (isVideo) {
            FilePickerCaptureHelper.createVideoOutput(ctx)
        } else {
            FilePickerCaptureHelper.createImageOutput(ctx)
        }
        if (capture == null) {
            // 宿主在 pre-Q 没给 WRITE_EXTERNAL_STORAGE，或 MediaProvider 拒绝写入
            Toast.makeText(ctx, viewModel.uiConfig.cameraEntryFailedToastContent, Toast.LENGTH_SHORT).show()
            return
        }
        pendingCapture = capture
        try {
            captureLauncher.launch(FilePickerCaptureHelper.buildCaptureIntent(capture))
        } catch (e: Exception) {
            // 设备上没有能响应拍照/录像的相机 App（ActivityNotFoundException），
            // 或宿主声明了 CAMERA 但被用户拒绝（SecurityException）
            FilePickerLog.e("FilePickerFragment 启动系统相机失败: ${e.message}")
            FilePickerCaptureHelper.discard(ctx, capture)
            pendingCapture = null
            Toast.makeText(ctx, viewModel.uiConfig.cameraEntryFailedToastContent, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onCaptureResult(resultCode: Int, data: Intent?) {
        val capture = pendingCapture ?: return
        pendingCapture = null
        val ctx = context ?: return

        if (resultCode != RESULT_OK) {
            // 用户在相机里退出了。这里**不能**无条件清理：部分 ROM / 三方相机会先写盘再回
            // RESULT_CANCELED，而 Q+ 上的 delete() 会把文件本体一起删掉。
            // discard 内部已经有"有内容就不删、只落定"的兜底，所以直接交给它。
            FilePickerCaptureHelper.discard(ctx, capture)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            var uri = FilePickerCaptureHelper.finalizeCapture(ctx, capture, data)
            var entity = FileQueryHelper.queryEntityByUri(ctx, uri)

            if (!isUsableCapture(ctx, uri, entity)) {
                // 最后一道兜底：个别老 ROM / 三方相机既不理 EXTRA_OUTPUT、也不回 data.data，
                // 只在回执里塞一张缩略图 Bitmap。这时把那张图存下来，好过用户拍完什么都没有。
                // 拿不到 bitmap 时返回 null，下面继续按"没产出内容"处理。
                val fallback = FilePickerCaptureHelper.saveThumbnailFallback(ctx, data)
                if (fallback != null) {
                    uri = fallback
                    entity = FileQueryHelper.queryEntityByUri(ctx, fallback)
                }
            }

            if (entity == null) {
                // 查不到 ≠ 没拍到：列缺失、竞态、provider 拒绝都会返回 null。
                // 所以这条路径只放弃回填，绝不删任何东西（删错了就是用户永久丢照片）。
                FilePickerLog.e("FilePickerFragment 拍摄产物查询失败，放弃回填: uri=$uri")
                return@launch
            }
            if (!isUsableCapture(ctx, uri, entity)) {
                // 确认确实是空的：这次没有产出内容，清掉我们建的那条记录/空文件
                FilePickerLog.e("FilePickerFragment 相机未产出有效内容: uri=$uri")
                if (uri == capture.outputUri) {
                    FilePickerCaptureHelper.discard(ctx, capture)
                }
                return@launch
            }
            // 插到列表最前面
            if (!viewModel.insertCapturedMedia(entity)) {
                // 唯一原因是大小不符合 minFileSize / maxFileSize（口径与查询侧一致）。
                // 不插、不勾，但必须告诉用户一声 —— 否则"拍完回来什么都没有"会被当成 bug
                Toast.makeText(ctx, viewModel.uiConfig.cameraEntrySizeLimitToastContent, Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 单击立即返回模式（maxSelectNumber==1 && setSingleClickEnable(true)）：
            // 拍到的这张就是结果 —— 直接替换掉已有选择并返回，跟"点一下格子就返回"完全一致。
            //
            // 这里**刻意放在上限判断之前**：宿主可能通过 setSelectedList 传进来一张已选的，
            // 此时"已到上限"是必然成立的。要是先判上限，用户就会卡在
            // "选了旧的 → 拍完新的 → 弹已达上限" 的死角里，拍的照片反而用不上。
            // 单击模式的结果恒为 1 张，本来也不存在超上限的问题。
            if (viewModel.isCanSingleClickSelect()) {
                // 头像裁剪模式：拍到的这张也要进 1:1 裁剪页，而不是直接返回原图
                if (viewModel.cropAspect.value > 0f && entity.isImage()) {
                    launchAvatarCrop(entity)
                } else if (viewModel.editOnClick.value && entity.isImage()) {
                    launchEditDialogForSingleClick(entity)
                } else {
                    callbackToChooser(arrayListOf(entity))
                }
                return@launch
            }

            // 普通模式：插进来之后默认"这张就是要用的"，顺手勾上；
            // 但**已经到上限时不能勾** —— 否则会超出 maxSelectNumber
            val countBefore = viewModel.getSelectedCount() + viewModel.tempSelectData.size
            val captureCheck = viewModel.canSelect(entity)
            if (captureCheck !is SelectCheck.Allowed) {
                toastSelectCheck(captureCheck)
                return@launch
            }
            viewModel.takeSelectionSnapshot()
            viewModel.addSelectedData(entity)
            // 角标只变了新增这一条；已选数量从 0 变成 1 时最后一行/最后一项的 bottomMargin 也要跟着刷
            updateSelectDataUI(viewModel.getSelectionChangedPaths(), countBefore == 0)
            updateBottomMenuSelectNumberUI()
        }
    }

    /**
     * 这次拍摄到底有没有产出可用内容。
     *
     * 不能只看 MediaStore 的 SIZE：我们建那条记录时文件还是 0 字节（pre-Q 先建空文件再 insert，
     * Q+ 的 IS_PENDING 期间元数据由延迟扫描回填），相机写完盘之后 SIZE 什么时候回填并不保证。
     * 所以 SIZE 为 0 时再真读一个字节确认 —— 读到了就说明只是 SIZE 没跟上，内容是在的。
     */
    private fun isUsableCapture(context: Context, uri: Uri, entity: MediaEntity?): Boolean {
        if (entity == null) return false
        if (entity.size > 0L) return true
        return FilePickerCaptureHelper.hasContent(context, uri)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 拍摄途中宿主可能被回收（相机 App 吃内存时很常见），回来后要靠这些认领回执，
        // 顺带保证那条 pending 记录一定会被落定或清理
        pendingCapture?.let { capture ->
            outState.putString(STATE_CAPTURE_URI, capture.outputUri.toString())
            outState.putBoolean(STATE_CAPTURE_IS_VIDEO, capture.isVideo)
            outState.putString(STATE_CAPTURE_PRE_Q_FILE, capture.preQFile?.absolutePath)
        }
    }

    private fun restorePendingCapture(savedInstanceState: Bundle?) {
        val uri = savedInstanceState?.getString(STATE_CAPTURE_URI) ?: return
        pendingCapture = FilePickerCaptureHelper.PendingCapture(
            outputUri = Uri.parse(uri),
            isVideo = savedInstanceState.getBoolean(STATE_CAPTURE_IS_VIDEO, false),
            preQFile = savedInstanceState.getString(STATE_CAPTURE_PRE_Q_FILE)?.let { File(it) },
        )
    }

    var isFirstResume = true

    override fun onResume() {
        super.onResume()
        if (isFirstResume) {
            isFirstResume = false
            return
        }
        // 性能优化：媒体库已完整加载过时不再全量重扫，
        // 避免从预览/视频页/后台返回时重复查询 MediaStore 引发卡顿重刷
        if (viewModel.isAllDataLoaded.value) {
            FilePickerLog.d("FilePickerFragment", "onResume: isAllDataLoaded=true, skip rescan")
            return
        }
        FilePickerLog.d("FilePickerFragment", "onResume: isFirstResume:$isFirstResume")
        loadData()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()

    }

    override fun onClick(v: View?) {
        when (v?.id) {
            binding.tvAlbum.id, binding.ivArrowDown.id -> {
                FolderChooseDialog(
                    mCtx = requireContext(),
                    list = viewModel.getAllDataList(),
                    currentFolder = viewModel.currentFolder.value,
                    uiConfig = viewModel.uiConfig
                ) { folder ->
                    viewModel.updateCurrentFolder(folder)
                    binding.tvAlbum.text = folder?.name ?: viewModel.uiConfig.allAlbumName
                    resetListDataWithSelectData()
                }.setOnDismissListener(object : BasePopupWindow.OnDismissListener() {
                    override fun onDismiss() {
                        binding.ivArrowDown.animate().rotation(0f).setDuration(200).start()
                    }
                }).showPopupWindow(binding.tvAlbum)
                binding.ivArrowDown.animate().rotation(180f).setDuration(200).start()
            }
        }
    }


    override fun onDestroy() {
        viewModel.onDestroy()
        super.onDestroy()
    }
}