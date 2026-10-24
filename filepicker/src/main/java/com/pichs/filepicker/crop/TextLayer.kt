package com.pichs.filepicker.crop

/**
 * 编辑页上的一个文字图层。
 *
 * 坐标用「当前显示图片」的归一化坐标（0~1），好处是：
 * 缩放/平移动画、裁剪框移动都不影响文字锚点；导出时按原图尺寸再放大一次即可。
 *
 * 字号 [sizeFrac] 是相对图片**短边**的比例，导出时 × 原图短边 = 实际字号。
 */
data class TextLayer(
    val text: String,
    /** 文字中心在图片宽度上的比例（0~1） */
    var fx: Float,
    /** 文字中心在图片高度上的比例（0~1） */
    var fy: Float,
    /** 字号 / 图片短边 */
    val sizeFrac: Float,
    val color: Int,
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
