package com.pichs.filepicker.demo.tabs

import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.pichs.filepicker.FilePicker
import com.pichs.filepicker.FilePickerSelectType
import com.pichs.filepicker.select.SelectRule


/**
 * 自定义比例输入框：用户输入完一段数字后（停顿 600ms），自动补一个冒号，
 * 方便直接输入下一个数字；继续输入时重新计时，多位数不会被截断。
 */
private fun EditText.enableSmartRatioInput() {
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    var pending: Runnable? = null
    doAfterTextChanged {
        pending?.let(handler::removeCallbacks)
        pending = Runnable {
            val current = text.toString()
            if (current.isNotEmpty() && current.matches(Regex("^\\d+$"))) {
                setText("$current:")
                setSelection(text.length)
            }
        }.also { handler.postDelayed(it, 600) }
    }
}

/** 解析 "21:9" / "21/9" / "21x9" 这类自定义比例；非法返回 null */
private fun parseCustomRatio(text: String): Float? {
    val raw = text.trim()
    if (raw.isEmpty()) return null
    val parts = raw.split(':', '/', 'x', 'X', '：')
    if (parts.size != 2) return null
    val w = parts[0].trim().toFloatOrNull() ?: return null
    val h = parts[1].trim().toFloatOrNull() ?: return null
    if (w <= 0f || h <= 0f) return null
    return w / h
}

/** 普通选择：类型 / 数量 / 大小 / 相机入口 / 预览可编辑 */
class BasicSelectFragment : BaseTestFragment() {
    private lateinit var rgType: RadioGroup
    private lateinit var etMax: EditText
    private lateinit var etSizeMb: EditText
    private lateinit var cbCamera: android.widget.CheckBox
    private lateinit var cbPreviewEdit: android.widget.CheckBox

    override fun buildOptions(container: LinearLayout) {
        container.section("选择类型")
        rgType = container.radio(listOf("图片", "视频", "图片+视频", "全部(含GIF)"), 0)
        container.section("限制")
        etMax = container.labeledInput("最大数量（0 = 不限）", "输入数字，如 9", "9")
        etSizeMb = container.labeledInput("最大文件大小 MB", "输入数字，如 200", "200")
        container.section("附加能力")
        cbCamera = container.check("显示相机入口", false)
        cbPreviewEdit = container.check("预览页可编辑（编辑弹窗）", false)
    }

    override fun onStartClicked() {
        runWithMediaPermission {
            val type = when (rgType.selectedIndex()) {
                1 -> FilePickerSelectType.ofVideo()
                2 -> FilePickerSelectType.ofAll()
                3 -> FilePickerSelectType.ofAllWithGif()
                else -> FilePickerSelectType.ofImage()
            }
            val max = etMax.text.toString().toIntOrNull() ?: 0
            val sizeMb = etSizeMb.text.toString().toIntOrNull() ?: 200
            newBuilder(type)
                .setMaxSelectNumber(max)
                .setMaxFileSize(sizeMb * 1024L * 1024L)
                .setShowCameraEntry(cbCamera.isChecked)
                .setEditInPreviewEnable(cbPreviewEdit.isChecked)
                .start()
        }
    }
}

/** 规则模式：分类型限量 / 互斥 / GIF 占额度 */
class RulesFragment : BaseTestFragment() {
    private lateinit var rgType: RadioGroup
    private lateinit var etImageMin: EditText
    private lateinit var etImageMax: EditText
    private lateinit var etVideoMin: EditText
    private lateinit var etVideoMax: EditText
    private lateinit var cbExclusive: android.widget.CheckBox
    private lateinit var cbGifAsImage: android.widget.CheckBox

    override fun buildOptions(container: LinearLayout) {
        container.section("选择类型")
        rgType = container.radio(listOf("图片+视频", "图片+视频+GIF"), 0)
        container.section("图片规则")
        etImageMax = container.labeledInput("图片最多选几张（空 = 不限）", "输入数字，如 9", "9")
        etImageMin = container.labeledInput("图片最少选几张（空 = 不强制）", "输入数字，如 3")
        container.section("视频规则")
        etVideoMax = container.labeledInput("视频最多选几个（空 = 不限）", "输入数字，如 1", "1")
        etVideoMin = container.labeledInput("视频最少选几个（空 = 不强制）", "输入数字，如 1")
        etImageMin.doAfterTextChanged { validateMinMax(etImageMin, etImageMax) }
        etImageMax.doAfterTextChanged { validateMinMax(etImageMin, etImageMax) }
        etVideoMin.doAfterTextChanged { validateMinMax(etVideoMin, etVideoMax) }
        etVideoMax.doAfterTextChanged { validateMinMax(etVideoMin, etVideoMax) }
        container.section("其它")
        cbExclusive = container.check("互斥（图片/视频只能选一类）", false)
        cbGifAsImage = container.check("GIF 占用图片额度", true)
    }

    /** 最小值 > 最大值时把两个输入框文字标红提示；开始前也会拦截 */
    private fun validateMinMax(minEt: EditText, maxEt: EditText) {
        val min = minEt.text.toString().toIntOrNull()
        val max = maxEt.text.toString().toIntOrNull()
        val invalid = min != null && max != null && min > max
        val normal = 0xFFFFFFFF.toInt()
        val error = 0xFFFF5252.toInt()
        minEt.setTextColor(if (invalid) error else normal)
        maxEt.setTextColor(if (invalid) error else normal)
    }

    private fun minGreaterThanMax(min: Int?, max: Int?): Boolean =
        min != null && max != null && min > max

    override fun onStartClicked() {
        val imageMin = etImageMin.text.toString().toIntOrNull()
        val imageMax = etImageMax.text.toString().toIntOrNull()
        val videoMin = etVideoMin.text.toString().toIntOrNull()
        val videoMax = etVideoMax.text.toString().toIntOrNull()
        if (imageMin == null && imageMax == null && videoMin == null && videoMax == null) {
            Toast.makeText(requireContext(), "至少配一条规则", Toast.LENGTH_SHORT).show()
            return
        }
        if (minGreaterThanMax(imageMin, imageMax) || minGreaterThanMax(videoMin, videoMax)) {
            Toast.makeText(requireContext(), "最小值不能大于最大值", Toast.LENGTH_SHORT).show()
            return
        }
        runWithMediaPermission {
            val rules = ArrayList<SelectRule>()
            if (imageMin != null || imageMax != null) {
                rules.add(SelectRule.ofImage(imageMin ?: 0, imageMax ?: Int.MAX_VALUE))
            }
            if (videoMin != null || videoMax != null) {
                rules.add(SelectRule.ofVideo(videoMin ?: 0, videoMax ?: Int.MAX_VALUE))
            }
            val type = if (rgType.selectedIndex() == 1) {
                FilePickerSelectType.ofAllWithGif()
            } else {
                FilePickerSelectType.ofAll()
            }
            newBuilder(type)
                .setSelectRules(*rules.toTypedArray())
                .setExclusive(cbExclusive.isChecked)
                .setGifCountsAsImage(cbGifAsImage.isChecked)
                .start()
        }
    }
}

/** 文件类型：列出库支持的全部类型（4 列网格），自定义后缀提供常用预设 */
class FileTypeFragment : BaseTestFragment() {
    private lateinit var typeGrid: GridLayout
    private lateinit var etExt: EditText
    private lateinit var etMax: EditText

    /** 与 FilePickerSelectType 支持的类型一一对应 */
    private val typeLabels = listOf(
        "图片", "视频", "图片+视频", "全部+GIF",
        "GIF", "音频", "文档", "PDF",
        "Word", "PPT", "Excel", "TXT",
        "APK", "压缩包", "ZIP", "RAR", "自定义",
    )

    /** 常用后缀预设（点一下追加到输入框） */
    private val presetExts = listOf(
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "txt",
        "md", "json", "xml", "csv", "log", "zip", "rar", "7z",
        "apk", "java", "kt", "mp3",
    )

    override fun buildOptions(container: LinearLayout) {
        container.section("文件类型（库支持的全部类型）")
        typeGrid = container.chipGrid(typeLabels, columns = 4, initial = 0)
        container.section("自定义后缀")
        etExt = container.labeledInput(
            "后缀（逗号分隔；点下面预设可直接填入）",
            "如 txt,json,docx",
            "",
            number = false,
        )
        container.presetGrid(presetExts, columns = 4) { ext ->
            val current = etExt.text.toString().trim()
            val list = current.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if (!list.contains(ext)) list.add(ext)
            etExt.setText(list.joinToString(","))
        }
        container.section("限制")
        etMax = container.labeledInput("最大数量（0 = 不限）", "输入数字，如 1", "1")
    }

    override fun onStartClicked() {
        val index = selectedChip(typeGrid)
        val mediaType = index in 0..4
        val action: () -> Unit = action@ {
            val type = when (index) {
                0 -> FilePickerSelectType.ofImage()
                1 -> FilePickerSelectType.ofVideo()
                2 -> FilePickerSelectType.ofAll()
                3 -> FilePickerSelectType.ofAllWithGif()
                4 -> FilePickerSelectType.ofGif()
                5 -> FilePickerSelectType.ofAudio()
                6 -> FilePickerSelectType.ofDocument()
                7 -> FilePickerSelectType.ofPdf()
                8 -> FilePickerSelectType.ofDoc()
                9 -> FilePickerSelectType.ofPpt()
                10 -> FilePickerSelectType.ofExcel()
                11 -> FilePickerSelectType.ofTxt()
                12 -> FilePickerSelectType.ofApk()
                13 -> FilePickerSelectType.ofZipAll()
                14 -> FilePickerSelectType.ofZip()
                15 -> FilePickerSelectType.ofRar()
                else -> {
                    val exts = etExt.text.toString().split(',', '，')
                        .map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
                    if (exts.isEmpty()) {
                        Toast.makeText(requireContext(), "请填写或点选后缀", Toast.LENGTH_SHORT).show()
                        return@action
                    }
                    FilePickerSelectType.ofExtensions(*exts)
                }
            }
            val max = etMax.text.toString().toIntOrNull() ?: 0
            newBuilder(type).setMaxSelectNumber(max).start()
        }
        if (mediaType) runWithMediaPermission { action() } else runWithAllFilesPermission { action() }
    }
}

/** 头像裁剪：比例（含自定义）/ 可拖拽 / 不锁比例 / 初始大小 */
class AvatarCropFragment : BaseTestFragment() {
    private lateinit var rgRatio: RadioGroup
    private lateinit var etCustomRatio: EditText
    private lateinit var cbResizable: android.widget.CheckBox
    private lateinit var cbUnlocked: android.widget.CheckBox
    private lateinit var etScale: EditText

    override fun buildOptions(container: LinearLayout) {
        container.section("裁剪比例")
        rgRatio = container.radio(listOf("1:1", "1:2", "3:4", "16:9", "自由", "自定义"), 0)
        etCustomRatio = container.labeledInput(
            "自定义比例",
            "宽:高，例如 21:9",
            "",
            number = false,
        )
        etCustomRatio.isVisible = false
        etCustomRatio.enableSmartRatioInput()
        rgRatio.setOnCheckedChangeListener { _, _ ->
            etCustomRatio.isVisible = rgRatio.selectedIndex() == 5
        }
        container.section("裁剪框")
        cbResizable = container.check("允许拖拽改变裁剪框大小", false)
        cbUnlocked = container.check("拖拽时不锁比例（可拉变形）", false)
        etScale = container.labeledInput(
            "初始框大小（屏幕短边比例，0.05~1）",
            "例如 0.75",
            "0.75",
        )
    }

    override fun onStartClicked() {
        runWithMediaPermission {
            val customSelected = rgRatio.selectedIndex() == 5
            val presetAspect = when (rgRatio.selectedIndex()) {
                1 -> 0.5f
                2 -> 0.75f
                3 -> 16f / 9f
                4 -> 0f
                else -> 1f
            }
            val custom = parseCustomRatio(etCustomRatio.text.toString())
            if (customSelected && custom == null) {
                Toast.makeText(requireContext(), "请输入自定义比例，例如 21:9", Toast.LENGTH_SHORT).show()
                return@runWithMediaPermission
            }
            val aspect = if (customSelected) custom!! else presetAspect
            val scale = etScale.text.toString().toFloatOrNull() ?: 0.75f
            newBuilder(FilePickerSelectType.ofImage())
                .setMaxSelectNumber(1)
                .setAvatarCropMode(true)
                .setAvatarCropAspectRatio(aspect)
                .setAvatarCropResizable(cbResizable.isChecked)
                .setAvatarCropRatioLocked(!cbUnlocked.isChecked)
                .setAvatarCropFrameScale(scale)
                .start()
        }
    }
}

/** 点击即编辑：点任意图直接进编辑弹窗 */
class EditOnClickFragment : BaseTestFragment() {
    override fun buildOptions(container: LinearLayout) {
        container.section("点击即编辑")
        container.label(
            "进入选择器后：点任意图片 → 直接弹出编辑弹窗（画笔/文字/裁剪/马赛克）。\n" +
                "「完成」→ 返回编辑后的成品；「取消」→ 回图库继续选。",
        )
    }

    override fun onStartClicked() {
        runWithMediaPermission {
            newBuilder(FilePickerSelectType.ofImage())
                .setMaxSelectNumber(1)
                .setEditOnClickEnable(true)
                .start()
        }
    }
}

/** 预览页编辑：先选，再在预览里点「编辑」 */
class EditInPreviewFragment : BaseTestFragment() {
    private lateinit var rgType: RadioGroup
    private lateinit var cbEnable: android.widget.CheckBox
    private lateinit var etMax: EditText

    override fun buildOptions(container: LinearLayout) {
        container.section("预览页编辑")
        rgType = container.radio(listOf("图片", "图片+视频"), 0)
        etMax = container.labeledInput("最大数量（0 = 不限）", "输入数字，如 9", "9")
        cbEnable = container.check("开启预览页编辑入口", true)
    }

    override fun onStartClicked() {
        runWithMediaPermission {
            val type = if (rgType.selectedIndex() == 1) {
                FilePickerSelectType.ofAll()
            } else {
                FilePickerSelectType.ofImage()
            }
            val max = etMax.text.toString().toIntOrNull() ?: 0
            newBuilder(type)
                .setMaxSelectNumber(max)
                .setEditInPreviewEnable(cbEnable.isChecked)
                .start()
        }
    }
}

/** 拍照直出：拍照→编辑 / 仅拍照 / 拍照→头像裁剪（含自定义比例） */
class DirectCameraFragment : BaseTestFragment() {
    private lateinit var rgMode: RadioGroup
    private lateinit var rgRatio: RadioGroup
    private lateinit var etCustomRatio: EditText
    private lateinit var cbResizable: android.widget.CheckBox
    private lateinit var cbUnlocked: android.widget.CheckBox
    private lateinit var etScale: EditText

    override fun buildOptions(container: LinearLayout) {
        container.section("拍摄后行为")
        rgMode = container.radio(listOf("拍照→编辑", "仅拍照", "拍照→头像裁剪"), 0)
        container.section("头像裁剪参数（仅选「拍照→头像裁剪」时生效）")
        rgRatio = container.radio(listOf("1:1", "1:2", "3:4", "16:9", "自由", "自定义"), 0)
        etCustomRatio = container.labeledInput(
            "自定义比例",
            "宽:高，例如 21:9",
            "",
            number = false,
        )
        etCustomRatio.isVisible = false
        etCustomRatio.enableSmartRatioInput()
        rgRatio.setOnCheckedChangeListener { _, _ ->
            etCustomRatio.isVisible = rgRatio.selectedIndex() == 5
        }
        cbResizable = container.check("允许拖拽改变裁剪框大小", false)
        cbUnlocked = container.check("拖拽时不锁比例", false)
        etScale = container.labeledInput("初始框大小（屏幕短边比例，0.05~1）", "例如 0.75", "0.75")
    }

    override fun onStartClicked() {
        runWithMediaPermission {
            val builder = newBuilder(FilePickerSelectType.ofImage()).setOpenCameraDirect(true)
            when (rgMode.selectedIndex()) {
                1 -> Unit // 仅拍照
                2 -> {
                    val customSelected = rgRatio.selectedIndex() == 5
                    val presetAspect = when (rgRatio.selectedIndex()) {
                        1 -> 0.5f
                        2 -> 0.75f
                        3 -> 16f / 9f
                        4 -> 0f
                        else -> 1f
                    }
                    val custom = parseCustomRatio(etCustomRatio.text.toString())
                    if (customSelected && custom == null) {
                        Toast.makeText(requireContext(), "请输入自定义比例，例如 21:9", Toast.LENGTH_SHORT).show()
                        return@runWithMediaPermission
                    }
                    val aspect = if (customSelected) custom!! else presetAspect
                    val scale = etScale.text.toString().toFloatOrNull() ?: 0.75f
                    builder.setAvatarCropMode(true)
                        .setAvatarCropAspectRatio(aspect)
                        .setAvatarCropResizable(cbResizable.isChecked)
                        .setAvatarCropRatioLocked(!cbUnlocked.isChecked)
                        .setAvatarCropFrameScale(scale)
                }
                else -> builder.setEditOnClickEnable(true) // 拍照→编辑
            }
            builder.start()
        }
    }
}

/** 录像直出：直接录像 → 完成直接返回视频 */
class DirectVideoFragment : BaseTestFragment() {
    override fun buildOptions(container: LinearLayout) {
        container.section("录像直出")
        container.label(
            "进入后直接拉起系统相机录像；录完确定直接返回视频（无编辑）。\n" +
                "相机里取消 → 回到当前界面（onCancel）。",
        )
    }

    override fun onStartClicked() {
        runWithMediaPermission {
            newBuilder(FilePickerSelectType.ofVideo())
                .setOpenCameraDirect(true)
                .start()
        }
    }
}
