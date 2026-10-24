package com.pichs.filepicker.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.pichs.filepicker.FilePickerViewModel
import com.pichs.filepicker.R
import com.pichs.filepicker.databinding.DialogFilePickerPreviewBinding
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.photoview.PhotoView
import com.pichs.filepicker.video.FilePickerVideoPlayerView
import razerdp.basepopup.BasePopupWindow
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.ItemTouchHelper
import com.drake.brv.utils.bindingAdapter
import com.drake.brv.utils.linear
import com.drake.brv.utils.models
import com.drake.brv.utils.setup
import com.pichs.filepicker.SelectTypeUtil
import com.pichs.filepicker.databinding.FilePickerItemRvAlbumSelectedBinding
import com.pichs.filepicker.loader.MediaLoader
import com.pichs.filepicker.utils.FilePickerClickHelper
import com.pichs.filepicker.utils.FilePickerLog
import com.pichs.filepicker.utils.FilePickerTimeFormatUtils
import com.pichs.filepicker.widget.OnFilePickerDragItemTouchHelperCallback
import com.pichs.filepicker.widget.SelectedThumbnailStrip
import com.pichs.filepicker.widget.StripMetrics
import com.pichs.filepicker.select.QuotaType
import com.pichs.filepicker.select.SelectCheck
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@UnstableApi
class FilePickerPreviewDialog(
    val context: Context,
    val viewModel: FilePickerViewModel,
    val curItem: MediaEntity,
    val onSelect: (MediaEntity, Boolean, Int) -> Unit,
    val onDragEnd: () -> Unit,
    var onSelectListScrollChanged: (index: Int, type: Int, dx: Int) -> Unit,
    val onConfirm: (Int) -> Unit
) : BasePopupWindow(context) {

    lateinit var binding: DialogFilePickerPreviewBinding

    private var isShowToolBar = MutableStateFlow(true)

    private var mCurrentItem: MediaEntity? = null
    private var mCurrentIndex: Int = 0

    /** 底部缩略图间距（按"一屏 5 个半"算出来），null = 还没测量/保持原样 */
    private var stripLayout: StripMetrics.Layout? = null

    init {
        setContentView(R.layout.dialog_file_picker_preview)
    }

    private var job: Job? = null

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(contentView: View) {
        super.onViewCreated(contentView)
        binding = DialogFilePickerPreviewBinding.bind(contentView)
        setBackgroundColor(Color.TRANSPARENT)
        mCurrentItem = curItem
        mCurrentIndex = resolveCurrentIndex()
        initConfigUI()
        initViewPager2()
        initSelectedRecyclerView()
        initListener()
        updateBottomMenuSelectNumberUI()

        job = viewModel.viewModelScope.launch {
            launch {
                viewModel.currentFolderDataList.collectLatest {
                    FilePickerLog.d("FilePickerFragment", "initDataFlow currentFolderDataList: size:${it.size}")
                    // 该列表会被 fast→full 两次扫描整体替换，indexOf 可能返回 -1。
                    // 绝不能把 -1 交给 setCurrentItem：ViewPager2 内部会 Math.max(item, 0)
                    // 静默夹到第 0 页，表现就是"点的是这张、显示的是第一张"。
                    val index = resolveCurrentIndex(binding.viewPager2.currentItem)

                    binding.viewPager2.adapter?.notifyDataSetChanged()

                    binding.viewPager2.setCurrentItem(index, false)
                    updateIndexUI(mCurrentItem)
                    updateBottomMenuSelectNumberUI()
                }
            }

            launch {
                viewModel.originalCheckedFlow.collectLatest {
                    FilePickerLog.d("FilePickerPreviewDialog", "initDataFlow-----dialog originalCheckedFlow: isChecked:${it}")
                    binding.cboxOriginal.isChecked = it
                }
            }

            launch {
                isShowToolBar.collectLatest { isShow ->
                    FilePickerLog.d("FilePickerPreviewDialog", "initDataFlow-----dialog isShowToolBar: isShow:${isShow}")
//                    binding.statusBarView.isVisible = isShow
//                    binding.clToolbar.isVisible = isShow
//                    binding.rvSelected.isVisible = isShow
//                    binding.clBottomBar.isVisible = isShow
                    // 使用动画渐隐渐显。
                    if (isShow) {
                        binding.statusBarView.animate().alpha(1f).setDuration(300).start()
                        binding.clToolbar.animate().alpha(1f).setDuration(300).start()
                        binding.rvSelected.animate().alpha(1f).setDuration(300).start()
                        binding.clBottomBar.animate().alpha(1f).setDuration(300).start()
                    } else {
                        binding.statusBarView.animate().alpha(0f).setDuration(300).start()
                        binding.clToolbar.animate().alpha(0f).setDuration(300).start()
                        binding.rvSelected.animate().alpha(0f).setDuration(300).start()
                        binding.clBottomBar.animate().alpha(0f).setDuration(300).start()
                    }
                }
            }

            launch {
                viewModel.activityLifecycleChannel.consumeAsFlow().collectLatest {
                    if (it == Lifecycle.Event.ON_PAUSE) {
                        // 暂停播放
                        if (mCurrentIndex >= 0 && mCurrentIndex < viewModel.getCurrentFolderDataList().size) {
                            // 查找当前页面的 ViewHolder 并释放播放器
                            val recyclerView = binding.viewPager2.getChildAt(0) as RecyclerView
                            val viewHolder = recyclerView.findViewHolderForAdapterPosition(mCurrentIndex)
                            if (viewHolder is MediaPagerAdapter.VideoViewHolder) {
                                viewHolder.videoPlayerView.releasePlayer()
                            }
                        }
                    } else if (it == Lifecycle.Event.ON_RESUME) {
                        // 恢复播放
                        if (mCurrentIndex >= 0 && mCurrentIndex < viewModel.getCurrentFolderDataList().size) {
                            // 查找当前页面的 ViewHolder 并恢复播放器
                            val recyclerView = binding.viewPager2.getChildAt(0) as RecyclerView
                            val viewHolder = recyclerView.findViewHolderForAdapterPosition(mCurrentIndex)
                            if (viewHolder is MediaPagerAdapter.VideoViewHolder) {
                                viewHolder.videoPlayerView.loadCover(viewModel.getCurrentFolderDataList().getOrNull(mCurrentIndex))
                            }
                        }
                    }
                }
            }
        }

        if (viewModel.getSelectedCount() > 0) {
            binding.rvSelected.isVisible = true
        } else {
            binding.rvSelected.isVisible = false
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initConfigUI() {
        FilePickerClickHelper.clicks(binding.llOriginal) {
            viewModel.originalCheckedFlow.update { !viewModel.originalCheckedFlow.value }
        }

        if (viewModel.getSelectedCount() <= 0) {
//            binding.btnConfirm.isEnabled = false
            binding.btnConfirm.text = viewModel.uiConfig.confirmBtnText
        } else {
            binding.btnConfirm.isEnabled = true
            binding.btnConfirm.text = "${viewModel.uiConfig.confirmBtnText}(${viewModel.getSelectedCount()})"
        }

        binding.llOriginal.isVisible = viewModel.uiConfig.isShowOriginal && SelectTypeUtil.isCanOriginal(viewModel.selectType.value)

        binding.tvOriginal.text = viewModel.uiConfig.originalText
        binding.cboxOriginal.isChecked = viewModel.originalCheckedFlow.value
        binding.tvSelect.text = viewModel.uiConfig.previewSelectText

        if (viewModel.uiConfig.isPreviewPageIndexMode) {
            binding.tvSelectIndex.isVisible = true
            binding.ivSelectState.isVisible = false
        } else {
            binding.tvSelectIndex.isVisible = false
            binding.ivSelectState.isVisible = true
        }
    }


    private fun initSelectedRecyclerView() {
        binding.rvSelected.linear(RecyclerView.HORIZONTAL).setup {
            addType<MediaEntity>(R.layout.file_picker_item_rv_album_selected)

            onBind {
                val item = getModel<MediaEntity>()
                val itemBinding = getBinding<FilePickerItemRvAlbumSelectedBinding>()

                MediaLoader.loadThumbnail(item, itemBinding.ivCoverImage)

                itemBinding.clSelectDelete.isVisible = viewModel.uiConfig.isShowSelectedListDeleteIcon
                itemBinding.ivDelete.setImageResource(if (viewModel.uiConfig.selectedListDeleteIconResId != 0) viewModel.uiConfig.selectedListDeleteIconResId else R.drawable.filepicker_ic_delete_item)
                itemBinding.ivDelete.setBackgroundColor(viewModel.uiConfig.selectedListDeleteIconBackgroundColor)

                // 高亮按"当前页在已选列表里的下标"算，和整条重绘用同一口径
                itemBinding.ivCoverImage.isSelected = modelPosition == currentStripIndex()
                // 只改间距（格子尺寸和删除按钮都不动），让一屏露出 5 个半
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
                    viewModel.removeSelectedData(item)
                    binding.tvSelectIndex.text = ""
                    binding.tvSelectIndex.isChecked = false
                    binding.ivSelectState.isChecked = false
                    onSelect(item, false, itemIndexOfList(item))
                    updateBottomMenuSelectNumberUI()
                }

                itemBinding.root.setOnClickListener {
                    // 需要切换是当前 的viewpager2 的位置。
                    val indexOfList = itemIndexOfList(item)
                    if (indexOfList < 0) {
                        // 该项已不在当前预览列表里（列表被整体替换过）。
                        // setCurrentItem(-1) 会被 ViewPager2 夹到第 0 页造成错位，直接忽略这次切换。
                        FilePickerLog.d("FilePickerPreviewDialog", "选中项不在当前列表，忽略切换，path=${item.path}")
                        return@setOnClickListener
                    }
                    if (indexOfList != mCurrentIndex) {
                        // 高亮、页码、缩略图跟随全部交给 onPageSelected 统一处理，
                        // 避免这里和翻页回调各做一遍，滚动被触发两次。
                        binding.viewPager2.setCurrentItem(indexOfList, false)
                    } else {
                        SelectedThumbnailStrip.smoothScrollToItem(binding.rvSelected, modelPosition)
                    }
                }
            }
        }.models = viewModel.getSelectedDataList()

        OnFilePickerDragItemTouchHelperCallback(binding.rvSelected.bindingAdapter, viewModel, onDragEnd = {
            onDragEnd()
            updateIndexUI(mCurrentItem)
        }).let { callback ->
            ItemTouchHelper(callback).attachToRecyclerView(binding.rvSelected)
        }

        // 算间距（格子尺寸不动）：等 rv 测量完、子项绑出来之后再算，算不出就隔 80ms 重试
        binding.rvSelected.post { applyStripSpacing() }

        binding.rvSelected.post {
            val indexOfSelected = viewModel.getSelectedDataList().indexOf(mCurrentItem)
            if (indexOfSelected >= 0) {
                // 首帧直接到位，避免打开弹窗时先看到列表从头滚一遍
                binding.rvSelected.scrollToPosition(indexOfSelected)
            }
            refreshStripSelection()
        }

        binding.rvSelected.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // 只镜像用户手动拖动的位移。程序化平滑滚动已经通过 type=1 通知过目标下标，
                // 再逐帧镜像 dx 会让底部条被滚两次，看起来"越跑越偏"。
                if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    onSelectListScrollChanged(mCurrentIndex, 2, dx)
                }
            }
        })

    }

//    fun getScrollX(recyclerView: RecyclerView): Int {
//        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return 0
//        val firstVisible = layoutManager.findFirstVisibleItemPosition()
//        val firstView = layoutManager.findViewByPosition(firstVisible) ?: return 0
//        val itemWidth = firstView.width
//        val marginLeft = (firstView.layoutParams as? ViewGroup.MarginLayoutParams)?.leftMargin ?: 0
//        val scrollX = firstVisible * (itemWidth + marginLeft) - (firstView.left - marginLeft)
//        return scrollX.coerceAtLeast(0)
//    }

    fun itemIndexOfList(item: MediaEntity?): Int {
        if (item == null) return 0
        return viewModel.getCurrentFolderDataList().indexOf(item)
    }

    /**
     * 求一个一定合法的 ViewPager2 目标下标，并同步 mCurrentIndex / mCurrentItem。
     *
     * 为什么需要：itemIndexOfList 在"当前项已不在列表里"时返回 -1，而
     * ViewPager2.setCurrentItemInternal 里有 `item = Math.max(item, 0)`，
     * 负数会被静默夹到第 0 页 —— 表现就是点开第 k 张却显示第 1 张。
     * 这里找不到就退回 [fallback]（一般是 ViewPager 当前页），并让 mCurrentItem
     * 跟随实际显示的那一项，保证图片、页码、角标三者自洽。
     */
    private fun resolveCurrentIndex(fallback: Int = 0): Int {
        val list = viewModel.getCurrentFolderDataList()
        val found = itemIndexOfList(mCurrentItem)
        val index = PreviewIndexResolver.resolve(found, list.size, fallback)
        if (!PreviewIndexResolver.isFound(found, list.size) && list.isNotEmpty()) {
            mCurrentItem = list[index]
            FilePickerLog.d("FilePickerPreviewDialog", "当前项不在列表中，回退到下标 $index (size=${list.size})")
        }
        mCurrentIndex = index
        return index
    }

    @SuppressLint("SetTextI18n")
    private fun initListener() {
        FilePickerClickHelper.clicks(binding.ivBack) {
            dismiss()
        }

        FilePickerClickHelper.clicks(binding.btnConfirm) {
            // 必选条件没满足时拦住：预览页的"确定"不能绕过规则。
            // 一个都没选时外层会拿"当前页"当结果，所以要把当前项作为候选一起校验。
            val candidate = if (viewModel.getMergedSelectedCount() > 0) {
                null
            } else {
                listOfNotNull(viewModel.getCurrentFolderDataList().getOrNull(mCurrentIndex))
            }
            val notSatisfiedMessage = viewModel.selectRulesNotSatisfiedMessage(candidate)
            if (notSatisfiedMessage != null) {
                Toast.makeText(context, notSatisfiedMessage, Toast.LENGTH_SHORT).show()
                FilePickerLog.d("FilePickerPreviewDialog", "必选未满足：$notSatisfiedMessage")
                return@clicks
            }
            onConfirm(mCurrentIndex)
        }

        FilePickerClickHelper.clicks(binding.flSelectIndex) {
            val item = viewModel.getCurrentFolderDataByPosition(mCurrentIndex)
            if (item == null) return@clicks

            val indexOfSelect = viewModel.indexOfSelected(item)

            if (indexOfSelect == -1) {
                // 未选中，添加到选中列表
                val selectCheck = viewModel.canSelect(item)
                if (selectCheck is SelectCheck.Rejected) {
                    val message = viewModel.selectRejectedMessage(selectCheck)
                        ?: viewModel.uiConfig.selectMaxNumberOverToastContent
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    updateBottomMenuSelectNumberUI()
                    return@clicks
                }
                // 进行选中
                viewModel.addSelectedData(item)
                binding.ivSelectState.isChecked = true
                updateBottomMenuSelectNumberUI()
                val indexNow = viewModel.indexOfSelected(item)
                binding.tvSelectIndex.text = "${indexNow + 1}"
                binding.tvSelectIndex.isChecked = true
                onSelect(item, true, mCurrentIndex)
            } else {
                viewModel.removeSelectedData(item)
                binding.tvSelectIndex.text = ""
                binding.tvSelectIndex.isChecked = false
                binding.ivSelectState.isChecked = false
                updateBottomMenuSelectNumberUI()
                onSelect(item, false, mCurrentIndex)


            }
        }
    }

    /** 最普通的平滑滚动：交给 RecyclerView 自己算，不手动计算坐标 */
    fun scrollItemToCenter(recyclerView: RecyclerView, position: Int) {
        if (position < 0) return
        recyclerView.smoothScrollToPosition(position)
    }

    override fun onDismiss() {
        super.onDismiss()
        FilePickerLog.d("FilePickerPreviewDialog", "onDismiss: releasing player for position=$mCurrentIndex")
        // 释放当前页面的播放器

        if (mCurrentIndex >= 0 && mCurrentIndex < viewModel.getCurrentFolderDataList().size) {
            // 释放上一个页面的播放器
            FilePickerLog.d("FilePickerPreviewDialog", "onPageSelected: releasing player for position=$mCurrentIndex")
//                // 查找上一个页面的 ViewHolder 并释放播放器
            val recyclerView = binding.viewPager2.getChildAt(0) as RecyclerView
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(mCurrentIndex)
            if (viewHolder is MediaPagerAdapter.VideoViewHolder) {
                viewHolder.videoPlayerView.releasePlayer()
            }
        }

        job?.cancel()
        job = null
    }

    @SuppressLint("SetTextI18n")
    private fun initViewPager2() {
        // ViewPager2 预览
        binding.viewPager2.adapter = MediaPagerAdapter(context)
        // 预加载左右各一页：滑动开始时相邻页的大图已经在解，不会等到停在那一页才加载
        binding.viewPager2.offscreenPageLimit = 1

        binding.viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            @SuppressLint("NotifyDataSetChanged")
            override fun onPageSelected(position: Int) {
                if (mCurrentIndex == position) {
                    return
                }

                if (mCurrentIndex >= 0 && mCurrentIndex < viewModel.getCurrentFolderDataList().size) {
                    // 释放上一个页面的播放器
                    FilePickerLog.d("FilePickerPreviewDialog", "onPageSelected: releasing player for position=$mCurrentIndex")
//                // 查找上一个页面的 ViewHolder 并释放播放器
                    val recyclerView = binding.viewPager2.getChildAt(0) as RecyclerView
                    val viewHolder = recyclerView.findViewHolderForAdapterPosition(mCurrentIndex)
                    if (viewHolder is MediaPagerAdapter.VideoViewHolder) {
                        viewHolder.videoPlayerView.releasePlayer()
                    }
                }
                FilePickerLog.d("FilePickerPreviewDialog", "onPageSelected: position=$position, mCurrentIndex=$mCurrentIndex")
                val item = viewModel.getCurrentFolderDataByPosition(position) ?: return
                mCurrentItem = item
                // updateIndexUI 内部会整条重绘底部缩略图的绿框
                updateIndexUI(item)
                mCurrentIndex = position

                binding.rvSelected.post {
                    val indexOfSelect = viewModel.getSelectedDataList().indexOf(item)
                    if (indexOfSelect >= 0) {
                        // 最普通的做法：交给 RecyclerView 自己平滑滚到对应位置
                        SelectedThumbnailStrip.smoothScrollToItem(binding.rvSelected, indexOfSelect)
                    }
                    onSelectListScrollChanged(indexOfSelect, 1, 0)
                }
            }
        })

        val index = resolveCurrentIndex(binding.viewPager2.currentItem)

        binding.viewPager2.setCurrentItem(index, false)

        updateIndexUI(mCurrentItem)
    }


    @SuppressLint("SetTextI18n")
    private fun updateIndexUI(item: MediaEntity?) {
        if (item == null) {
            return
        }
        val size = viewModel.getCurrentFolderDataList().size
        // 下标为 -1 时退回 mCurrentIndex，避免出现 "0/N" 这种"看不到第几张"的显示
        val index = itemIndexOfList(item).let { if (it >= 0) it else mCurrentIndex }
        binding.tvIndex.text = if (size > 0) "${index + 1}/$size" else "0/0"
        // 这里需要根据是否选中来右上角角标。
        val indexOfSelect = viewModel.indexOfSelected(item)
        if (indexOfSelect == -1) {
            // 未选中
            binding.ivSelectState.isChecked = false
            binding.tvSelectIndex.text = ""
            binding.tvSelectIndex.isChecked = false
        } else {
            // 已选中
            binding.tvSelectIndex.text = "${indexOfSelect + 1}"
            binding.tvSelectIndex.isChecked = true
            binding.ivSelectState.isChecked = true
        }
        // 整条重绘底部缩略图的"当前页"绿框
        refreshStripSelection()
    }


    /**
     * 按"边缘露出半格"算一次底部条间距并应用。
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
            "FilePickerPreviewDialog",
            "缩略图条：可视宽=${rv.width} 格子宽=$itemWidth 算出=$layout 当前=$stripLayout"
        )
        if (layout != null && layout != stripLayout) {
            stripLayout = layout
            rv.bindingAdapter?.notifyDataSetChanged()
        }
    }

    /** 当前预览项在"已选列表"里的下标；不在已选里返回 -1 */
    private fun currentStripIndex(): Int = viewModel.getSelectedDataList().indexOf(mCurrentItem)

    /**
     * 整条重绘底部缩略图的"当前页"绿框：只有下标对得上的那格亮。
     *
     * 以前是"记住上一项、只清它一个"，一旦 ViewHolder 被复用就会把绿框留在别的格子上；
     * 现在每次按 adapter position 全量刷可见格子，值没变时 XCardImageView 内部直接 return，开销可忽略。
     */
    private fun refreshStripSelection() {
        if (!::binding.isInitialized) return
        SelectedThumbnailStrip.paintCurrent(binding.rvSelected, currentStripIndex())
    }

    @SuppressLint("SetTextI18n")
    private fun updateBottomMenuSelectNumberUI() {
        val selectedMergeSize = viewModel.getSelectedCount() + viewModel.tempSelectData.size
        if (selectedMergeSize > 0) {
            binding.rvSelected.isVisible = true
            binding.btnConfirm.isEnabled = true
            // 强制数量没选够时不高亮（仍可点击，点了给提示）
            binding.btnConfirm.alpha = if (viewModel.selectRulesSatisfied()) 1f else 0.4f
            binding.btnConfirm.text = "${viewModel.uiConfig.confirmBtnText}(${selectedMergeSize})"
            // 内容没变就不重置 models（重置会整条重新绑定 + 跳回最左）；
            // 真变了也先保留原来的滚动锚点，再让当前缩略图平滑跟进。
            if (SelectedThumbnailStrip.syncModels(binding.rvSelected, viewModel.getSelectedDataList())) {
                val index = viewModel.getSelectedDataList().indexOf(mCurrentItem)
                if (index >= 0) {
                    binding.rvSelected.post { SelectedThumbnailStrip.smoothScrollToItem(binding.rvSelected, index) }
                }
                refreshStripSelection()
            }
        } else {
            binding.rvSelected.isVisible = false
            SelectedThumbnailStrip.syncModels(binding.rvSelected, emptyList())
            SelectedThumbnailStrip.paintCurrent(binding.rvSelected, -1)
//            binding.btnConfirm.isEnabled = false
            binding.btnConfirm.text = viewModel.uiConfig.confirmBtnText
        }
    }

    companion object {
        const val TYPE_IMAGE = 0
        const val TYPE_VIDEO = 1
    }

    @UnstableApi
    inner class MediaPagerAdapter(
        private val context: Context,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return if (viewModel.getCurrentFolderDataList().getOrNull(position)?.isVideo() == true) {
                TYPE_VIDEO
            } else {
                TYPE_IMAGE
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_IMAGE) {
                val photoView = PhotoView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                photoView.setBackgroundColor(Color.BLACK)
                photoView.setOnClickListener {
                    isShowToolBar.update { isShowToolBar.value.not() }
                }
                ImageViewHolder(photoView)
            } else {
                val playView = FilePickerVideoPlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                /**
                 * 点击rootView事件
                 */
                playView.setOnSingleClickListener {
                    isShowToolBar.update { isShowToolBar.value.not() }
                }
                VideoViewHolder(playView)
            }
        }

        override fun getItemCount() = viewModel.getCurrentFolderDataList().size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = viewModel.getCurrentFolderDataList().getOrNull(position) ?: return
            if (holder is ImageViewHolder) {
                MediaLoader.loadImage(item, holder.photoView)
//                Glide.with(context).load(item.path).into(holder.photoView)
            } else if (holder is VideoViewHolder) {
                FilePickerLog.d("MediaPagerAdapter", "onBindViewHolder: item.path=${item.path}, item.uri=${item.uri}")
                holder.videoPlayerView.loadCover(item)
            }
        }

        override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
            super.onViewDetachedFromWindow(holder)
            FilePickerLog.d("MediaPagerAdapter", "onViewDetachedFromWindow11111:positon${holder.absoluteAdapterPosition} ==========")
            if (holder is VideoViewHolder) {
                holder.videoPlayerView.releasePlayer()
            }
        }

        override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
            super.onViewAttachedToWindow(holder)
            FilePickerLog.d("MediaPagerAdapter", "onViewAttachedToWindow22222: position=${holder.absoluteAdapterPosition} ==========")
            if (holder is VideoViewHolder) {
                // 这里可以根据需要重新加载视频封面或播放器
                holder.videoPlayerView.loadCover(viewModel.getCurrentFolderDataList().getOrNull(holder.absoluteAdapterPosition))
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            super.onViewRecycled(holder)
            if (holder is VideoViewHolder) {
                holder.videoPlayerView.releasePlayer()
            }
        }

        inner class ImageViewHolder(val photoView: PhotoView) : RecyclerView.ViewHolder(photoView)

        @UnstableApi
        inner class VideoViewHolder(val videoPlayerView: FilePickerVideoPlayerView) : RecyclerView.ViewHolder(videoPlayerView)
    }
}
