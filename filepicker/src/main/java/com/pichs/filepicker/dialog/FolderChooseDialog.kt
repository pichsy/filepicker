package com.pichs.filepicker.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import com.drake.brv.utils.linear
import com.drake.brv.utils.setup
import com.pichs.filepicker.R
import com.pichs.filepicker.databinding.FilePickerFolderChooseDialogBinding
import com.pichs.filepicker.databinding.FilePickerFolderChooseDialogItemBinding
import com.pichs.filepicker.entity.MediaFolder
import com.pichs.filepicker.loader.MediaLoader
import razerdp.basepopup.BasePopupWindow
import razerdp.util.animation.AnimationHelper
import razerdp.util.animation.TranslationConfig

class FolderChooseDialog(mCtx: Context, val list: MutableList<MediaFolder>, val currentFolder: MediaFolder?, val onSelectCallback: (MediaFolder?) -> Unit) :
    BasePopupWindow(mCtx) {

    private lateinit var binding: FilePickerFolderChooseDialogBinding

    init {
        setContentView(R.layout.file_picker_folder_choose_dialog)
    }

    override fun onViewCreated(contentView: View) {
        super.onViewCreated(contentView)
        binding = FilePickerFolderChooseDialogBinding.bind(contentView)
        setPopupGravityMode(GravityMode.ALIGN_TO_ANCHOR_SIDE, GravityMode.RELATIVE_TO_ANCHOR)
        popupGravity = Gravity.CENTER or Gravity.BOTTOM

        // 设置 弹窗 下面，上面不透明
        setBackgroundColor(Color.TRANSPARENT)

        setOutSideDismiss(true)

        setOutSideTouchable(true)

        initRecycler()
    }

    @SuppressLint("SetTextI18n")
    private fun initRecycler() {
        binding.recyclerView.itemAnimator = null
        binding.recyclerView.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL).apply {
            ContextCompat.getDrawable(context, R.drawable.item_decroration_line)?.let { setDrawable(it) }
        })
        binding.recyclerView.linear().setup {
            addType<MediaFolder>(R.layout.file_picker_folder_choose_dialog_item)

            onBind {
                val item = getModel<MediaFolder>()
                val itemBinding = getBinding<FilePickerFolderChooseDialogItemBinding>()

                itemBinding.tvAlbum.text = item.name ?: ""
                if (item.name == "全部") {
                    itemBinding.tvAlbumNumber.text = "(${list.sumOf { it.mediaEntityList.size }})"
                    if (currentFolder == null) {
                        itemBinding.ivSelect.isVisible = true
                    } else {
                        itemBinding.ivSelect.isVisible = false
                    }
                } else {
                    itemBinding.tvAlbumNumber.text = "(${item.mediaEntityList.size})"
                    itemBinding.ivSelect.isVisible = currentFolder?.name == item.name
                }

                val firstEntity = item.mediaEntityList.firstOrNull()
                if (firstEntity != null) {
                    MediaLoader.loadImage(firstEntity.uri, firstEntity.mimeType, itemBinding.ivCover)
                } else {
                    val ent = list.firstOrNull()?.mediaEntityList?.firstOrNull()
                    MediaLoader.loadImage(ent?.uri, ent?.mimeType, itemBinding.ivCover)
                }

                itemBinding.root.setOnClickListener {
                    if (item.name == "全部") {
                        onSelectCallback(null)
                    } else {
                        onSelectCallback(item)
                    }
                    dismiss()
                }
            }
        }.models = list.toMutableList().apply {
            add(0, MediaFolder("全部", null))
        }
    }

    override fun showPopupWindow(anchorView: View?) {
        super.showPopupWindow(anchorView)
    }

//    override fun onCreateShowAnimation(): Animation? {
//        return AnimationHelper.asAnimation().withTranslation(TranslationConfig.FROM_TOP).toShow()
//    }
//
//    override fun onCreateDismissAnimation(): Animation? {
//        return AnimationHelper.asAnimation().withTranslation(TranslationConfig.TO_TOP).toDismiss()
//    }

}