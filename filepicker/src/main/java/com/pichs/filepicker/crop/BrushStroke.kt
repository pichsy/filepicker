package com.pichs.filepicker.crop

/**
 * 画笔/马赛克的一笔。
 *
 * 坐标是「当前显示图片」的归一化坐标（0~1），和文字一样：
 * 缩放/平移/裁剪都不影响，导出时按原图尺寸换算笔刷半径。
 */
data class BrushStroke(
    /** 轨迹点：每个元素是 [fx, fy] */
    val points: MutableList<FloatArray> = mutableListOf(),
    /** 笔刷半径 / 图片短边 */
    var radiusFrac: Float,
    /** 0 = 像素马赛克（真正的马赛克）；1 = 毛刷模糊；2 = 彩色画笔涂鸦 */
    var mode: Int,
    /** 橡皮：擦掉已经涂的效果 */
    var erase: Boolean = false,
    /** 画笔颜色（mode=2 时用） */
    var color: Int = 0xFFFFFFFF.toInt(),
) {
    fun add(fx: Float, fy: Float) {
        points.add(floatArrayOf(fx, fy))
    }

    /** 图片顺时针转 90° 后轨迹跟着转 */
    fun followRotate90Cw() {
        points.forEach { p ->
            val ox = p[0]
            p[0] = 1f - p[1]
            p[1] = ox
        }
    }

    /** 图片水平镜像后轨迹跟着翻 */
    fun followMirrorHorizontal() {
        points.forEach { p -> p[0] = 1f - p[0] }
    }
}
