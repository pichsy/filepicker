package com.pichs.filepicker.demo

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Toast
import android.os.Bundle
import android.service.autofill.Validators.and
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.isVisible
import com.drake.brv.utils.divider
import com.drake.brv.utils.grid
import com.drake.brv.utils.models
import com.drake.brv.utils.setup
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.pichs.filepicker.FilePicker
import com.pichs.filepicker.FilePickerSelectType
import com.pichs.filepicker.FilePickerUIConfig
import com.pichs.filepicker.FilePickerViewModel
import com.pichs.filepicker.common.ImagePreviewDialog
import com.pichs.filepicker.demo.databinding.ActivityMainBinding
import com.pichs.filepicker.demo.databinding.ItemImageBinding
import com.pichs.filepicker.demo.databinding.ItemResultFileBinding
import com.pichs.filepicker.demo.databinding.ItemResultMediaBinding
import com.pichs.filepicker.demo.databinding.ItemTypeChipBinding
import com.pichs.filepicker.demo.paging.PagingDemoActivity
import com.pichs.filepicker.demo.paging.ImagePagingDemoActivity
import com.pichs.filepicker.demo.newpicker.LocalMediaPickerActivity
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.loader.MediaLoader
import com.pichs.filepicker.utils.FilePickerTimeFormatUtils
import com.pichs.xwidget.utils.XDisplayHelper
import com.pichs.filepicker.common.VideoPreviewDialog
import com.pichs.filepicker.query.FileQueryHelper
import com.pichs.filepicker.query.QueryType
import com.pichs.xbase.binding.BindingActivity
import com.pichs.xbase.kotlinext.fastClick
import com.pichs.xbase.xlog.XLog
import com.pichs.xbase.xlog.XLogFileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BindingActivity<ActivityMainBinding>() {

    override fun beforeOnCreate(savedInstanceState: Bundle?) {
        super.beforeOnCreate(savedInstanceState)
    }

    @SuppressLint("SetTextI18n")
    override fun afterOnCreate() {
        // 右上角设置：数据制造、相机 intent 探测这些调试入口都收进去了，首页只留主链路
        binding.ivSettings.fastClick {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 开始按钮点击事件
        binding.btnStart.fastClick {
            // 获取最大选择数量
            val maxSelectCount = binding.etMaxSelectCount.text.toString().toIntOrNull() ?: 0
            // 获取最大文件大小（MB转字节）
            val maxFileSizeMB = binding.etMaxFileSize.text.toString().toIntOrNull() ?: 200
            val maxFileSize = maxFileSizeMB * 1024 * 1024
            // 获取类型
            val type = selectedType
            // 是否显示拍照/录像入口。原样传递，不在 demo 里替库做类型判断 ——
            // 这样正好能在"全部 + 勾选"的组合下验证库自己的类型判定
            val showCameraEntry = binding.cbShowCamera.isChecked

            if (type == FilePickerSelectType.ofAll() || type == FilePickerSelectType.ofVideo() || type == FilePickerSelectType.ofImage() || type == FilePickerSelectType.ofAudio() || type == FilePickerSelectType.ofGif() || type == FilePickerSelectType.ofAllWithGif()) {
                // 权限请求
                XXPermissions.with(this).unchecked().permission(
                    Permission.READ_MEDIA_IMAGES,
                    Permission.READ_MEDIA_VIDEO,
                    Permission.READ_MEDIA_AUDIO,
                ).request { permissions, all ->
                    if (all) {
                        selectFile(type, maxSelectCount, maxFileSize, showCameraEntry)
                    } else {
                        XXPermissions.startPermissionActivity(this, permissions)
                    }
                }
            } else {
                // 权限请求
                XXPermissions.with(this).unchecked().permission(
                    Permission.MANAGE_EXTERNAL_STORAGE,
                ).request { permissions, all ->
                    if (all) {
                        selectFile(type, maxSelectCount, maxFileSize, showCameraEntry)
                    } else {
                        XXPermissions.startPermissionActivity(this, permissions)
                    }
                }
            }
        }
        initRecyclerView()
        initTypeList()
    }

    /**
     * 选择结果：4 列纵向网格。
     *
     * 同一个 MediaEntity 按"能不能出缩略图"分成两种 item 布局（BRV 的 addType 支持按数据
     * 动态给布局，所以不用一个布局里塞一堆 visibility 开关）：
     * - 图片 / 视频 / GIF → [item_result_media]：缩略图 + 视频播放标记与时长、GIF 标记
     * - 音频 / 文档 / 压缩包 / apk / 任意其它后缀 → [item_result_file]：按类型着色的色块 + 后缀角标 + 文件名
     */
    private fun initRecyclerView() {
        binding.recyclerView.grid(4).setup {
            addType<MediaEntity> {
                if (isImage() || isVideo() || isGif()) {
                    R.layout.item_result_media
                } else {
                    R.layout.item_result_file
                }
            }

            onBind {
                val item = getModel<MediaEntity>()
                val index = modelPosition + 1
                if (item.isImage() || item.isVideo() || item.isGif()) {
                    bindMediaCell(getBinding<ItemResultMediaBinding>(), item, index)
                } else {
                    bindFileCell(getBinding<ItemResultFileBinding>(), item, index)
                }
            }
        }
    }

    /** 缩略图那一类：图片/视频/GIF */
    private fun bindMediaCell(binding: ItemResultMediaBinding, item: MediaEntity, index: Int) {
        binding.tvIndex.text = "$index"
        // 统一走库里的 MediaLoader：图片读真实文件、视频走 MediaStore 视频缩略图通道，
        // 和选择器内部的取图口径保持一致（自己用 Glide 直接 load(path) 对视频是拿不到封面的）
        MediaLoader.loadThumbnail(item, binding.ivCover)

        val isVideo = item.isVideo()
        binding.ivPlay.isVisible = isVideo
        binding.tvDuration.isVisible = isVideo
        binding.tvDuration.text = FilePickerTimeFormatUtils.formatTimeMillSeconds(item.duration)
        binding.tvGif.isVisible = item.isGif()

        binding.root.setOnClickListener { previewItem(item) }
        binding.ivDelete.setOnClickListener { removeResult(item) }
    }

    /** 文字色块那一类：音频/文档/压缩包/其它 */
    private fun bindFileCell(binding: ItemResultFileBinding, item: MediaEntity, index: Int) {
        binding.tvIndex.text = "$index"
        binding.tvExt.text = item.extLabel()
        binding.tvName.text = item.name ?: item.path?.substringAfterLast('/').orEmpty()
        binding.flFile.background = GradientDrawable().apply {
            cornerRadius = XDisplayHelper.dp2px(this@MainActivity, 8f).toFloat()
            setColor(fileCellColor(item))
        }

        binding.root.setOnClickListener { previewItem(item) }
        binding.ivDelete.setOnClickListener { removeResult(item) }
    }

    /**
     * 从结果列表里移除一条。
     *
     * 只动 demo 自己这份结果列表，**不删文件**。同时从 selectedDataList 里摘掉 ——
     * 它会被当作"上次选的"在下次进选择器时传回去（setSelectedList），
     * 不摘的话下次进去会看到一个已经被你删掉的预选项。
     */
    private fun removeResult(item: MediaEntity) {
        selectedDataList = selectedDataList.filterNot { it == item }.toMutableList()
        renderResult(selectedDataList)
    }

    /** 后缀角标：取文件名最后一个点之后的部分，大写；取不到就退化成 FILE */
    private fun MediaEntity.extLabel(): String {
        val fileName = name ?: path?.substringAfterLast('/').orEmpty()
        val ext = fileName.substringAfterLast('.', "")
        return if (ext.isEmpty()) "FILE" else ext.uppercase()
    }

    /**
     * 色块颜色按类型给，同类同色 —— 扫一眼就能分清音频/文档/压缩包，不用读名字。
     * 用的都是深色系（饱和度够、白字压得住）。
     */
    private fun fileCellColor(item: MediaEntity): Int = when {
        item.isAudio() -> 0xFF6A1B9A.toInt()                                  // 音频：紫
        item.isPdf() -> 0xFFC62828.toInt()                                    // pdf：红
        item.isWordDoc() -> 0xFF1565C0.toInt()                                // word：蓝
        item.isExcel() -> 0xFF2E7D32.toInt()                                  // excel：绿
        item.isPPT() -> 0xFFEF6C00.toInt()                                    // ppt：橙
        item.isTxt() -> 0xFF546E7A.toInt()                                    // txt：蓝灰
        item.isMarkdown() -> 0xFF37474F.toInt()                               // markdown：深蓝灰
        item.isXmind() || item.isFreemind() -> 0xFF00695C.toInt()             // 思维导图：青
        item.isApk() -> 0xFF00897B.toInt()                                    // apk：青绿
        item.isArchive() -> 0xFF6D4C41.toInt()                                // 压缩包：棕
        else -> 0xFF424242.toInt()                                            // 其它：中性灰
    }

    /** 点结果格：能预览的走预览弹窗，其它（音频/文档/压缩包）给个 toast 说明点了什么 */
    private fun previewItem(item: MediaEntity) {
        XLog.d("MainActivity", "Clicked on item: ${item.path}")
        if (item.isImage() || item.isVideo() || item.isGif()) {
            if (item.isVideo()) {
                VideoPreviewDialog(
                    context = this, title = item.name, videoUrl = item.path,
                ).showPopupWindow()
            } else {
                ImagePreviewDialog(
                    context = this, title = item.name, url = item.path
                ).showPopupWindow()
            }
        } else {
            Toast.makeText(
                this,
                "${item.extLabel()}  ${FilePickerTimeFormatUtils.formatFileSize(item.size)}\n${item.name ?: item.path}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** 结果区：有结果才显示（标题 + 网格一起） */
    private fun renderResult(list: List<MediaEntity>) {
        val hasResult = list.isNotEmpty()
        binding.tvResultTitle.isVisible = hasResult
        binding.recyclerView.isVisible = hasResult
        if (hasResult) {
            binding.recyclerView.models = list
        } else {
            binding.recyclerView.models = emptyList<MediaEntity>()
        }
    }

    private var selectedDataList = mutableListOf<MediaEntity>()

    /** 类型单选列表 */
    private data class PickerType(val label: String, val type: String)

    private var selectedType: String = FilePickerSelectType.ofAll()

    private fun initTypeList() {
        val types = listOf(
            PickerType("全部", FilePickerSelectType.ofAll()),
            PickerType("全部含GIF", FilePickerSelectType.ofAllWithGif()),
            PickerType("图片", FilePickerSelectType.ofImage()),
            PickerType("视频", FilePickerSelectType.ofVideo()),
            PickerType("GIF", FilePickerSelectType.ofGif()),
            PickerType("音频", FilePickerSelectType.ofAudio()),
            PickerType("文档", FilePickerSelectType.ofDocument()),
            PickerType("pdf", FilePickerSelectType.ofPdf()),
            PickerType("doc", FilePickerSelectType.ofDoc()),
            PickerType("ppt", FilePickerSelectType.ofPpt()),
            PickerType("excel", FilePickerSelectType.ofExcel()),
            PickerType("txt", FilePickerSelectType.ofTxt()),
            // 新加的文档类型：按后缀识别（xmind 本质是 zip、md 可能被报成 text/plain，
            // 所以不走 mime 轨），这里给个入口方便验证
            PickerType("markdown", FilePickerSelectType.ofMarkdown()),
            PickerType("xmind", FilePickerSelectType.ofXmind()),
            PickerType("freemind", FilePickerSelectType.ofFreemind()),
            PickerType("APK", FilePickerSelectType.ofApk()),
            PickerType("zip_all", FilePickerSelectType.ofZipAll()),
            PickerType("zip", FilePickerSelectType.ofZip()),
            PickerType("rar", FilePickerSelectType.ofRar()),
            PickerType("7z", FilePickerSelectType.of7Z()),
            // 自定义后缀过滤示例：任意后缀组合，不再需要为每种后缀加 API
            PickerType("自定义(xz,br)", FilePickerSelectType.ofExtensions("xz", "br")),
            PickerType("自定义(GZ,TGZ)", FilePickerSelectType.ofExtensions("gz,tgz")),
            PickerType("自定义(jpg,jpeg)", FilePickerSelectType.ofExtensions("jpg,jpeg")),
        )
        binding.rvType.layoutManager = GridLayoutManager(this, 2, GridLayoutManager.HORIZONTAL, false)
        binding.rvType.divider {
            setDivider(8, true)
        }.setup {
            addType<PickerType>(R.layout.item_type_chip)
            onBind {
                val item = getModel<PickerType>()
                val itemBinding = getBinding<ItemTypeChipBinding>()
                itemBinding.tvTypeName.text = item.label
                val selected = item.type == selectedType
                itemBinding.tvTypeName.isChecked = selected
//                itemBinding.tvTypeName.setBackgroundColor(Color.TRANSPARENT)
//                itemBinding.tvTypeName.setTextColor(if (selected) 0xFF2196F3.toInt() else 0xFFCCCCCC.toInt())
                itemBinding.tvTypeName.paint.isFakeBoldText = selected
                itemBinding.tvTypeName.setOnClickListener {
                    if (selectedType != item.type) {
                        selectedType = item.type
                        binding.rvType.adapter?.notifyDataSetChanged()
                    }
                }
            }
        }
        binding.rvType.models = types
    }

    fun selectFile(type: String, maxSelectCount: Int, maxFileSize: Int, showCameraEntry: Boolean) {
        FilePicker.with(this).setMaxSelectNumber(maxSelectCount)
            .setMaxFileSize(maxFileSize.toLong()).setSelectType(type)
            .setShowCameraEntry(showCameraEntry)
            .setSingleClickEnable(true)
            .setSelectedList(selectedDataList).setOnSelectCallback { isUseOriginal, list ->
                XLog.d("FilePicker", "Selected files: ${list.size}")
                selectedDataList = list
                renderResult(list)
            }.setUiConfig(
                FilePickerUIConfig(
                    isHideSelectTab = false,
                    allAlbumName = "全部",
                    confirmBtnText = "发送",
                    isShowOriginal = false,
                    isPreviewPageIndexMode = true,
                    isShowSelectedListDeleteIcon = true,
                    folderNickNameMap = hashMapOf(
                        "DCIM" to "相册", "Download" to "下载"
                    )
                )
            ).start()
    }

}