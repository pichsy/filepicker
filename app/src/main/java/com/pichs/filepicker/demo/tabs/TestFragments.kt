package com.pichs.filepicker.demo.tabs

import android.widget.LinearLayout
import android.widget.Toast
import com.pichs.filepicker.FilePicker
import com.pichs.filepicker.FilePickerSelectType
import com.pichs.filepicker.select.SelectRule

/** 普通选择：类型 / 数量 / 大小 / 相机入口 */
class BasicSelectFragment : BaseTestFragment() {
    private lateinit var rgType: android.widget.RadioGroup
    private lateinit var etMax: android.widget.EditText
    private lateinit var etSizeMb: android.widget.EditText
    private lateinit var cbCamera: android.widget.CheckBox
    private lateinit var cbPreviewEdit: android.widget.CheckBox

    override fun buildOptions(container: LinearLayout) {
        container.section("选择类型")
        rgType = container.radio(listOf("图片", "视频", "图片+视频", "全部"), 0)
        container.section("限制")
        etMax = container.input("最大数量（0 = 不限）", "9")
        etSizeMb = container.input("最大文件大小 MB（默认 200）", "200")
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
    private lateinit var rgType: android.widget.RadioGroup
    private lateinit var etImageMin: android.widget.EditText
    private lateinit var etImageMax: android.widget.EditText
    private lateinit var etVideoMin: android.widget.EditText
    private lateinit var etVideoMax: android.widget.EditText
    private lateinit var cbExclusive: android.widget.CheckBox
    private lateinit var cbGifAsImage: android.widget.CheckBox

    override fun buildOptions(container: LinearLayout) {
        container.section("选择类型")
        rgType = container.radio(listOf("图片+视频", "图片+视频+GIF"), 0)
        container.section("图片规则（空 = 不限 / 不强制）")
        etImageMin = container.input("图片最少选几张", "")
        etImageMax = container.input("图片最多选几张", "9")
        container.section("视频规则")
        etVideoMin = container.input("视频最少选几个", "")
        etVideoMax = container.input("视频最多选几个", "1")
        container.section("其它")
        cbExclusive = container.check("互斥（图片/视频只能选一类）", false)
        cbGifAsImage = container.check("GIF 占用图片额度", true)
    }

    override fun onStartClicked() {
        val imageMin = etImageMin.text.toString().toIntOrNull()
        val imageMax = etImageMax.text.toString().toIntOrNull()
        val videoMin = etVideoMin.text.toString().toIntOrNull()
        val videoMax = etVideoMax.text.toString().toIntOrNull()
        if (imageMin == null && imageMax == null && videoMin == null && videoMax == null) {
            Toast.makeText(requireContext(), "至少配一条规则", Toast.LENGTH_SHORT).show()
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

/** 文件类型：音频 / 文档 / PDF / APK / 压缩包 / 自定义后缀 */
class FileTypeFragment : BaseTestFragment() {
    private lateinit var rgType: android.widget.RadioGroup
    private lateinit var etExt: android.widget.EditText
    private lateinit var etMax: android.widget.EditText

    override fun buildOptions(container: LinearLayout) {
        container.section("文件类型")
        rgType = container.radio(listOf("音频", "文档", "PDF", "APK", "压缩包", "自定义"), 0)
        etExt = container.input("自定义后缀（逗号分隔，仅自定义时生效）", "txt,json", number = false)
        container.section("限制")
        etMax = container.input("最大数量（0 = 不限）", "1")
    }

    override fun onStartClicked() {
        runWithAllFilesPermission {
            val type = when (rgType.selectedIndex()) {
                1 -> FilePickerSelectType.ofDocument()
                2 -> FilePickerSelectType.ofPdf()
                3 -> FilePickerSelectType.ofApk()
                4 -> FilePickerSelectType.ofZipAll()
                5 -> {
                    val exts = etExt.text.toString().split(',', '，')
                        .map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
                    if (exts.isEmpty()) {
                        Toast.makeText(requireContext(), "请填写后缀", Toast.LENGTH_SHORT).show()
                        return@runWithAllFilesPermission
                    }
                    FilePickerSelectType.ofExtensions(*exts)
                }
                else -> FilePickerSelectType.ofAudio()
            }
            val max = etMax.text.toString().toIntOrNull() ?: 1
            newBuilder(type).setMaxSelectNumber(max).start()
        }
    }
}

/** 头像裁剪：比例 / 可拖拽 / 不锁比例 / 初始大小 */
class AvatarCropFragment : BaseTestFragment() {
    private lateinit var rgRatio: android.widget.RadioGroup
    private lateinit var cbResizable: android.widget.CheckBox
    private lateinit var cbUnlocked: android.widget.CheckBox
    private lateinit var etScale: android.widget.EditText

    override fun buildOptions(container: LinearLayout) {
        container.section("裁剪比例")
        rgRatio = container.radio(listOf("1:1", "1:2", "3:4", "16:9", "自由"), 0)
        container.section("裁剪框")
        cbResizable = container.check("允许拖拽改变裁剪框大小", false)
        cbUnlocked = container.check("拖拽时不锁比例（可拉变形）", false)
        etScale = container.input("初始框大小（屏幕短边比例，0.05~1，默认 0.75）", "0.75")
    }

    override fun onStartClicked() {
        runWithMediaPermission {
            val aspect = when (rgRatio.selectedIndex()) {
                1 -> 0.5f
                2 -> 0.75f
                3 -> 16f / 9f
                4 -> 0f
                else -> 1f
            }
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
        container.addView(android.widget.TextView(requireContext()).apply {
            text = "进入选择器后：点任意图片 → 直接弹出编辑弹窗（画笔/文字/裁剪/马赛克）。\n" +
                    "「完成」→ 返回编辑后的成品；「取消」→ 回图库继续选。"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 13f
        })
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
    private lateinit var rgType: android.widget.RadioGroup
    private lateinit var cbEnable: android.widget.CheckBox
    private lateinit var etMax: android.widget.EditText

    override fun buildOptions(container: LinearLayout) {
        container.section("预览页编辑")
        rgType = container.radio(listOf("图片", "图片+视频"), 0)
        etMax = container.input("最大数量（0 = 不限）", "9")
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

/** 拍照直出：拍照→编辑 / 仅拍照 / 拍照→头像裁剪 */
class DirectCameraFragment : BaseTestFragment() {
    private lateinit var rgMode: android.widget.RadioGroup
    private lateinit var rgRatio: android.widget.RadioGroup
    private lateinit var cbResizable: android.widget.CheckBox
    private lateinit var cbUnlocked: android.widget.CheckBox
    private lateinit var etScale: android.widget.EditText

    override fun buildOptions(container: LinearLayout) {
        container.section("拍摄后行为")
        rgMode = container.radio(listOf("拍照→编辑", "仅拍照", "拍照→头像裁剪"), 0)
        container.section("头像裁剪参数（仅选「拍照→头像裁剪」时生效）")
        rgRatio = container.radio(listOf("1:1", "1:2", "3:4", "16:9", "自由"), 0)
        cbResizable = container.check("允许拖拽改变裁剪框大小", false)
        cbUnlocked = container.check("拖拽时不锁比例", false)
        etScale = container.input("初始框大小（默认 0.75）", "0.75")
    }

    override fun onStartClicked() {
        runWithMediaPermission {
            val builder = newBuilder(FilePickerSelectType.ofImage()).setOpenCameraDirect(true)
            when (rgMode.selectedIndex()) {
                1 -> Unit // 仅拍照
                2 -> {
                    val aspect = when (rgRatio.selectedIndex()) {
                        1 -> 0.5f
                        2 -> 0.75f
                        3 -> 16f / 9f
                        4 -> 0f
                        else -> 1f
                    }
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
        container.addView(android.widget.TextView(requireContext()).apply {
            text = "进入后直接拉起系统相机录像；录完确定直接返回视频（无编辑）。\n" +
                    "相机里取消 → 回到当前界面（onCancel）。"
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 13f
        })
    }

    override fun onStartClicked() {
        runWithMediaPermission {
            newBuilder(FilePickerSelectType.ofVideo())
                .setOpenCameraDirect(true)
                .start()
        }
    }
}
