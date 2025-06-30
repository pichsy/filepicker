package com.pichs.filepicker

import android.graphics.Color
import android.os.Parcelable
import com.pichs.xwidget.utils.XColorHelper
import kotlinx.parcelize.Parcelize

@Parcelize
data class FilePickerUIConfig(
    /**
     * 确定按钮文本
     * 默认值为 "确定(1)"
     */
    var confirmBtnText: String = "确定",

    /**
     * 预览页面是否显示选中索引。显示顶部的就是 index号，不选就是对号✅
     */
    var isShowPreviewPageSelectedIndex: Boolean = true,

    /**
     * 预览页面的标题
     */
    var previewText: String = "预览",

    /**
     * selectText 选择
     */
    var previewSelectText: String = "选择",

    /**
     * originalText 原图
     */
    var originalText: String = "原图",

    /**
     * 是否显示原图选项
     */
    var isShowOriginal: Boolean = true,

    /**
     * 是否勾选原图
     */
    var isOriginalChecked: Boolean = false,

    /**
     * 是否展示选中列表item中的close按钮
     */
    var isShowSelectedListDeleteIcon: Boolean = false,

    /**
     * 选中列表item中删除按钮的背景色
     */
    var selectedListDeleteIconBackgroundColor: Int = XColorHelper.parseColor("#FA4B3A"),

    /**
     * 至少选择一个 toast
     */
    var atLeastSelectOneToastContent: String = "至少选择一个",

    /**
     * 已达到最大选择数量
     */
    var selectMaxNumberOverToastContent: String = "已达到最大选择数量",
) : Parcelable