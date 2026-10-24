package com.pichs.filepicker.crop

/**
 * 编辑页上的一个文字图层。
 *
 * 坐标用「当前显示图片」的归一化坐标（0~1），好处是：
 * 缩放/平移动画、裁剪框移动都不影响文字锚点；导出时按原图尺寸再放大一次即可。
 *
 * 样式有两种模式（和微信编辑器一致）：
 * - **圆角背景模式**：[backgroundColor] != null，颜色条选的是背景色，文字白色；
 * - **字体颜色模式**：[backgroundColor] == null，颜色条选的是字色。
 */
data class TextLayer(
    var text: String,
    /** 文字中心在图片宽度上的比例（0~1） */
    var fx: Float,
    /** 文字中心在图片高度上的比例（0~1） */
    var fy: Float,
    /** 字号 / 图片短边（双指缩放文字时改它） */
    var sizeFrac: Float,
    /** 文字颜色 */
    var textColor: Int,
    /** 圆角背景颜色；null = 无背景（字体颜色模式） */
    var backgroundColor: Int?,
    /** 正在被输入框编辑时临时隐藏（避免和输入框里的字重影） */
    var hidden: Boolean = false,
) {
    /** 图片顺时针转 90° 后，文字锚点跟着转：bitmap (x,y) → (1-y, x) */
    fun followRotate90Cw() {
        val oldFx = fx
        fx = 1f - fy
        fy = oldFx
    }

    /** 图片水平镜像后，文字锚点跟着翻：x → 1-x */
    fun followMirrorHorizontal() {
        fx = 1f - fx
    }
}
