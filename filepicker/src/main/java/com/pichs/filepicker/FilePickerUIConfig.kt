package com.pichs.filepicker

import android.os.Parcelable
import com.pichs.xwidget.utils.XColorHelper
import kotlinx.parcelize.Parcelize

@Parcelize
data class FilePickerUIConfig(

    /**
     * 是否隐藏选择标签页
     * 仅在 在 ofAll() 时 隐藏和显示有用。
     * ofImage() 和 ofVideo() 时，怎么都不会显示。因为只有一种选择类型。没必要展示一个tab。
     * tips：如果你非要展示，请下载源码处理。
     */
    var isHideSelectTab: Boolean = false,

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
     * 全部文件夹的文本名字
     */
    var allAlbumName: String = "全部",

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