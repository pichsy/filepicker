package com.pichs.filepicker.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.pichs.filepicker.FilePickerViewModel
import com.pichs.filepicker.R
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.photoview.PhotoView
import com.pichs.filepicker.video.FilePickerVideoPlayerView
import razerdp.basepopup.BasePopupWindow
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.ItemTouchHelper
import com.drake.brv.BindingAdapter
import com.drake.brv.utils.bindingAdapter
import com.drake.brv.utils.linear
import com.drake.brv.utils.setup
import com.pichs.filepicker.SelectTypeUtil
import com.pichs.filepicker.databinding.DialogFilePickerSelectedPreviewBinding
import com.pichs.filepicker.databinding.FilePickerItemRvAlbumJustSelectedBinding
import com.pichs.filepicker.entity.FilePickerTempSelected
import com.pichs.filepicker.loader.MediaLoader
import com.pichs.filepicker.utils.FilePickerClickHelper
import com.pichs.filepicker.utils.FilePickerLog
import com.pichs.filepicker.utils.FilePickerTimeFormatUtils
import com.pichs.filepicker.widget.OnFilePickerDragItemTouchHelperCallback
import com.pichs.filepicker.widget.SelectedThumbnailStrip
import com.pichs.filepicker.widget.StripMetrics
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class FilePickerFinalPreviewDialog(
    val context: Context,
    val viewModel: FilePickerViewModel,
    val onDismissDataDelete: (deleteList: MutableList<MediaEntity>) -> Unit,
    val onDragEnd: () -> Unit,
    val onConfirm: (ArrayList<MediaEntity>) -> Unit,
    /** 编辑完成后通知外面刷新对应格子（网格缩略图 + 已编辑角标） */
    val onEditApplied: (MediaEntity) -> Unit = {},
) : BasePopupWindow(context) {

    lateinit var binding: DialogFilePickerSelectedPreviewBinding

    private var isShowToolBar = MutableStateFlow(true)

    private val tempSelectDataList = viewModel.getSelectedDataList().map { FilePickerTempSelected(false, it) }.toMutableList()

    private var mCurrentItem: FilePickerTempSelected? = null

    private var mCurrentIndex: Int = 0

    /** 底部缩略图间距（按"一屏 5 个半"算出来），null = 还没测量/保持原样 */
    private var stripLayout: StripMetrics.Layout? = null

    init {
        mCurrentItem = tempSelectDataList.firstOrNull()
        mCurrentIndex = 0
        setContentView(R.layout.dialog_file_picker_selected_preview)
    }

    private var job: Job? = null

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(contentView: View) {
        super.onViewCreated(contentView)
        binding = DialogFilePickerSelectedPreviewBinding.bind(contentView)
        setBackgroundColor(Color.TRANSPARENT)
        // 初始化其他的。
        initConfigUI()
        initViewPager2()
        initSelectedRecyclerView()
        initListener()
        updateIndexUI(mCurrentItem)
        updateBottomMenuSelectNumberUI()

        job = viewModel.viewModelScope.launch {
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
                        binding.statusBarView.animate().alpha(1f).setDuration(250).start()
                        binding.clToolbar.animate().alpha(1f).setDuration(250).start()
                        binding.rvSelected.animate().alpha(1f).setDuration(250).start()
                        binding.clBottomBar.animate().alpha(1f).setDuration(250).start()
                    } else {
                        binding.statusBarView.animate().alpha(0f).setDuration(250).start()
                        binding.clToolbar.animate().alpha(0f).setDuration(250).start()
                        binding.rvSelected.animate().alpha(0f).setDuration(250).start()
                        binding.clBottomBar.animate().alpha(0f).setDuration(250).start()
                    }
                }
            }

            launch {
                viewModel.activityLifecycleChannel.consumeAsFlow().collectLatest {
                    if (it == Lifecycle.Event.ON_PAUSE) {
                        // 暂停播放
                        if (mCurrentIndex >= 0 && mCurrentIndex < tempSelectDataList.size) {
                            // 查找当前页面的 ViewHolder 并释放播放器
                            val recyclerView = binding.viewPager2.getChildAt(0) as RecyclerView
                            val viewHolder = recyclerView.findViewHolderForAdapterPosition(mCurrentIndex)
                            if (viewHolder is FilePickerPreviewDialog.MediaPagerAdapter.VideoViewHolder) {
                                viewHolder.videoPlayerView.releasePlayer()
                            }
                        }
                    } else if (it == Lifecycle.Event.ON_RESUME) {
                        // 恢复播放
                        if (mCurrentIndex >= 0 && mCurrentIndex < tempSelectDataList.size) {
                            // 查找当前页面的 ViewHolder 并恢复播放器
                            val recyclerView = binding.viewPager2.getChildAt(0) as RecyclerView
                            val viewHolder = recyclerView.findViewHolderForAdapterPosition(mCurrentIndex)
                            if (viewHolder is FilePickerPreviewDialog.MediaPagerAdapter.VideoViewHolder) {
                                // 本弹窗的 ViewPager/Adapter 全部基于私有 tempSelectDataList，
                                // 不能用 viewModel.getCurrentFolderDataList()（另一个列表）按同一下标取值，
                                // 否则恢复前台后会把"别的视频"的封面贴到当前页上（错位）。
                                viewHolder.videoPlayerView.loadCover(tempSelectDataList.getOrNull(mCurrentIndex)?.mediaEntity)
                            }
                        }
                    }
                }
            }
        }

        if (tempSelectDataList.isNotEmpty()) {
            binding.rvSelected.isVisible = true
        } else {
            binding.rvSelected.isVisible = false
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initConfigUI() {

        if (tempSelectDataList.isEmpty()) {
//            binding.btnConfirm.isEnabled = false
            binding.btnConfirm.text = viewModel.uiConfig.confirmBtnText
        } else {
            binding.btnConfirm.isEnabled = true
            binding.btnConfirm.text = "${viewModel.uiConfig.confirmBtnText}(${tempSelectDataList.filter { !it.isDelete }.size})"
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
            addType<FilePickerTempSelected>(R.layout.file_picker_item_rv_album_just_selected)

            onBind {
                val item = getModel<FilePickerTempSelected>()
                val itemBinding = getBinding<FilePickerItemRvAlbumJustSelectedBinding>()

                val itemEntity = item.mediaEntity

                MediaLoader.loadThumbnail(viewModel.displayEntity(itemEntity), itemBinding.ivCoverImage)

                itemBinding.clSelectDelete.isVisible = viewModel.uiConfig.isShowSelectedListDeleteIcon
                itemBinding.ivDelete.setImageResource(if (viewModel.uiConfig.selectedListDeleteIconResId != 0) viewModel.uiConfig.selectedListDeleteIconResId else R.drawable.filepicker_ic_delete_item)
                itemBinding.ivDelete.setBackgroundColor(viewModel.uiConfig.selectedListDeleteIconBackgroundColor)

                // 高亮按"当前页在已选列表里的下标"算，和整条重绘用同一口径
                itemBinding.ivCoverImage.isSelected = modelPosition == currentStripIndex()
                // 只改间距（格子尺寸和删除按钮都不动），让一屏露出 5 个半
                SelectedThumbnailStrip.applyLayout(itemBinding.root, stripLayout)

                if (item.isDelete) {
                    itemBinding.ivCoverImage.foreground = ContextCompat.getDrawable(context, R.drawable.item_filepicker_delete_preview_select_mask)
                } else {
                    itemBinding.ivCoverImage.foreground = null
                }

                if (itemEntity.isVideo()) {
                    itemBinding.tvDuration.visibility = View.VISIBLE
                    itemBinding.tvDuration.text = FilePickerTimeFormatUtils.formatTimeMillSeconds(itemEntity.duration)
                } else if (itemEntity.isGif()) {
                    itemBinding.tvDuration.visibility = View.VISIBLE
                    itemBinding.tvDuration.text = "GIF"
                } else {
                    itemBinding.tvDuration.visibility = View.GONE
                    itemBinding.tvDuration.text = ""
                }

                itemBinding.clSelectDelete.setOnClickListener {
                    item.isDelete = true
                    // 删除选中项
                    binding.tvSelectIndex.text = ""
                    binding.tvSelectIndex.isChecked = false
                    binding.ivSelectState.isChecked = false
                    // 只给这一格贴遮罩，不 notifyItemChanged：
                    // 变更动画会把整张缩略图淡出再淡入，用户看到的就是闪一下。
                    applyDeleteMask(item, true)
                    updateBottomMenuSelectNumberUI()
                }

                itemBinding.root.setOnClickListener {
                    // 需要切换是当前 的viewpager2 的位置。
                    val indexOfList = itemIndexOfList(item)
                    if (indexOfList < 0) {
                        return@setOnClickListener
                    }
                    if (indexOfList != mCurrentIndex) {
                        // 高亮、页码、缩略图跟随统一交给 onPageSelected 处理，避免重复刷新。
                        binding.viewPager2.setCurrentItem(indexOfList, false)
                    } else {
                        SelectedThumbnailStrip.smoothScrollToItem(binding.rvSelected, modelPosition)
                    }
                }
            }
        }.models = tempSelectDataList

        OnFilePickerDragItemTouchHelperCallback(binding.rvSelected.bindingAdapter, viewModel, onDragEnd = {
            onDragEnd()
            updateIndexUI(mCurrentItem)
        }).let { callback ->
            ItemTouchHelper(callback).attachToRecyclerView(binding.rvSelected)
        }

        binding.rvSelected.post { applyStripSpacing() }
        binding.rvSelected.post {
            val index = tempSelectDataList.indexOf(mCurrentItem)
            if (index >= 0) binding.rvSelected.scrollToPosition(index)
            refreshStripSelection()
        }
    }

    fun itemIndexOfList(item: FilePickerTempSelected?): Int {
        if (item == null) return 0
        return tempSelectDataList.indexOf(item)
    }

    @SuppressLint("SetTextI18n")
    private fun initListener() {

        FilePickerClickHelper.clicks(binding.llOriginal) {
            viewModel.originalCheckedFlow.update { !viewModel.originalCheckedFlow.value }
        }

        FilePickerClickHelper.clicks(binding.ivBack) {
            dismiss()
        }

        // 和多选网格里的单图预览复用同一个编辑页
        FilePickerClickHelper.clicks(binding.tvEdit) {
            startEditForCurrentItem()
        }

        FilePickerClickHelper.clicks(binding.btnConfirm) {
//            这里可以回调到选择器，通知选择完成。
//            if (viewModel.getSelectedCount() <= 0 && viewModel.tempSelectData.isEmpty()) {
//                Toast.makeText(context, "至少选择一个", Toast.LENGTH_SHORT).show()
//                return@clicks
//            }
            val list = tempSelectDataList.filter { !it.isDelete }.map { it.mediaEntity }.toMutableList()
            if (list.isEmpty()) {
                mCurrentItem?.mediaEntity?.let {
                    list.add(it)
                }
            }
            onConfirm(ArrayList(list))
        }

        FilePickerClickHelper.clicks(binding.flSelectIndex) {
            val item = tempSelectDataList.getOrNull(mCurrentIndex)
            if (item == null) return@clicks

            item.isDelete = item.isDelete.not()
            // 遮罩直接贴到当前这一格，不走 notify 全量刷新
            applyDeleteMask(item, item.isDelete)

            if (item.isDelete.not()) {
                // 进行选中
                binding.ivSelectState.isChecked = true
                updateBottomMenuSelectNumberUI()
                val indexNow = tempSelectDataList.indexOf(item)
                binding.tvSelectIndex.text = "${indexNow + 1}"
                binding.tvSelectIndex.isChecked = true
            } else {
                binding.tvSelectIndex.text = ""
                binding.tvSelectIndex.isChecked = false
                binding.ivSelectState.isChecked = false
                updateBottomMenuSelectNumberUI()
            }
        }
    }

    /** 最普通的平滑滚动：交给 RecyclerView 自己算，不手动计算坐标 */
    fun scrollItemToCenter(recyclerView: RecyclerView, position: Int) {
        SelectedThumbnailStrip.smoothScrollToItem(recyclerView, position)
    }

    /** 按"边缘露出半格"算一次底部条间距并应用（只改间距，不改格子尺寸） */
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
            "FilePickerFinalPreviewDialog",
            "缩略图条：可视宽=${rv.width} 格子宽=$itemWidth 算出=$layout 当前=$stripLayout"
        )
        if (layout != null && layout != stripLayout) {
            stripLayout = layout
            rv.bindingAdapter?.notifyDataSetChanged()
        }
    }

    /** 当前预览项在已选列表里的下标；不在列表里返回 -1 */
    private fun currentStripIndex(): Int = tempSelectDataList.indexOf(mCurrentItem)

    /**
     * 整条重绘"当前页"绿框：按 adapter position 全量刷可见格子，
     * ViewHolder 被复用、数据被移动时都不会把绿框留在别的格子上。
     */
    private fun refreshStripSelection() {
        if (!::binding.isInitialized) return
        SelectedThumbnailStrip.paintCurrent(binding.rvSelected, currentStripIndex())
    }

    /** 直接更新某一格的"移出最终选择"遮罩，避免 notifyItemChanged 的交叉淡入淡出。 */
    private fun applyDeleteMask(item: FilePickerTempSelected, deleted: Boolean) {
        val holder = findThumbnailHolder(item.mediaEntity) ?: return
        val cover = holder.itemView.findViewById<View>(R.id.iv_cover_image) ?: return
        cover.foreground = if (deleted) {
            ContextCompat.getDrawable(context, R.drawable.item_filepicker_delete_preview_select_mask)
        } else {
            null
        }
    }

    /**
     * 按 Holder 实际绑定的数据找格子，而不是按 models 下标：
     * 长按拖动只移动 ViewHolder、没有重排 adapter 数据，用下标会标错。
     */
    private fun findThumbnailHolder(entity: MediaEntity?): RecyclerView.ViewHolder? {
        if (entity == null) return null
        val recyclerView = binding.rvSelected
        for (index in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(index))
            val model = (holder as? BindingAdapter.BindingViewHolder)?.getModel<FilePickerTempSelected>()
            if (model?.mediaEntity == entity) return holder
        }
        return null
    }

    override fun onBeforeDismiss(): Boolean {
        val deleteList = tempSelectDataList.filter { it.isDelete }.map { it.mediaEntity }.toMutableList()
        onDismissDataDelete(deleteList)
        return super.onBeforeDismiss()
    }

    override fun onDismiss() {
        FilePickerLog.d("FilePickerPreviewDialog", "onDismiss: releasing player for position=$mCurrentIndex")
        // 释放当前页面的播放器

        if (mCurrentIndex >= 0 && mCurrentIndex < tempSelectDataList.size) {
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
        super.onDismiss()
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

                if (mCurrentIndex >= 0 && mCurrentIndex < tempSelectDataList.size) {
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
                val item = tempSelectDataList.getOrNull(position) ?: return
                mCurrentItem = item
                // updateIndexUI 内部会整条重绘底部缩略图的绿框
                updateIndexUI(item)
                mCurrentIndex = position

                binding.rvSelected.post {
                    // 最普通的平滑滚动：交给 RecyclerView 自己算，已经可见就不动
                    SelectedThumbnailStrip.smoothScrollToItem(binding.rvSelected, position)
                }
            }
        })

        val index = itemIndexOfList(mCurrentItem)

        binding.viewPager2.setCurrentItem(index, false)

        binding.tvIndex.text = "${index + 1}/${tempSelectDataList.size}"

        updateIndexUI(mCurrentItem)
    }


    @SuppressLint("SetTextI18n")
    private fun updateIndexUI(item: FilePickerTempSelected?) {
        if (item == null) {
            return
        }
        binding.tvIndex.text = "${itemIndexOfList(item) + 1}/${tempSelectDataList.size}"
        // 这里需要根据是否选中来右上角角标。
        if (item.isDelete) {
            // 未选中
            binding.ivSelectState.isChecked = false
            binding.tvSelectIndex.text = ""
            binding.tvSelectIndex.isChecked = false
        } else {
            val indexOfSelect = itemIndexOfList(item)
            // 已选中
            binding.tvSelectIndex.text = "${indexOfSelect + 1}"
            binding.tvSelectIndex.isChecked = true
            binding.ivSelectState.isChecked = true
        }
        // 「编辑」入口：开启编辑且当前是图片时显示（视频/GIF 不支持）
        binding.tvEdit.isVisible = viewModel.editInPreview.value && item.mediaEntity.isImage()
        // 整条重绘底部缩略图的"当前页"绿框
        refreshStripSelection()
    }

    /** 预览页「编辑」：对当前大图做编辑（结果作为会话级覆盖，不改原图） */
    private fun startEditForCurrentItem() {
        val item = mCurrentItem?.mediaEntity ?: return
        if (!item.isImage()) return
        // 再次编辑从"编辑后的图"开始
        val source = viewModel.displayEntity(item) ?: item
        com.pichs.filepicker.crop.FilePickerEditDialog(
            context = context,
            entity = source,
            outputMaxSize = viewModel.cropOutputMaxSize.value,
            quality = viewModel.cropQuality.value,
        ) { editedPath ->
            if (editedPath.isNullOrEmpty()) {
                FilePickerLog.d("FilePickerFinalPreviewDialog", "编辑已取消")
                return@FilePickerEditDialog
            }
            viewModel.attachEdit(item, editedPath)
            FilePickerLog.d("FilePickerFinalPreviewDialog", "编辑完成: $editedPath")
            refreshAfterEdit(item)
            onEditApplied(item)
        }.showPopupWindow()
    }

    /** 编辑完成后：只刷当前页 + 底部已选条，不动列表数据、不动选中状态 */
    @SuppressLint("NotifyDataSetChanged")
    private fun refreshAfterEdit(item: MediaEntity) {
        val pos = itemIndexOfList(mCurrentItem)
        if (pos >= 0) {
            binding.viewPager2.adapter?.notifyItemChanged(pos)
        }
        binding.rvSelected.bindingAdapter?.notifyDataSetChanged()
        updateIndexUI(mCurrentItem)
    }


    @SuppressLint("SetTextI18n")
    private fun updateBottomMenuSelectNumberUI() {
        // 只更新按钮文案/数量。缩略图条的数据和状态在变化的当格就地更新，
        // 不再用 notifyItemRangeChanged 全量刷新（那是缩略图闪烁的直接原因）。
        val selectedMergeSize = tempSelectDataList.filter { !it.isDelete }.size
        if (selectedMergeSize > 0) {
            binding.btnConfirm.isEnabled = true
            binding.btnConfirm.text = "${viewModel.uiConfig.confirmBtnText}(${selectedMergeSize})"
        } else {
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
            return if (tempSelectDataList.getOrNull(position)?.mediaEntity?.isVideo() == true) {
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

        override fun getItemCount() = tempSelectDataList.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = tempSelectDataList.getOrNull(position) ?: return
            if (holder is ImageViewHolder) {
                MediaLoader.loadImage(viewModel.displayEntity(item.mediaEntity), holder.photoView)
            } else if (holder is VideoViewHolder) {
                FilePickerLog.d("MediaPagerAdapter", "onBindViewHolder: item.path=${item.mediaEntity.path}, item.uri=${item.mediaEntity.uri}")
                holder.videoPlayerView.loadCover(item.mediaEntity)
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
                holder.videoPlayerView.loadCover(tempSelectDataList.getOrNull(holder.absoluteAdapterPosition)?.mediaEntity)
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
