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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import com.pichs.filepicker.select.SelectRule
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

        // ===== 测试快捷入口：不用勾选，直接以对应模式进选择器 =====
        binding.btnTestEditOnClick.fastClick { startQuickTest("editOnClick") }
        binding.btnTestCameraEdit.fastClick { startQuickTest("cameraEdit") }
        binding.btnTestAvatar.fastClick { startQuickTest("avatar") }
        binding.btnTestVideoCamera.fastClick { startQuickTest("videoCamera") }
        binding.btnTestCaptureEdit.fastClick { startQuickTest("captureEdit") }
        binding.btnTestCaptureVideo.fastClick { startQuickTest("captureVideo") }

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
        initRuleModeList()
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
        // 编辑过的图：展示编辑产物（临时文件），身份仍是原图
        val displayItem = if (item.isEdited()) item.copy(path = item.editedPath, uri = null) else item
        MediaLoader.loadThumbnail(displayItem, binding.ivCover)

        // 已编辑角标（左下角）
        binding.ivEditedBadge.isVisible = item.isEdited()
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
        XLog.d("MainActivity", "Clicked on item: ${item.path}, edited: ${item.editedPath}")
        // 编辑过的显示编辑后的图（会话级覆盖，不改原图）
        val displayItem = if (item.isEdited()) item.copy(path = item.editedPath, uri = null) else item
        if (item.isImage() || item.isVideo() || item.isGif()) {
            if (item.isVideo()) {
                VideoPreviewDialog(
                    context = this, title = item.name, videoUrl = displayItem.path,
                ).showPopupWindow()
            } else {
                ImagePreviewDialog(
                    context = this, title = item.name, url = displayItem.path
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
        if (hasResult) {
            val editedCount = list.count { it.isEdited() }
            val editedSuffix = if (editedCount > 0) "，$editedCount 张已编辑" else ""
            binding.tvResultTitle.text = "选择结果（${list.size} 张$editedSuffix）"
        }
        binding.recyclerView.isVisible = hasResult
        if (hasResult) {
            binding.recyclerView.models = list
        } else {
            binding.recyclerView.models = emptyList<MediaEntity>()
        }
    }

    private var selectedDataList = mutableListOf<MediaEntity>()

    /**
     * 规则模式。
     * - [NONE]：不启用新模型，完全走旧的 `setMaxSelectNumber`；
     * - [COEXIST]：图片和视频可以一起选，各自受数量约束；
     * - [EXCLUSIVE]：图片和视频只能选其中一类。
     */
    private enum class RuleMode(val label: String) {
        NONE("无"),
        COEXIST("共存"),
        EXCLUSIVE("互斥"),
    }

    private var selectedRuleMode: RuleMode = RuleMode.NONE

    /** 读取数量输入框：空 / 非法 / <=0 一律视为"不限" */
    private fun countOf(editText: android.widget.EditText): Int =
        editText.text.toString().trim().toIntOrNull()?.takeIf { it > 0 } ?: 0

    /**
     * 三个参数 → 选择规则：
     * - 模式：无 → 不调 setSelectRules；共存/互斥 → 启用新模型（互斥额外 setExclusive(true)）；
     * - 图片数量 + 强制：强制 = 必须正好这么多（min=max=数量）；不强制 = 只作为上限（0..数量）；
     * - 视频数量 + 强制：同上。
     * 数量留空 = 该类型不限，不生成规则。
     */
    private fun buildSelectRules(): Pair<List<SelectRule>, Boolean> {
        val imageCount = countOf(binding.etImageCount)
        val videoCount = countOf(binding.etVideoCount)
        val imageForce = binding.cbImageForce.isChecked
        val videoForce = binding.cbVideoForce.isChecked
        val rules = buildList {
            if (imageCount > 0) {
                add(SelectRule.ofImage(if (imageForce) imageCount else 0, imageCount))
            }
            if (videoCount > 0) {
                add(SelectRule.ofVideo(if (videoForce) videoCount else 0, videoCount))
            }
        }
        // 关键：规则表为空时新模型不启用（setExclusive 也不生效），数量限制回退到「最大选择数量」
        val useRules = selectedRuleMode != RuleMode.NONE && rules.isNotEmpty()
        XLog.d(
            "FilePicker",
            "规则参数：模式=$selectedRuleMode 图片=$imageCount(强制=$imageForce) " +
                    "视频=$videoCount(强制=$videoForce) → $rules（新模型=$useRules，" +
                    "最大选择数量=${countOf(binding.etMaxSelectCount)}）"
        )
        return rules to useRules
    }

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

    /** 规则模式 chips：无 / 共存 / 互斥，单选，横向一行排列 */
    private fun initRuleModeList() {
        binding.rvRuleMode.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvRuleMode.divider {
            setDivider(8, true)
        }.setup {
            addType<RuleMode>(R.layout.item_type_chip)
            onBind {
                val item = getModel<RuleMode>()
                val itemBinding = getBinding<ItemTypeChipBinding>()
                itemBinding.tvTypeName.text = item.label
                val selected = item == selectedRuleMode
                itemBinding.tvTypeName.isChecked = selected
                itemBinding.tvTypeName.paint.isFakeBoldText = selected
                itemBinding.tvTypeName.setOnClickListener {
                    if (selectedRuleMode != item) {
                        selectedRuleMode = item
                        binding.rvRuleMode.adapter?.notifyDataSetChanged()
                        applyRuleModeVisibility()
                    }
                }
            }
        }
        binding.rvRuleMode.models = RuleMode.entries.toList()
        binding.etImageCount.doAfterTextChanged { applyRuleModeVisibility() }
        binding.etVideoCount.doAfterTextChanged { applyRuleModeVisibility() }
        applyRuleModeVisibility()
    }

    /**
     * 规则模式为「无」时：用「最大选择数量」（旧的单一上限），隐藏分类型输入；
     * 共存 / 互斥时反过来：合计上限由规则自动叠加，隐藏「最大选择数量」。
     */
    private fun applyRuleModeVisibility() {
        val modeSelected = selectedRuleMode != RuleMode.NONE
        // 只有真的填了数量才算"配了规则"；没填时新模型不启用，仍然由「最大选择数量」生效
        val hasRules = countOf(binding.etImageCount) > 0 || countOf(binding.etVideoCount) > 0
        val useRules = modeSelected && hasRules
        binding.layoutMaxCount.isVisible = !useRules
        binding.layoutImageCount.isVisible = modeSelected
        binding.layoutVideoCount.isVisible = modeSelected
    }

    /**
     * 测试快捷入口：直接以指定模式进入选择器，不受首页勾选项影响。
     *
     * - editOnClick  : 单图点击 → 直接进编辑弹窗，编辑完成返回
     * - cameraEdit   : 进入即拍照 → 拍完确定直接进编辑弹窗
     * - avatar       : 进入即头像裁剪（点图直接 1:1 裁剪）
     * - videoCamera  : 进入即录像 → 拍完确定直接返回视频（无编辑）
     */
    private fun startQuickTest(kind: String) {
        XXPermissions.with(this).unchecked().permission(
            Permission.READ_MEDIA_IMAGES,
            Permission.READ_MEDIA_VIDEO,
            Permission.READ_MEDIA_AUDIO,
        ).request { permissions, all ->
            if (!all) {
                XXPermissions.startPermissionActivity(this, permissions)
                return@request
            }
            val builder = FilePicker.with(this)
                .setMaxFileSize(Long.MAX_VALUE)
                .setSelectedList(selectedDataList)
                .setOnSelectCallback { _, list ->
                    XLog.d("MainActivity", "快捷测试结果: ${list.size} 张")
                    selectedDataList = list
                    renderResult(list)
                }
            when (kind) {
                "editOnClick" -> builder
                    .setSelectType(FilePickerSelectType.ofImage())
                    .setEditOnClickEnable(true)

                "cameraEdit" -> builder
                    .setSelectType(FilePickerSelectType.ofImage())
                    .setOpenCameraOnStart(true)
                    .setEditOnClickEnable(true)

                "avatar" -> builder
                    .setSelectType(FilePickerSelectType.ofImage())
                    .setAvatarCropMode(true)
                    .setAvatarCropAspectRatio(1f)

                "videoCamera" -> builder
                    .setSelectType(FilePickerSelectType.ofVideo())
                    .setOpenCameraOnStart(true)

                // 拍摄优先：完全不经过列表
                "captureEdit" -> builder
                    .setSelectType(FilePickerSelectType.ofImage())
                    .setCaptureFirst(true)
                    .setEditOnClickEnable(true)

                "captureVideo" -> builder
                    .setSelectType(FilePickerSelectType.ofVideo())
                    .setCaptureFirst(true)
            }
            builder.start()
        }
    }

    fun selectFile(type: String, maxSelectCount: Int, maxFileSize: Int, showCameraEntry: Boolean) {
        // 头像裁剪：单选 + 单击即裁剪（1:1 固定框）
        val avatarCrop = binding.cbAvatarCrop.isChecked
        // 预览页「编辑」：预览大图时自由裁剪
        val editInPreview = binding.cbEditPreview.isChecked
        // 点击图片直接编辑：单选，点图即弹编辑弹窗，完成后直接返回
        val editOnClick = binding.cbEditOnClick.isChecked
        // 进入即拍照/录像：直接拉起系统相机
        val openCameraOnStart = binding.cbOpenCamera.isChecked
        val (rules, useNewModel) = buildSelectRules()
        if (selectedRuleMode != RuleMode.NONE && !useNewModel) {
            Toast.makeText(this, "没填图片/视频数量，按「最大选择数量」执行", Toast.LENGTH_SHORT).show()
        }
        val effectiveMaxCount = if (avatarCrop) 1 else maxSelectCount
        val builder = FilePicker.with(this).setMaxSelectNumber(effectiveMaxCount)
            .setMaxFileSize(maxFileSize.toLong()).setSelectType(type)
            .setShowCameraEntry(showCameraEntry)
            // 新模型下总容量=1 会自动启用单击返回；为了让规则测试能看到勾选过程，
            // 这里只在旧模型（不限）时打开，和原来 maxSelectNumber!=1 时无效的行为一致
            .setSingleClickEnable(avatarCrop || !useNewModel)
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
            )

        if (avatarCrop) {
            // 头像裁剪：先开总开关，再设比例（不设默认 1:1）
            builder.setAvatarCropMode(true).setAvatarCropAspectRatio(1f)
        }
        if (editInPreview) {
            builder.setEditInPreviewEnable(true)
        }
        if (editOnClick) {
            builder.setEditOnClickEnable(true)
        }
        if (openCameraOnStart) {
            builder.setOpenCameraOnStart(true)
        }

        // 选用新模型：分类型限量 + 互斥；模式为"无"时完全走旧逻辑
        if (useNewModel) {
            builder.setSelectRules(*rules.toTypedArray())
            if (selectedRuleMode == RuleMode.EXCLUSIVE) {
                builder.setExclusive(true)
            }
        }
        builder.start()
    }

}