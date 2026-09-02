package com.pichs.filepicker

import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
public data class FilePickerUIConfig(

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
    var isPreviewPageIndexMode: Boolean = true,

    /**
     * 全部文件夹的文本名字
     */
    var allAlbumName: String = "全部",

    /**
     * 预览页面的标题
     */
    var previewText: String = "预览",

    /**
     * 是否显示底部预览视图
     */
    var isShowBottomPreviewText: Boolean = true,

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
     *  是否显示 主界面的 底部选择的 列表
     *  默认展示
     *  但是，仅限于 图片类的，视频类的。（默认展示）
     *  文件和音频类型不支持。
     */
    var isShowHomePageSelectedBottomListWidget: Boolean = true,

    /**
     * 是否展示选中列表item中的close按钮
     */
    var isShowSelectedListDeleteIcon: Boolean = false,

    /**
     * 是否展示选中列表item中的close按钮 资源id
     */
    var selectedListDeleteIconResId: Int = 0,

    /**
     * 选中列表item中删除按钮的背景色
     */
    var selectedListDeleteIconBackgroundColor: Int = Color.TRANSPARENT,

    /**
     * 至少选择一个 toast
     */
    var atLeastSelectOneToastContent: String = "至少选择一个",

    /**
     * 已达到最大选择数量
     */
    var selectMaxNumberOverToastContent: String = "已达到最大选择数量",


    //=====================文件夹别名，如果要翻译文件夹则可以使用这个。====================

    /**
     * 文件夹别名映射，只认名字。
     * 例如："DCIM" to "相册", "Camera" to "相机"，"Download" to "下载" 等等。
     * 如果不设置，则使用默认名字。
     * 可随意扩展
     */
    var folderNickNameMap: HashMap<String, String> = hashMapOf<String, String>()
) : Parcelable {

    /**
     * 拍平为纯基本类型 Bundle，用于 Intent 传输。
     * 背景：把 app 内的自定义 Parcelable 直接放进 Intent extra 后，system_server 在启动
     * Activity 时会用自己进程的 classloader 反序列化 Bundle（窗口管理、ActivityRecord 等
     * 逻辑会读取/写入 extras），找不到 app 类会抛 ClassNotFoundException 并干扰启动流程
     * （华为系 ROM 上实测必现）。全部改用 framework 自带类型（Bundle/Boolean/Int/String），
     * 系统进程可安全解析，读取端见 [fromTransportBundle]。
     */
    public fun toTransportBundle(): Bundle = Bundle().apply {
        putBoolean("isHideSelectTab", isHideSelectTab)
        putString("confirmBtnText", confirmBtnText)
        putBoolean("isPreviewPageIndexMode", isPreviewPageIndexMode)
        putString("allAlbumName", allAlbumName)
        putString("previewText", previewText)
        putBoolean("isShowBottomPreviewText", isShowBottomPreviewText)
        putString("previewSelectText", previewSelectText)
        putString("originalText", originalText)
        putBoolean("isShowOriginal", isShowOriginal)
        putBoolean("isOriginalChecked", isOriginalChecked)
        putBoolean("isShowHomePageSelectedBottomListWidget", isShowHomePageSelectedBottomListWidget)
        putBoolean("isShowSelectedListDeleteIcon", isShowSelectedListDeleteIcon)
        putInt("selectedListDeleteIconResId", selectedListDeleteIconResId)
        putInt("selectedListDeleteIconBackgroundColor", selectedListDeleteIconBackgroundColor)
        putString("atLeastSelectOneToastContent", atLeastSelectOneToastContent)
        putString("selectMaxNumberOverToastContent", selectMaxNumberOverToastContent)
        putBundle("folderNickNameMap", Bundle().apply {
            folderNickNameMap.forEach { (key, value) -> putString(key, value) }
        })
    }

    public companion object {

        /**
         * 从 [toTransportBundle] 的产物重建；字段缺失时沿用 data class 默认值，
         * 语义与 Parcelable 反序列化一致。传 null（未携带该 extra）时返回 null。
         */
        public fun fromTransportBundle(bundle: Bundle?): FilePickerUIConfig? {
            if (bundle == null) return null
            val folderNickNameMap = hashMapOf<String, String>()
            bundle.getBundle("folderNickNameMap")?.let { nickNameBundle ->
                for (key in nickNameBundle.keySet()) {
                    val value = nickNameBundle.getString(key) ?: continue
                    folderNickNameMap[key] = value
                }
            }
            return FilePickerUIConfig(
                isHideSelectTab = bundle.getBoolean("isHideSelectTab", false),
                confirmBtnText = bundle.getString("confirmBtnText", "确定"),
                isPreviewPageIndexMode = bundle.getBoolean("isPreviewPageIndexMode", true),
                allAlbumName = bundle.getString("allAlbumName", "全部"),
                previewText = bundle.getString("previewText", "预览"),
                isShowBottomPreviewText = bundle.getBoolean("isShowBottomPreviewText", true),
                previewSelectText = bundle.getString("previewSelectText", "选择"),
                originalText = bundle.getString("originalText", "原图"),
                isShowOriginal = bundle.getBoolean("isShowOriginal", true),
                isOriginalChecked = bundle.getBoolean("isOriginalChecked", false),
                isShowHomePageSelectedBottomListWidget = bundle.getBoolean("isShowHomePageSelectedBottomListWidget", true),
                isShowSelectedListDeleteIcon = bundle.getBoolean("isShowSelectedListDeleteIcon", false),
                selectedListDeleteIconResId = bundle.getInt("selectedListDeleteIconResId", 0),
                selectedListDeleteIconBackgroundColor = bundle.getInt("selectedListDeleteIconBackgroundColor", Color.TRANSPARENT),
                atLeastSelectOneToastContent = bundle.getString("atLeastSelectOneToastContent", "至少选择一个"),
                selectMaxNumberOverToastContent = bundle.getString("selectMaxNumberOverToastContent", "已达到最大选择数量"),
                folderNickNameMap = folderNickNameMap,
            )
        }
    }
}