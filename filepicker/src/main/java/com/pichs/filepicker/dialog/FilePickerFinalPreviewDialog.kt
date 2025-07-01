package com.pichs.filepicker.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.pichs.filepicker.FilePickerViewModel
import com.pichs.filepicker.R
import com.pichs.filepicker.databinding.DialogFilePickerPreviewBinding
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.photoview.PhotoView
import com.pichs.filepicker.video.VideoPlayerView
import razerdp.basepopup.BasePopupWindow
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.drake.brv.utils.linear
import com.drake.brv.utils.setup
import com.pichs.filepicker.databinding.DialogFilePickerSelectedPreviewBinding
import com.pichs.filepicker.databinding.FilePickerItemRvAlbumJustSelectedBinding
import com.pichs.filepicker.entity.FilePickerTempSelected
import com.pichs.filepicker.loader.MediaLoader
import com.pichs.filepicker.utils.FilePickerClickHelper
import com.pichs.filepicker.utils.FilePickerTimeFormatUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class FilePickerFinalPreviewDialog(
    val context: Context,
    val viewModel: FilePickerViewModel,
    val onDismissDataDelete: (deleteList: MutableList<MediaEntity>) -> Unit,
    val onConfirm: (ArrayList<MediaEntity>) -> Unit
) : BasePopupWindow(context) {

    lateinit var binding: DialogFilePickerSelectedPreviewBinding

    private var isShowToolBar = MutableStateFlow(true)

    private val tempSelectDataList = viewModel.getSelectedDataList().map { FilePickerTempSelected(false, it) }.toMutableList()

    private var mCurrentItem: FilePickerTempSelected? = null

    private var mCurrentIndex: Int = 0

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
                    Log.d("FilePickerPreviewDialog", "initDataFlow-----dialog originalCheckedFlow: isChecked:${it}")
                    binding.cboxOriginal.isChecked = it
                }
            }

            launch {
                isShowToolBar.collectLatest { isShow ->
                    Log.d("FilePickerPreviewDialog", "initDataFlow-----dialog isShowToolBar: isShow:${isShow}")
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

        binding.llOriginal.isVisible = viewModel.uiConfig.isShowOriginal
        binding.tvOriginal.text = viewModel.uiConfig.originalText
        binding.cboxOriginal.isChecked = viewModel.originalCheckedFlow.value
        binding.tvSelect.text = viewModel.uiConfig.previewSelectText

        if (viewModel.uiConfig.isShowPreviewPageSelectedIndex) {
            binding.tvSelectIndex.isVisible = true
            binding.ivSelectState.isVisible = false
        } else {
            binding.tvSelectIndex.isVisible = false
            binding.ivSelectState.isVisible = true
        }
    }

    private fun initSelectedRecyclerView() {
        binding.rvSelected.itemAnimator = null
        binding.rvSelected.linear(RecyclerView.HORIZONTAL).setup {
            addType<FilePickerTempSelected>(R.layout.file_picker_item_rv_album_just_selected)

            onBind {
                val item = getModel<FilePickerTempSelected>()
                val itemBinding = getBinding<FilePickerItemRvAlbumJustSelectedBinding>()

                val itemEntity = item.mediaEntity

                MediaLoader.loadImage(itemEntity.uri, itemEntity.mimeType, itemBinding.ivCoverImage)

                itemBinding.clSelectDelete.isVisible = viewModel.uiConfig.isShowSelectedListDeleteIcon
                itemBinding.tvDelete.setBackgroundColor(viewModel.uiConfig.selectedListDeleteIconBackgroundColor)

                if (mCurrentItem == item) {
                    itemBinding.ivCoverImage.isSelected = true
                } else {
                    itemBinding.ivCoverImage.isSelected = false
                }

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
                    notifyItemChanged(modelPosition)
//                    onSelect(item, false, itemIndexOfList(itemEntity))
                    updateBottomMenuSelectNumberUI()
                }

                itemBinding.root.setOnClickListener {
                    // 需要切换是当前 的viewpager2 的位置。
                    val indexOfList = itemIndexOfList(item)
                    if (indexOfList != mCurrentIndex) {
                        binding.viewPager2.setCurrentItem(indexOfList, false)
                        mCurrentIndex = indexOfList
                        mCurrentItem = item
                        notifyItemRangeChanged(0, binding.rvSelected.adapter?.itemCount ?: 0)
                        updateIndexUI(item)
                        scrollItemToCenter(binding.rvSelected, modelPosition)
                    }
                }
            }
        }.models = tempSelectDataList
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

    fun scrollItemToCenter(recyclerView: RecyclerView, position: Int) {
        if (position == -1) return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val itemView = layoutManager.findViewByPosition(position)
        val recyclerViewWidth = recyclerView.width

        val itemWidth = itemView?.width ?: 0
        val offset = (recyclerViewWidth - itemWidth) / 2

        layoutManager.scrollToPositionWithOffset(position, offset)
    }

    override fun onBeforeDismiss(): Boolean {
        val deleteList = tempSelectDataList.filter { it.isDelete }.map { it.mediaEntity }.toMutableList()
        onDismissDataDelete(deleteList)
        return super.onBeforeDismiss()
    }

    override fun onDismiss() {
        Log.d("FilePickerPreviewDialog", "onDismiss: releasing player for position=$mCurrentIndex")
        // 释放当前页面的播放器

        if (mCurrentIndex >= 0 && mCurrentIndex < tempSelectDataList.size) {
            // 释放上一个页面的播放器
            Log.d("FilePickerPreviewDialog", "onPageSelected: releasing player for position=$mCurrentIndex")
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

        binding.viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            @SuppressLint("NotifyDataSetChanged")
            override fun onPageSelected(position: Int) {

                if (mCurrentIndex == position) {
                    return
                }

                if (mCurrentIndex >= 0 && mCurrentIndex < tempSelectDataList.size) {
                    // 释放上一个页面的播放器
                    Log.d("FilePickerPreviewDialog", "onPageSelected: releasing player for position=$mCurrentIndex")
//                // 查找上一个页面的 ViewHolder 并释放播放器
                    val recyclerView = binding.viewPager2.getChildAt(0) as RecyclerView
                    val viewHolder = recyclerView.findViewHolderForAdapterPosition(mCurrentIndex)
                    if (viewHolder is MediaPagerAdapter.VideoViewHolder) {
                        viewHolder.videoPlayerView.releasePlayer()
                    }
                }
                Log.d("FilePickerPreviewDialog", "onPageSelected: position=$position, mCurrentIndex=$mCurrentIndex")
                val item = tempSelectDataList.getOrNull(position) ?: return
                mCurrentItem = item
                updateIndexUI(item)
                mCurrentIndex = position

                binding.rvSelected.post {
                    // refresh select rv
                    binding.rvSelected.adapter?.notifyItemRangeChanged(0, binding.rvSelected.adapter?.itemCount ?: 0)
                    val indexOfSelect = tempSelectDataList.indexOf(item)
                    scrollItemToCenter(binding.rvSelected, indexOfSelect)
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
    }


    @SuppressLint("SetTextI18n")
    private fun updateBottomMenuSelectNumberUI() {
        val selectedMergeSize = tempSelectDataList.filter { !it.isDelete }.size
        Log.e("FilePickerPreviewDialog", "updateBottomMenuSelectNumberUI: selectedMergeSize=${selectedMergeSize}")
        if (selectedMergeSize > 0) {
//            binding.rvSelected.isVisible = true
            binding.btnConfirm.isEnabled = true
            binding.btnConfirm.text = "${viewModel.uiConfig.confirmBtnText}(${selectedMergeSize})"
//            binding.rvSelected.models = tempSelectDataList
            // 重新刷新列表
            binding.rvSelected.adapter?.notifyItemRangeChanged(0, binding.rvSelected.adapter?.itemCount ?: 0)
            scrollItemToCenter(binding.rvSelected, itemIndexOfList(mCurrentItem))
        } else {
//            binding.rvSelected.isVisible = false
//            binding.rvSelected.models = tempSelectDataList
//            binding.btnConfirm.isEnabled = false
            binding.rvSelected.adapter?.notifyItemRangeChanged(0, binding.rvSelected.adapter?.itemCount ?: 0)
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
                val playView = VideoPlayerView(context).apply {
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
                Glide.with(context).load(item.mediaEntity.path).into(holder.photoView)
            } else if (holder is VideoViewHolder) {
                Log.d("MediaPagerAdapter", "onBindViewHolder: item.path=${item.mediaEntity.path}, item.uri=${item.mediaEntity.uri}")
                holder.videoPlayerView.loadCover(item.mediaEntity)
            }
        }

        override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
            super.onViewDetachedFromWindow(holder)
            Log.d("MediaPagerAdapter", "onViewDetachedFromWindow11111:positon${holder.absoluteAdapterPosition} ==========")
            if (holder is VideoViewHolder) {
                holder.videoPlayerView.releasePlayer()
            }
        }

        override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
            super.onViewAttachedToWindow(holder)
            Log.d("MediaPagerAdapter", "onViewAttachedToWindow22222: position=${holder.absoluteAdapterPosition} ==========")
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
        inner class VideoViewHolder(val videoPlayerView: VideoPlayerView) : RecyclerView.ViewHolder(videoPlayerView)
    }
}
