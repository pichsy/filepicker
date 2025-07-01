package com.pichs.filepicker

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.drake.brv.utils.bindingAdapter
import com.drake.brv.utils.grid
import com.drake.brv.utils.models
import com.drake.brv.utils.setup
import com.pichs.filepicker.databinding.FilePickerItemRvAlbumBinding
import com.pichs.filepicker.databinding.FragmentFilepickerHomeBinding
import com.pichs.filepicker.dialog.FilePickerFinalPreviewDialog
import com.pichs.filepicker.dialog.FilePickerPreviewDialog
import com.pichs.filepicker.dialog.FolderChooseDialog
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.entity.MediaFolder
import com.pichs.filepicker.loader.MediaLoader
import com.pichs.filepicker.scanner.MediaScanner
import com.pichs.filepicker.utils.FilePickerClickHelper
import com.pichs.filepicker.utils.FilePickerTimeFormatUtils
import com.pichs.filepicker.widget.OnItemSelectionChangedListener
import com.pichs.xwidget.utils.XDisplayHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import razerdp.basepopup.BasePopupWindow

class FilePickerFragment : Fragment(), View.OnClickListener {

    private val viewModel by activityViewModels<FilePickerViewModel>()

    private lateinit var binding: FragmentFilepickerHomeBinding

    private var currentTabType = TAB_TYPE_ALL

    private val screenWidth by lazy { XDisplayHelper.getScreenWidth(requireContext()) }

    private var isTouchSelectStart = false

    companion object {

        /**
         * 内部 tab 选择类型
         */
        const val TAB_TYPE_ALL = "all"
        const val TAB_TYPE_IMAGE = "image"
        const val TAB_TYPE_VIDEO = "video"

        /**
         * 选择类型 all, image, video
         */
        const val SELECT_TYPE_IMAGE = "image"
        const val SELECT_TYPE_VIDEO = "video"
        const val SELECT_TYPE_ALL = "all"

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

        if (currentTabType != TAB_TYPE_ALL) {
            // 隐藏掉tab切换。
            binding.llSelectType.isVisible = false
        }
        initConfigUI()

        initTab()
        initRecycler()
        initDataFlow()
        loadData()
        initListener()
    }


    private fun initListener() {
        FilePickerClickHelper.clicks(binding.llPreview) {
            // 预览按钮点击事件
            Log.d("FilePickerFragment", "Preview clicked, selectedDataList size: ${viewModel.getSelectedDataList().size}")
            if (viewModel.getSelectedDataList().isEmpty()) {
                Toast.makeText(requireContext(), viewModel.uiConfig.atLeastSelectOneToastContent, Toast.LENGTH_SHORT).show()
                return@clicks
            }

            // todo 进入 展示界面弹窗，这里仅展示固定个数，不参与展示。
            FilePickerFinalPreviewDialog(requireContext(), viewModel, onDismissDataDelete = { deleteList ->
                Toast.makeText(requireContext(), "删除了 ${deleteList.size} 个文件", Toast.LENGTH_SHORT).show()
            }, onConfirm = { size ->
                Toast.makeText(requireContext(), "确定了 ${size} 个", Toast.LENGTH_SHORT).show()
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
            binding.llPreview.isEnabled = false
            binding.tvPreview.isEnabled = false
        } else {
            binding.btnConfirm.isEnabled = true
            binding.llPreview.isEnabled = true
            binding.tvPreview.isEnabled = true
            binding.btnConfirm.text = "${viewModel.uiConfig.confirmBtnText}(${viewModel.getSelectedCount()})"
            binding.tvPreview.text = "${viewModel.uiConfig.previewText}(${viewModel.getSelectedCount()})"
        }

        binding.llOriginal.isVisible = viewModel.uiConfig.isShowOriginal
        binding.tvOriginal.text = viewModel.uiConfig.originalText
        binding.cboxOriginal.isChecked = viewModel.originalCheckedFlow.value
    }

    private fun initTab() {
        binding.ivBack.setOnClickListener {
            activity?.finish()
        }

        binding.tvAlbum.setOnClickListener(this)
        binding.ivArrowDown.setOnClickListener(this)

        binding.llTypeAll.setOnClickListener {
            if (currentTabType == TAB_TYPE_ALL) {
                return@setOnClickListener
            }
            currentTabType = TAB_TYPE_ALL
            resetListDataWithSelectData()
            selectAllTabUI()
        }

        binding.llTypeImage.setOnClickListener {
            if (currentTabType == TAB_TYPE_IMAGE) {
                return@setOnClickListener
            }
            currentTabType = TAB_TYPE_IMAGE
            resetListDataWithSelectData()
            selectImageTabUI()

        }

        binding.llTypeVideo.setOnClickListener {

            if (currentTabType == TAB_TYPE_VIDEO) {
                return@setOnClickListener
            }
            currentTabType = TAB_TYPE_VIDEO
            resetListDataWithSelectData()
            selectVideoTabUI()
        }

        binding.btnConfirm.setOnClickListener {
            Log.d("FilePickerFragment", "selectedData:${viewModel.getSelectedDataList().size},selectType:${viewModel.selectType.value}")
            callbackToChooser(ArrayList(viewModel.getSelectedDataList()))
        }

        updateBottomMenuSelectNumberUI()

        if (currentTabType == TAB_TYPE_ALL) {
            selectAllTabUI()
        }
    }

    private fun initDataFlow() {
        lifecycleScope.launch {
            launch {
                viewModel.currentFolderDataList.collectLatest { list ->
                    Log.d("FilePickerFragment", "initDataFlow currentFolderDataList: size:${list.size}, currentTabType:$currentTabType")
                    for (item in viewModel.selectedData) {
                        val isContains = viewModel.getAllDataEntityList().contains(item)
                        Log.d("FilePickerFragment", "initDataFlow: item:${item.path}, isContains:$isContains")
                        if (!isContains) {
                            viewModel.removeSelectedData(item)
                        }
                    }
                    // 这里处理数据
                    binding.recyclerView.models = list
                }
            }

            launch {
                viewModel.originalCheckedFlow.collectLatest {
                    Log.d("FilePickerFragment", "initDataFlow originalCheckedFlow: $it")
                    binding.cboxOriginal.isChecked = it
                }
            }
        }
    }

    private fun loadData() {
        Log.d("FilePickerFragment", "loadData: selectType:${viewModel.selectType.value}")
        MediaScanner.scanMedia(viewModel.selectType.value, this, object : MediaScanner.ScanCallback {
            override fun onCompleted(folders: List<MediaFolder>) {
                if (folders.isEmpty()) return
                // 处理folder列表，过滤所需
                val finalFolders = viewModel.filterAllData(folders)
                if (finalFolders.isEmpty()) return

                finalFolders.forEach {
                    Log.d("FilePickerFragment777", "mediaFolder: ${it.folderPath},==========================")
                    it.mediaEntityList.forEach {
                        Log.d("FilePickerFragment777", "mediaEntity: ${it.path}, size: ${it.size}")
                    }
                }

                viewModel.updateAllDataList(finalFolders)
                viewModel.initUserSelectDataList(finalFolders)

                resetListDataWithSelectData()
            }
        })
    }

    private fun resetListDataWithSelectData() {
        lifecycleScope.launch {
            when (currentTabType) {
                TAB_TYPE_ALL -> {
                    viewModel.updateCurrentFolderDataList(
                        if (viewModel.currentFolder.value != null) {
                            viewModel.currentFolder.value?.mediaEntityList ?: mutableListOf()
                        } else {
                            viewModel.getAllDataList().flatMap { it.mediaEntityList }.toMutableList()
                        }
                    )
                }

                TAB_TYPE_IMAGE -> {
                    viewModel.updateCurrentFolderDataList(
                        if (viewModel.currentFolder.value != null) {
                            viewModel.currentFolder.value?.mediaEntityList?.filter { it.isImage() }?.toMutableList() ?: mutableListOf()
                        } else {
                            viewModel.getAllDataList().flatMap { it.mediaEntityList.filter { it.isImage() } }.toMutableList()
                        }
                    )
                }

                TAB_TYPE_VIDEO -> {
                    viewModel.updateCurrentFolderDataList(
                        if (viewModel.currentFolder.value != null) {
                            viewModel.currentFolder.value?.mediaEntityList?.filter { it.isVideo() }?.toMutableList() ?: mutableListOf()
                        } else {
                            viewModel.getAllDataList().flatMap { it.mediaEntityList.filter { it.isVideo() } }.toMutableList()
                        }
                    )
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initRecycler() {
//        binding.tvMaxSelectNumber.text = "${viewModel.maxSelectNumber.value}"

        binding.recyclerView.itemAnimator = null
        binding.recyclerView.grid(4).setup {
            addType<MediaEntity>(R.layout.file_picker_item_rv_album)
            onBind {
                val item = getModel<MediaEntity>()
                val itemBinding = getBinding<FilePickerItemRvAlbumBinding>()

                itemBinding.clSelectArea.updateLayoutParams {
                    width = screenWidth / 7
                    height = width
                }

                MediaLoader.loadImage(item.uri, item.mimeType, itemBinding.ivCoverImage)

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

                if (indexOfSelect != -1) {
                    itemBinding.tvSelectIndex.text = "${indexOfSelect + 1}"
                    itemBinding.tvSelectIndex.setNormalBackgroundColor(ContextCompat.getColor(context, R.color.file_picker_index_bg_color))
                    itemBinding.ivCoverImage.foreground = ContextCompat.getDrawable(context, R.drawable.item_filepicker_select_mask)
                    itemBinding.root.isSelected = true
                } else {
                    itemBinding.tvSelectIndex.text = ""
                    itemBinding.tvSelectIndex.setNormalBackgroundColor(Color.TRANSPARENT)
                    itemBinding.ivCoverImage.foreground = null
                    itemBinding.root.isSelected = false
                }

                itemBinding.clSelectArea.setOnClickListener {
                    Log.d("FilePickerFragment", "item.path:${item.path},mimeType:${item.mimeType}")
                    if (viewModel.containsSelectedData(item)) {
                        viewModel.removeSelectedData(item)
                        notifyItemChanged(modelPosition)
                        updateBottomMenuSelectNumberUI()
                        // 更新角标
                        updateSelectDataUI()
                    } else {
                        if (isOverMaxSelectNumber(viewModel.getSelectedDataList().size + viewModel.tempSelectData.size)) {
                            Toast.makeText(requireContext(), viewModel.uiConfig.selectMaxNumberOverToastContent, Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        viewModel.addSelectedData(item)
                        notifyItemChanged(modelPosition)
                        updateBottomMenuSelectNumberUI()
                    }
                }
                itemBinding.ivCoverImage.setOnClickListener {
                    // 进入弹窗
                    showFilePickerPreviewDialog(item)
                }
            }
        }


        binding.recyclerView.maxSelectNumber = viewModel.maxSelectNumber.value
        binding.recyclerView.currentSelectedCountProvider = { viewModel.getSelectedCount() + viewModel.tempSelectData.size }

        binding.recyclerView.setOnItemSelectionChangedListener(object : OnItemSelectionChangedListener {

            override fun onItemSelectionChanged(startPosition: Int, currentPosition: Int, isSelected: Boolean) {
                Log.d("FilePickerFragment", "startPosition:$startPosition, currentPosition:$currentPosition, isSelected:$isSelected")
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

                Log.d("FilePickerFragment", "from:$from, to:$to, tempList.size:${tempList.size}, isSelected:$isSelected")
                if (isSelected) {
                    // 这里需要区分 viewModel.maxSelectNumber.value==0 的情况。
                    // 如果是选中模式，那么 经过的都要选中。
                    viewModel.tempSelectData.clear()
                    val list = tempList.filter { it !in viewModel.getSelectedDataList() }

                    if (viewModel.maxSelectNumber.value == 0 || viewModel.maxSelectNumber.value == Int.MAX_VALUE) {
                        // 如果没有限制选择数量，那么直接添加到临时选择数据中。
                        viewModel.tempSelectData.addAll(list)
                    } else {
                        val dx = list.size + viewModel.getSelectedCount() - viewModel.maxSelectNumber.value

                        Log.d(
                            "FilePickerFragment",
                            "dx:$dx, list.size:${list.size}, selectedData.size:${viewModel.getSelectedCount()}, ----x=${viewModel.maxSelectNumber.value - viewModel.getSelectedCount()}"
                        )

                        if (dx > 0) {
                            viewModel.tempSelectData.addAll(list.subList(0, list.size - dx))
                        } else {
                            viewModel.tempSelectData.addAll(list)
                        }
                    }
                } else {
                    // 如果是取消选中模式，那么经过的都要取消选中。
                    viewModel.tempSelectData.removeAll { it in tempList }
                    viewModel.removeSelectedDataAll(tempList)
                    // 这里需要刷新移除的 条目
                    // updateUnselectDataUI(tempList)
                }

                updateSelectDataUI()
                updateBottomMenuSelectNumberUI()
            }

            override fun onToucheSelectStart() {
                isTouchSelectStart = true
            }

            override fun onTouchSelectEnd() {
                isTouchSelectStart = false
                if (viewModel.tempSelectData.isNotEmpty()) {
                    // 如果临时选择数据不为空，那么就添加到已选择数据中。
                    viewModel.addSelectedDataList(viewModel.tempSelectData)
                    viewModel.tempSelectData.clear()
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


    /**
     * 展示文件预览对话框
     */
    @OptIn(UnstableApi::class)
    private fun showFilePickerPreviewDialog(item: MediaEntity) {
        FilePickerPreviewDialog(requireContext(), viewModel, item, onSelect = { item, isSelect, position ->
            // 选择
            Log.d("FilePickerFragment", "item.path:${item.path},isSelect:$isSelect")
            if (isSelect) {
//                if (isOverMaxSelectNumber(viewModel.getSelectedDataList().size + viewModel.tempSelectData.size)) {
//                    return@FilePickerPreviewDialog
//                }
                binding.recyclerView.bindingAdapter.notifyItemChanged(position)
            } else {
                // 更新角标
                updateSelectDataUI()
            }
            updateBottomMenuSelectNumberUI()
        }, onConfirm = { pos ->
            val finalList = ArrayList(viewModel.getSelectedDataList())
            // 确认
            if (finalList.isEmpty()) {
                viewModel.getCurrentFolderDataList().getOrNull(pos)?.let { item ->
                    finalList.add(item)
                }
            }
            Log.d("FilePickerFragment", "onConfirm: finalList=${finalList.size}")
            callbackToChooser(finalList)
        }).showPopupWindow()
    }

    /**
     * 选择数据返回
     */
    private fun callbackToChooser(selectList: ArrayList<MediaEntity>) {
        Log.d("FilePickerFragment", "1111callbackToChooser: selectList size:${selectList.size}, selectType:${viewModel.selectType.value}")
        // 这里可以回调到选择器，通知选择完成。
        if (selectList.isEmpty()) {
            Toast.makeText(context, viewModel.uiConfig.atLeastSelectOneToastContent, Toast.LENGTH_SHORT).show()
            return
        }
        activity?.apply {
            setResult(RESULT_OK, Intent().apply {
                putParcelableArrayListExtra("selectedDataList", selectList)
                putExtra("selectType", viewModel.selectType.value)
            })
            finish()
        }
    }

    fun isOverMaxSelectNumber(listSize: Int): Boolean {
        return viewModel.isOverMaxSelectNumber(listSize)
    }

    @SuppressLint("SetTextI18n")
    private fun updateBottomMenuSelectNumberUI() {
        val selectedMergeSize = viewModel.getSelectedCount() + viewModel.tempSelectData.size
        if (selectedMergeSize > 0) {
            binding.llPreview.isEnabled = true
            binding.tvPreview.isEnabled = true

            binding.btnConfirm.isEnabled = true

            binding.btnConfirm.text = "${viewModel.uiConfig.confirmBtnText}(${selectedMergeSize})"
            binding.tvPreview.text = "${viewModel.uiConfig.previewText}(${selectedMergeSize})"

        } else {
            binding.llPreview.isEnabled = false
            binding.tvPreview.isEnabled = false
            binding.btnConfirm.isEnabled = false

            binding.btnConfirm.text = viewModel.uiConfig.confirmBtnText
            binding.tvPreview.text = viewModel.uiConfig.previewText
        }
    }

    private fun selectAllTabUI() {
        binding.tvTypeImage.isChecked = false
        binding.tvTypeVideo.isChecked = false
        binding.tvTypeAll.isChecked = true
        binding.tvTypeImage.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
        binding.tvTypeVideo.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
        binding.tvTypeAll.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        binding.lineTypeAll.isInvisible = false
        binding.lineTypeImage.isInvisible = true
        binding.lineTypeVideo.isInvisible = true
    }

    private fun selectImageTabUI() {
        binding.tvTypeImage.isChecked = true
        binding.tvTypeVideo.isChecked = false
        binding.tvTypeAll.isChecked = false
        binding.tvTypeImage.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        binding.tvTypeVideo.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
        binding.tvTypeAll.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)

        binding.lineTypeAll.isInvisible = true
        binding.lineTypeImage.isInvisible = false
        binding.lineTypeVideo.isInvisible = true
    }

    private fun selectVideoTabUI() {
        binding.tvTypeImage.isChecked = false
        binding.tvTypeVideo.isChecked = true
        binding.tvTypeAll.isChecked = false
        binding.tvTypeImage.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
        binding.tvTypeVideo.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        binding.tvTypeAll.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)

        binding.lineTypeAll.isInvisible = true
        binding.lineTypeImage.isInvisible = true
        binding.lineTypeVideo.isInvisible = false
    }


    private fun updateUnselectDataUI(removedList: List<MediaEntity>) {
        // 更新未选择数据的UI
//        for (item in removedList) {
//            val index = viewModel.currentFolderDataList.value.indexOfFirst { it.path == item.path }
//            if (index != -1) {
//                binding.recyclerView.post {
//                    binding.recyclerView.bindingAdapter.notifyItemChanged(index)
//                }
//            }
//        }
    }

    /**
     * 更新选择数据的UI。 主要是角标。
     * 有优化点，就是 针对性刷新，
     * 移除的那些需要刷新，新增的也需要刷新。
     */
    private fun updateSelectDataUI() {
//        val combineData = viewModel.getSelectedDataList() + viewModel.tempSelectData
//        for (item in combineData) {
//            // 找到对应的item，更新角标
//            val index = viewModel.currentFolderDataList.value.indexOfFirst { it.path == item.path }
//            Log.d("FilePickerFragment", "updateSelectDataUI: item:${item.path}, index:$index")
//            if (index != -1) {
//                binding.recyclerView.post {
//                    binding.recyclerView.bindingAdapter.notifyItemChanged(index)
//                }
//            }
//        }
        binding.recyclerView.post {
            binding.recyclerView.bindingAdapter.notifyItemRangeChanged(0, viewModel.currentFolderDataList.value.size)
        }
//        for (item in combineData) {
//            // 找到对应的item，更新角标
//            val index = rvData.indexOfFirst { it == item }
//            Log.d("FilePickerFragment", "updateSelectDataUI: item:${item.path}, index:$index")
//
//        }
//        for (item in viewModel.tempSelectData) {
//            val index = rvData?.indexOfFirst { it == item } ?: -1
//            binding.recyclerView.post {
//                if (index != -1) {
//                    binding.recyclerView.bindingAdapter.notifyItemChanged(index)
//                }
//            }
//        }
    }

    var isFirstResume = true

    override fun onResume() {
        super.onResume()
        if (isFirstResume) {
            isFirstResume = false
            return
        }
        Log.d("FilePickerFragment", "onResume: isFirstResume:$isFirstResume")
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
                FolderChooseDialog(requireContext(), viewModel.getAllDataList(), viewModel.currentFolder.value) { folder ->
                    viewModel.updateCurrentFolder(folder)
                    binding.tvAlbum.text = folder?.name ?: "全部"
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
}