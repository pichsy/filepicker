package com.pichs.filepicker.demo.tabs

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.drake.brv.utils.grid
import com.drake.brv.utils.models
import com.drake.brv.utils.setup
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.pichs.filepicker.FilePicker
import com.pichs.filepicker.FilePickerUIConfig
import com.pichs.filepicker.common.ImagePreviewDialog
import com.pichs.filepicker.common.VideoPreviewDialog
import com.pichs.filepicker.demo.R
import com.pichs.filepicker.demo.databinding.ItemResultFileBinding
import com.pichs.filepicker.demo.databinding.ItemResultMediaBinding
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.loader.MediaLoader
import com.pichs.filepicker.utils.FilePickerTimeFormatUtils
import com.pichs.xbase.xlog.XLog
import com.pichs.xwidget.utils.XDisplayHelper

/**
 * 测试页基类：
 * - 负责「选项区 + 开始按钮 + 结果网格」的公共部分
 * - 子类只关心 buildOptions()（往选项区加控件）和 onStartClicked()（读控件值、起选择器）
 */
abstract class BaseTestFragment : Fragment(R.layout.fragment_test_base) {

    protected lateinit var optionsContainer: LinearLayout
    private lateinit var btnStart: Button
    private lateinit var tvResultTitle: TextView
    private lateinit var recyclerView: RecyclerView

    protected var selectedDataList: MutableList<MediaEntity> = mutableListOf()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        optionsContainer = view.findViewById(R.id.options_container)
        btnStart = view.findViewById(R.id.btn_start)
        tvResultTitle = view.findViewById(R.id.tv_result_title)
        recyclerView = view.findViewById(R.id.recycler_view)

        initRecycler()
        buildOptions(optionsContainer)
        btnStart.setOnClickListener { onStartClicked() }
    }

    /** 往选项区加控件 */
    abstract fun buildOptions(container: LinearLayout)

    /** 用户点「开始」：自己读选项、构造 Builder、start() */
    abstract fun onStartClicked()

    // ---------------------------------------------------------------- 公共 Builder

    protected fun demoUiConfig(): FilePickerUIConfig = FilePickerUIConfig(
        isHideSelectTab = false,
        allAlbumName = "全部",
        confirmBtnText = "发送",
        isShowOriginal = false,
        isPreviewPageIndexMode = true,
        isShowSelectedListDeleteIcon = true,
        folderNickNameMap = hashMapOf("DCIM" to "相册", "Download" to "下载"),
    )

    /** 创建一个带公共配置（会话结果回调 + UI 配置）的 Builder；用 Fragment 发起 */
    protected fun newBuilder(type: String): FilePicker.Builder = FilePicker.with(this)
        .setSelectType(type)
        .setSelectedList(selectedDataList)
        .setUiConfig(demoUiConfig())
        .setOnSelectCallback { _, list ->
            XLog.d("BaseTestFragment", "选择结果: ${list.size}")
            selectedDataList = list
            renderResult(list)
        }

    // ---------------------------------------------------------------- 权限

    protected fun runWithMediaPermission(block: () -> Unit) {
        XXPermissions.with(requireActivity()).unchecked().permission(
            Permission.READ_MEDIA_IMAGES,
            Permission.READ_MEDIA_VIDEO,
            Permission.READ_MEDIA_AUDIO,
        ).request { permissions, all ->
            if (all) block() else XXPermissions.startPermissionActivity(requireActivity(), permissions)
        }
    }

    protected fun runWithAllFilesPermission(block: () -> Unit) {
        XXPermissions.with(requireActivity()).unchecked()
            .permission(Permission.MANAGE_EXTERNAL_STORAGE)
            .request { permissions, all ->
                if (all) block() else XXPermissions.startPermissionActivity(requireActivity(), permissions)
            }
    }

    // ---------------------------------------------------------------- 选项控件helper

    protected fun LinearLayout.section(title: String) {
        val tv = TextView(requireContext()).apply {
            text = title
            setTextColor(0xFF9E9E9E.toInt())
            textSize = 13f
            setPadding(0, dp(14), 0, dp(4))
        }
        addView(tv)
    }

    protected fun LinearLayout.check(text: String, checked: Boolean = false): CheckBox {
        val cb = CheckBox(requireContext()).apply {
            this.text = text
            isChecked = checked
            setTextColor(0xFFDDDDDD.toInt())
            textSize = 14f
            buttonTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
        }
        addView(cb)
        return cb
    }

    protected fun LinearLayout.radio(labels: List<String>, checkedIndex: Int = 0): RadioGroup {
        val rg = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.START
        }
        labels.forEachIndexed { index, label ->
            val rb = RadioButton(requireContext()).apply {
                text = label
                tag = index
                setTextColor(0xFFDDDDDD.toInt())
                textSize = 13f
                buttonTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
                if (index == checkedIndex) isChecked = true
            }
            rg.addView(rb)
        }
        addView(rg)
        return rg
    }

    protected fun RadioGroup.selectedIndex(): Int {
        val rb = findViewById<RadioButton>(checkedRadioButtonId) ?: return 0
        return (rb.tag as? Int) ?: 0
    }

    protected fun LinearLayout.input(hint: String, default: String = "", number: Boolean = true): EditText {
        val et = EditText(requireContext()).apply {
            this.hint = hint
            setText(default)
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0x99FFFFFF.toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0x22FFFFFF)
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            inputType = if (number) {
                android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            } else {
                android.text.InputType.TYPE_CLASS_TEXT
            }
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(44)
        ).apply { topMargin = dp(6) }
        addView(et, lp)
        return et
    }

    /** 一行标题 + 输入框（输入框 hint 在填入数字后会消失，所以给标题） */
    protected fun LinearLayout.labeledInput(
        label: String,
        hint: String,
        default: String = "",
        number: Boolean = true,
    ): EditText {
        addView(TextView(requireContext()).apply {
            text = label
            setTextColor(0xFFBBBBBB.toInt())
            textSize = 13f
        })
        return input(hint, default, number)
    }

    /** 可选中的类型 chip 网格（GridLayout，默认 4 列）；选中项通过 [selectedChip] 读 */
    protected fun LinearLayout.chipGrid(
        items: List<String>,
        columns: Int = 4,
        initial: Int = 0,
    ): android.widget.GridLayout {
        val grid = android.widget.GridLayout(requireContext()).apply {
            columnCount = columns
            tag = intArrayOf(initial)
        }
        val chips = mutableListOf<TextView>()
        fun styleChip(tv: TextView, selected: Boolean) {
            tv.background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(if (selected) 0xFF06B10D.toInt() else 0x22FFFFFF)
            }
            tv.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFFDDDDDD.toInt())
        }
        fun refresh() {
            val sel = (grid.tag as IntArray)[0]
            chips.forEachIndexed { index, tv -> styleChip(tv, index == sel) }
        }
        items.forEachIndexed { index, label ->
            val tv = TextView(requireContext()).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 12f
                setPadding(dp(2), dp(10), dp(2), dp(10))
                setOnClickListener {
                    (grid.tag as IntArray)[0] = index
                    refresh()
                }
            }
            styleChip(tv, index == initial)
            val lp = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = android.widget.GridLayout.spec(index % columns, 1f)
                rowSpec = android.widget.GridLayout.spec(index / columns)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            grid.addView(tv, lp)
            chips.add(tv)
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) }
        addView(grid, lp)
        return grid
    }

    protected fun selectedChip(grid: android.widget.GridLayout): Int =
        (grid.tag as? IntArray)?.get(0) ?: 0

    /** 预设 chip（点一下触发回调，不维护选中态；用于「常用后缀」） */
    protected fun LinearLayout.presetGrid(
        items: List<String>,
        columns: Int = 4,
        onClick: (String) -> Unit,
    ): android.widget.GridLayout {
        val grid = android.widget.GridLayout(requireContext()).apply { columnCount = columns }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) }
        addView(grid, lp)
        items.forEachIndexed { index, item ->
            val tv = TextView(requireContext()).apply {
                text = item
                gravity = Gravity.CENTER
                textSize = 12f
                setPadding(dp(2), dp(8), dp(2), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(0x22FFFFFF)
                }
                setTextColor(0xFFDDDDDD.toInt())
                setOnClickListener { onClick(item) }
            }
            val glp = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = android.widget.GridLayout.spec(index % columns, 1f)
                rowSpec = android.widget.GridLayout.spec(index / columns)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            grid.addView(tv, glp)
        }
        return grid
    }

    /** 纯文本说明（多行，灰色） */
    protected fun LinearLayout.label(text: String) {
        addView(TextView(requireContext()).apply {
            this.text = text
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 13f
            setLineSpacing(0f, 1.1f)
            setPadding(0, dp(4), 0, dp(2))
        })
    }

    protected fun LinearLayout.space(heightDp: Int = 6) {
        addView(View(requireContext()), LinearLayout.LayoutParams(1, dp(heightDp)))
    }

    protected fun dp(v: Int): Int = XDisplayHelper.dp2px(requireContext(), v.toFloat())

    // ---------------------------------------------------------------- 结果展示

    private fun initRecycler() {
        recyclerView.grid(4).setup {
            addType<MediaEntity> {
                if (isImage() || isVideo() || isGif()) R.layout.item_result_media
                else R.layout.item_result_file
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

    protected fun renderResult(list: List<MediaEntity>) {
        tvResultTitle.isVisible = list.isNotEmpty()
        val editedCount = list.count { it.isEdited() }
        tvResultTitle.text = if (editedCount > 0) {
            "选择结果（${list.size} 张，${editedCount} 张已编辑）"
        } else {
            "选择结果（${list.size} 张）"
        }
        recyclerView.models = list
    }

    private fun bindMediaCell(binding: ItemResultMediaBinding, item: MediaEntity, index: Int) {
        binding.tvIndex.text = "$index"
        val displayItem = if (item.isEdited()) item.copy(path = item.editedPath, uri = null) else item
        MediaLoader.loadThumbnail(displayItem, binding.ivCover)
        binding.ivEditedBadge.isVisible = item.isEdited()
        val isVideo = item.isVideo()
        binding.ivPlay.isVisible = isVideo
        binding.tvDuration.isVisible = isVideo
        binding.tvDuration.text = FilePickerTimeFormatUtils.formatTimeMillSeconds(item.duration)
        binding.tvGif.isVisible = item.isGif()
        binding.root.setOnClickListener { previewItem(item) }
        binding.ivDelete.setOnClickListener { removeResult(item) }
    }

    private fun bindFileCell(binding: ItemResultFileBinding, item: MediaEntity, index: Int) {
        binding.tvIndex.text = "$index"
        binding.tvExt.text = item.extLabel()
        binding.tvName.text = item.name ?: item.path?.substringAfterLast('/').orEmpty()
        binding.flFile.background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(fileCellColor(item))
        }
        binding.root.setOnClickListener { previewItem(item) }
        binding.ivDelete.setOnClickListener { removeResult(item) }
    }

    private fun previewItem(item: MediaEntity) {
        if (item.isVideo()) {
            VideoPreviewDialog(context = requireContext(), title = item.name, videoUrl = item.path).showPopupWindow()
        } else if (item.isImage() || item.isGif()) {
            ImagePreviewDialog(context = requireContext(), title = item.name, url = item.path).showPopupWindow()
        } else {
            Toast.makeText(
                requireContext(),
                "${item.extLabel()}  ${FilePickerTimeFormatUtils.formatFileSize(item.size)}\n${item.name ?: item.path}",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

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

    /** 色块颜色按类型给，同类同色 */
    private fun fileCellColor(item: MediaEntity): Int = when {
        item.isAudio() -> 0xFF6A1B9A.toInt()
        item.isPdf() -> 0xFFC62828.toInt()
        item.isWordDoc() -> 0xFF1565C0.toInt()
        item.isExcel() -> 0xFF2E7D32.toInt()
        item.isPPT() -> 0xFFEF6C00.toInt()
        item.isTxt() -> 0xFF546E7A.toInt()
        item.isMarkdown() -> 0xFF37474F.toInt()
        item.isXmind() || item.isFreemind() -> 0xFF00695C.toInt()
        item.isApk() -> 0xFF00897B.toInt()
        item.isArchive() -> 0xFF6D4C41.toInt()
        else -> 0xFF424242.toInt()
    }
}
