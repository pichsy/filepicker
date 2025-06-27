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

@UnstableApi
class FilePickerPreviewDialog(
    val context: Context, val viewModel: FilePickerViewModel, val curIndex: Int, val onSelect: (MediaEntity, Boolean, Int) -> Unit, val onConfirm: (Int) -> Unit
) : BasePopupWindow(context) {

    lateinit var binding: DialogFilePickerPreviewBinding

    private var mCurrentIndex = 0

    init {
        setContentView(R.layout.dialog_file_picker_preview)
    }

    override fun onViewCreated(contentView: View) {
        super.onViewCreated(contentView)
        binding = DialogFilePickerPreviewBinding.bind(contentView)
        setBackgroundColor(Color.TRANSPARENT)
        mCurrentIndex = curIndex
        binding.tvMaxSelectNumber.text = "${viewModel.maxSelectNumber.value}"
        updateBottomMenuSelectNumberUI()
        initViewPager2()
        initListener()
    }

    @SuppressLint("SetTextI18n")
    private fun initListener() {

        binding.ivBack.setOnClickListener {
            dismiss()
        }

        binding.btnDialogConfirm.setOnClickListener {
            // 这里可以回调到选择器，通知选择完成。
            if (viewModel.getSelectedCount() <= 0 && viewModel.tempSelectData.isEmpty()) {
                Toast.makeText(context, "至少选择一个", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onConfirm(mCurrentIndex)
        }

        binding.flSelectIndex.setOnClickListener {
            val item = viewModel.getCurrentFolderDataByPosition(mCurrentIndex)
            if (item == null) return@setOnClickListener

            val indexOfSelect = viewModel.indexOfSelected(item)
            if (indexOfSelect == -1) {
                // 未选中，添加到选中列表
                val listSize = viewModel.getSelectedCount() + viewModel.tempSelectData.size
                if (viewModel.isOverMaxSelectNumber(listSize)) {
                    Toast.makeText(context, "已达到最大选择数量", Toast.LENGTH_SHORT).show()
                    updateBottomMenuSelectNumberUI()
                    return@setOnClickListener
                }
                // 进行选中
                viewModel.addSelectedData(item)
                binding.ivSelectState.isChecked = true
                updateBottomMenuSelectNumberUI()
//                binding.tvSelectIndex.text = "${indexNow + 1}"
//                binding.tvSelectIndex.setNormalBackgroundColor(ContextCompat.getColor(context, R.color.file_picker_index_bg_color))
                onSelect(item, true, mCurrentIndex)
            } else {
                viewModel.removeSelectedData(item)
//                binding.tvSelectIndex.text = ""
//                binding.tvSelectIndex.setNormalBackgroundColor(Color.TRANSPARENT)
                binding.ivSelectState.isChecked = false
                updateBottomMenuSelectNumberUI()
                onSelect(item, false, mCurrentIndex)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initViewPager2() {
        // ViewPager2 预览
        binding.viewPager2.adapter = MediaPagerAdapter(context, viewModel.getCurrentFolderDataList())

        binding.viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (mCurrentIndex == position) {
                    return
                }
//                // 查找上一个页面的 ViewHolder 并释放播放器
//                val recyclerView = binding.viewPager2.getChildAt(0) as RecyclerView
//                val viewHolder = recyclerView.findViewHolderForAdapterPosition(mCurrentIndex)
//                if (viewHolder is MediaPagerAdapter.VideoViewHolder) {
//                    viewHolder.videoPlayerView.releasePlayer()
//                }
                val item = viewModel.getCurrentFolderDataByPosition(position) ?: return
                updateIndexUI(item)
                mCurrentIndex = position
            }
        })

        binding.viewPager2.setCurrentItem(curIndex, false)
        binding.tvIndex.text = "${curIndex + 1}/${viewModel.getCurrentFolderDataList().size}"
        updateIndexUI(viewModel.getCurrentFolderDataByPosition(curIndex) ?: return)
    }


    @SuppressLint("SetTextI18n")
    private fun updateIndexUI(item: MediaEntity) {
        binding.tvIndex.text = "${mCurrentIndex + 1}/${viewModel.getCurrentFolderDataList().size}"
        // 这里需要根据是否选中来右上角角标。
        val indexOfSelect = viewModel.indexOfSelected(item)
        if (indexOfSelect == -1) {
            // 未选中
            binding.ivSelectState.isChecked = false
//            binding.tvSelectIndex.text = ""
//            binding.tvSelectIndex.setNormalBackgroundColor(Color.TRANSPARENT)
        } else {
            // 已选中
//            binding.tvSelectIndex.text = "${indexOfSelect + 1}"
//            binding.tvSelectIndex.setNormalBackgroundColor(ContextCompat.getColor(context, R.color.file_picker_index_bg_color))
            binding.ivSelectState.isChecked = true
        }
    }


//    override fun onCreateShowAnimation(): Animation? {
//        return AnimationHelper.asAnimation().withAlpha(AlphaConfig().apply {
//            from(0.7f)
//            to(1f)
//        }).toShow()
//    }
//
//    override fun onCreateDismissAnimation(): Animation? {
//        return AnimationHelper.asAnimation().withAlpha(AlphaConfig().apply {
//            from(1f)
//            to(0.7f)
//        }).toDismiss()
//    }


    private fun updateBottomMenuSelectNumberUI() {
        val selectedMergeSize = viewModel.getSelectedCount() + viewModel.tempSelectData.size
        Log.e("FilePickerPreviewDialog", "updateBottomMenuSelectNumberUI: selectedMergeSize=${selectedMergeSize}")
        if (viewModel.maxSelectNumber.value > 0) {
            if (selectedMergeSize <= 0) {
                binding.tvSelectNumberHint.text = "至少选择一个"
                binding.tvSelectNumber.text = ""
                binding.tvSelectNumber.isVisible = false
                binding.tvSelectNumberSplitLine.isVisible = false
                binding.tvMaxSelectNumber.isVisible = false
                binding.btnDialogConfirm.alpha = 0.3f
//                Log.e("FilePickerPreviewDialog", "updateBottomMenuSelectNumberUI: binding.btnConfirm.alpha = 0.3f")
            } else {
                binding.tvSelectNumberHint.text = "已选:"
                binding.tvSelectNumber.text = "$selectedMergeSize"
                binding.tvSelectNumber.isVisible = true
                binding.tvSelectNumberSplitLine.isVisible = true
                binding.tvMaxSelectNumber.isVisible = true
                binding.btnDialogConfirm.alpha = 1f
//                Log.e("FilePickerPreviewDialog", "updateBottomMenuSelectNumberUI: binding.btnConfirm.alpha = 1f")
            }
        } else {
            if (selectedMergeSize <= 0) {
                binding.tvSelectNumberHint.text = "至少选择一个"
                binding.tvSelectNumber.text = ""
                binding.tvSelectNumber.isVisible = false
                binding.tvSelectNumberSplitLine.isVisible = false
                binding.tvMaxSelectNumber.isVisible = false
                binding.btnDialogConfirm.alpha = 0.3f
//                Log.e("FilePickerPreviewDialog", "updateBottomMenuSelectNumberUI:222222 binding.btnConfirm.alpha = 0.3f")
            } else {
                binding.tvSelectNumberHint.text = "已选:"
                binding.tvSelectNumber.text = "$selectedMergeSize"
                binding.btnDialogConfirm.alpha = 1f
                binding.tvSelectNumber.isVisible = true
                binding.tvMaxSelectNumber.isVisible = false
                binding.tvSelectNumberSplitLine.isVisible = false
//                Log.e("FilePickerPreviewDialog", "updateBottomMenuSelectNumberUI:222222 binding.btnConfirm.alpha = 1f")

            }
        }
    }

}

@UnstableApi
class MediaPagerAdapter(
    private val context: Context, private val items: List<MediaEntity>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_IMAGE = 0
        const val TYPE_VIDEO = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].isVideo()) {
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

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
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
            holder.videoPlayerView.loadCover(items.getOrNull(holder.absoluteAdapterPosition))
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoViewHolder) {
            holder.videoPlayerView.releasePlayer()
        }
    }

    class ImageViewHolder(val photoView: PhotoView) : RecyclerView.ViewHolder(photoView)

    @UnstableApi
    class VideoViewHolder(val videoPlayerView: VideoPlayerView) : RecyclerView.ViewHolder(videoPlayerView)
}
