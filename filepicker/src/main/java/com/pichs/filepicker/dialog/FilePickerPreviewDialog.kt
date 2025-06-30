package com.pichs.filepicker.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
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
import com.drake.brv.utils.models
import com.drake.brv.utils.setup
import com.pichs.filepicker.databinding.FilePickerItemRvAlbumSelectedBinding
import com.pichs.filepicker.loader.MediaLoader
import com.pichs.filepicker.utils.FilePickerClickHelper
import com.pichs.filepicker.utils.FilePickerTimeFormatUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@UnstableApi
class FilePickerPreviewDialog(
    val context: Context,
    val viewModel: FilePickerViewModel,
    val curItem: MediaEntity,
    val onSelect: (MediaEntity, Boolean, Int) -> Unit,
    val onConfirm: (Int) -> Unit
) : BasePopupWindow(context) {

    lateinit var binding: DialogFilePickerPreviewBinding

    private var mCurrentItem: MediaEntity? = null
    private var mCurrentIndex: Int = 0

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
        mCurrentIndex = itemIndexOfList(mCurrentItem)
        initConfigUI()
        initViewPager2()
        initSelectedRecyclerView()
        initListener()
        updateBottomMenuSelectNumberUI()

        job = viewModel.viewModelScope.launch {
            launch {
                viewModel.currentFolderDataList.collect {
                    Log.d("FilePickerFragment", "initDataFlow currentFolderDataList: size:${it.size}")
                    mCurrentIndex = viewModel.getCurrentFolderDataList().indexOf(mCurrentItem)

                    binding.viewPager2.adapter?.notifyDataSetChanged()

                    binding.viewPager2.setCurrentItem(mCurrentIndex, false)
                    updateIndexUI(mCurrentItem)
                    updateBottomMenuSelectNumberUI()
                }
            }

            launch {
                viewModel.originalCheckedFlow.collect {
                    Log.d("FilePickerPreviewDialog", "initDataFlow-----dialog originalCheckedFlow: isChecked:${it}")
                    binding.cboxOriginal.isChecked = it
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
            addType<MediaEntity>(R.layout.file_picker_item_rv_album_selected)

            onBind {
                val item = getModel<MediaEntity>()
                val itemBinding = getBinding<FilePickerItemRvAlbumSelectedBinding>()

                MediaLoader.loadImage(item.uri, item.mimeType, itemBinding.ivCoverImage)

                itemBinding.clSelectDelete.isVisible = viewModel.uiConfig.isShowSelectedListDeleteIcon
                itemBinding.tvDelete.setBackgroundColor(viewModel.uiConfig.selectedListDeleteIconBackgroundColor)

                if (mCurrentItem == item) {
                    itemBinding.ivCoverImage.isSelected = true
                } else {
                    itemBinding.ivCoverImage.isSelected = false
                }

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
        }.models = viewModel.getSelectedDataList()
    }

    fun itemIndexOfList(item: MediaEntity?): Int {
        if (item == null) return 0
        return viewModel.getCurrentFolderDataList().indexOf(item)
    }

    @SuppressLint("SetTextI18n")
    private fun initListener() {
        FilePickerClickHelper.clicks(binding.ivBack) {
            dismiss()
        }

        FilePickerClickHelper.clicks(binding.btnConfirm) {
//            这里可以回调到选择器，通知选择完成。
//            if (viewModel.getSelectedCount() <= 0 && viewModel.tempSelectData.isEmpty()) {
//                Toast.makeText(context, "至少选择一个", Toast.LENGTH_SHORT).show()
//                return@clicks
//            }
            onConfirm(mCurrentIndex)
        }

        FilePickerClickHelper.clicks(binding.flSelectIndex) {
            val item = viewModel.getCurrentFolderDataByPosition(mCurrentIndex)
            if (item == null) return@clicks

            val indexOfSelect = viewModel.indexOfSelected(item)

            if (indexOfSelect == -1) {
                // 未选中，添加到选中列表
                val listSize = viewModel.getSelectedCount() + viewModel.tempSelectData.size
                if (viewModel.isOverMaxSelectNumber(listSize)) {
                    Toast.makeText(context, "已达到最大选择数量", Toast.LENGTH_SHORT).show()
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

    fun scrollItemToCenter(recyclerView: RecyclerView, position: Int) {
        if (position == -1) return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val itemView = layoutManager.findViewByPosition(position)
        val recyclerViewWidth = recyclerView.width

        val itemWidth = itemView?.width ?: 0
        val offset = (recyclerViewWidth - itemWidth) / 2

        layoutManager.scrollToPositionWithOffset(position, offset)
    }

    override fun onDismiss() {
        super.onDismiss()
        Log.d("FilePickerPreviewDialog", "onDismiss: releasing player for position=$mCurrentIndex")
        // 释放当前页面的播放器

        if (mCurrentIndex >= 0 && mCurrentIndex < viewModel.getCurrentFolderDataList().size) {
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

                if (mCurrentIndex >= 0 && mCurrentIndex < viewModel.getCurrentFolderDataList().size) {
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
                val item = viewModel.getCurrentFolderDataByPosition(position) ?: return
                mCurrentItem = item
                updateIndexUI(item)
                mCurrentIndex = position

                binding.rvSelected.post {
                    // refresh select rv
                    binding.rvSelected.adapter?.notifyItemRangeChanged(0, binding.rvSelected.adapter?.itemCount ?: 0)
                    val indexOfSelect = viewModel.indexOfSelected(item)
                    scrollItemToCenter(binding.rvSelected, indexOfSelect)
                }
            }
        })

        val index = itemIndexOfList(mCurrentItem)

        binding.viewPager2.setCurrentItem(index, false)

        binding.tvIndex.text = "${index + 1}/${viewModel.getCurrentFolderDataList().size}"

        updateIndexUI(mCurrentItem)
    }


    @SuppressLint("SetTextI18n")
    private fun updateIndexUI(item: MediaEntity?) {
        if (item == null) {
            return
        }
        binding.tvIndex.text = "${itemIndexOfList(item) + 1}/${viewModel.getCurrentFolderDataList().size}"
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
    }


    @SuppressLint("SetTextI18n")
    private fun updateBottomMenuSelectNumberUI() {
        val selectedMergeSize = viewModel.getSelectedCount() + viewModel.tempSelectData.size
        Log.e("FilePickerPreviewDialog", "updateBottomMenuSelectNumberUI: selectedMergeSize=${selectedMergeSize}")
        if (selectedMergeSize > 0) {
            binding.rvSelected.isVisible = true
            binding.btnConfirm.isEnabled = true
            binding.btnConfirm.text = "${viewModel.uiConfig.confirmBtnText}(${selectedMergeSize})"
            binding.rvSelected.models = viewModel.getSelectedDataList()
            scrollItemToCenter(binding.rvSelected, viewModel.indexOfSelected(mCurrentItem))
        } else {
            binding.rvSelected.isVisible = false
            binding.rvSelected.models = viewModel.getSelectedDataList()
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
                ImageViewHolder(photoView)
            } else {
                val playView = VideoPlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                VideoViewHolder(playView)
            }
        }

        override fun getItemCount() = viewModel.getCurrentFolderDataList().size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = viewModel.getCurrentFolderDataList().getOrNull(position) ?: return
            if (holder is ImageViewHolder) {
                Glide.with(context).load(item.path).into(holder.photoView)
            } else if (holder is VideoViewHolder) {
                Log.d("MediaPagerAdapter", "onBindViewHolder: item.path=${item.path}, item.uri=${item.uri}")
                holder.videoPlayerView.loadCover(item)
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
        inner class VideoViewHolder(val videoPlayerView: VideoPlayerView) : RecyclerView.ViewHolder(videoPlayerView)
    }
}
