package com.pichs.filepicker.crop

import com.pichs.filepicker.crop.CropGeometry.Handle
import com.pichs.filepicker.crop.CropGeometry.RectF2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 裁剪几何单测：比例锁定、边界钳制、把手命中、坐标映射、旋转。
 *
 * 这些是最容易在真机上手抖/越界的地方，先在这里算清楚。
 */
class CropGeometryTest {

    private val eps = 0.001f

    @Test
    fun fitCenter_centeredWithoutCrop() {
        // 4000x3000 的图放进 1000x1000 的 View
        val rect = CropGeometry.fitCenter(4000, 3000, 1000, 1000)
        assertEquals(0f, rect.left, eps)
        assertEquals(125f, rect.top, eps)
        assertEquals(1000f, rect.right, eps)
        assertEquals(875f, rect.bottom, eps)
    }

    @Test
    fun initialFrame_freeIsWholeImage() {
        val image = RectF2(0f, 100f, 1000f, 850f)
        val frame = CropGeometry.initialFrame(image, null)
        assertEquals(image, frame)
        assertEquals(image, CropGeometry.initialFrame(image, 0f))
    }

    @Test
    fun initialFrame_squareCenteredInLandscape() {
        val image = RectF2(0f, 0f, 1000f, 750f)
        val frame = CropGeometry.initialFrame(image, 1f)
        // 1:1 取最大正方形：750×750，左右各留 125，上下贴满
        assertEquals(125f, frame.left, eps)
        assertEquals(0f, frame.top, eps)
        assertEquals(875f, frame.right, eps)
        assertEquals(750f, frame.bottom, eps)
        assertEquals(frame.width, frame.height, eps)
    }

    @Test
    fun clampFrame_keepsInsideBounds() {
        val bounds = RectF2(0f, 0f, 1000f, 750f)
        val frame = RectF2(900f, 100f, 1200f, 600f)
        val clamped = CropGeometry.clampFrame(frame, bounds, minSize = 60f)
        assertTrue(bounds.contains(clamped))
        assertEquals(700f, clamped.left, eps)
        assertEquals(1000f, clamped.right, eps)
    }

    @Test
    fun moveFrame_clampedToBounds() {
        val bounds = RectF2(0f, 0f, 1000f, 750f)
        val frame = RectF2(100f, 100f, 300f, 200f)
        val moved = CropGeometry.moveFrame(frame, dx = -500f, dy = 0f, bounds = bounds)
        assertEquals(0f, moved.left, eps)
        assertEquals(200f, moved.right, eps)
        assertEquals(100f, moved.top, eps)
    }

    @Test
    fun resizeFrame_cornerFollowsFinger() {
        val bounds = RectF2(0f, 0f, 1000f, 1000f)
        val frame = RectF2(100f, 100f, 300f, 300f)
        val resized = CropGeometry.resizeFrame(
            frame, Handle.BOTTOM_RIGHT, dx = 50f, dy = 50f, bounds = bounds, minSize = 60f,
        )
        assertEquals(350f, resized.right, eps)
        assertEquals(350f, resized.bottom, eps)
        assertEquals(100f, resized.left, eps)
    }

    @Test
    fun resizeFrame_neverSmallerThanMinSize() {
        val bounds = RectF2(0f, 0f, 1000f, 1000f)
        val frame = RectF2(100f, 100f, 140f, 140f)
        val resized = CropGeometry.resizeFrame(
            frame, Handle.TOP_LEFT, dx = 500f, dy = 500f, bounds = bounds, minSize = 48f,
        )
        assertEquals(48f, resized.width, eps)
        assertEquals(48f, resized.height, eps)
    }

    @Test
    fun resizeFrame_aspectLockedKeepsSquare() {
        val bounds = RectF2(0f, 0f, 1000f, 1000f)
        val frame = RectF2(100f, 100f, 300f, 300f)
        val resized = CropGeometry.resizeFrame(
            frame, Handle.BOTTOM_RIGHT, dx = 100f, dy = 0f, bounds = bounds, minSize = 60f, aspect = 1f,
        )
        assertEquals(resized.width, resized.height, eps)
        assertEquals(400f, resized.right, eps)
        assertEquals(400f, resized.bottom, eps)
    }

    @Test
    fun hitTest_matchesCornersEdgesAndInside() {
        val frame = RectF2(100f, 100f, 400f, 400f)
        assertEquals(Handle.TOP_LEFT, CropGeometry.hitTest(frame, 102f, 102f, 20f))
        assertEquals(Handle.TOP, CropGeometry.hitTest(frame, 250f, 101f, 20f))
        assertEquals(Handle.END, CropGeometry.hitTest(frame, 398f, 250f, 20f))
        assertEquals(Handle.INSIDE, CropGeometry.hitTest(frame, 250f, 250f, 20f))
        assertEquals(Handle.NONE, CropGeometry.hitTest(frame, 50f, 50f, 20f))
    }

    @Test
    fun frameToImageRatio_mapsCorrectly() {
        val imageRect = RectF2(0f, 0f, 1000f, 750f)
        val frame = RectF2(100f, 75f, 600f, 450f)
        val ratio = CropGeometry.frameToImageRatio(frame, imageRect)
        assertEquals(0.1f, ratio.left, eps)
        assertEquals(0.1f, ratio.top, eps)
        assertEquals(0.6f, ratio.right, eps)
        assertEquals(0.6f, ratio.bottom, eps)
    }

    @Test
    fun frameToImageRatio_clampedToUnitSquare() {
        val imageRect = RectF2(0f, 0f, 1000f, 750f)
        // 框比图片大：归一化后必须被钳到 0~1
        val ratio = CropGeometry.frameToImageRatio(RectF2(-100f, -100f, 1200f, 900f), imageRect)
        assertEquals(0f, ratio.left, eps)
        assertEquals(0f, ratio.top, eps)
        assertEquals(1f, ratio.right, eps)
        assertEquals(1f, ratio.bottom, eps)
    }

    @Test
    fun rotateRect_clockwise() {
        val rect = RectF2(0.1f, 0.2f, 0.4f, 0.6f)
        val rotated = CropGeometry.rotateRect(rect, 90)
        assertEquals(0.4f, rotated.left, eps)
        assertEquals(0.1f, rotated.top, eps)
        assertEquals(0.8f, rotated.right, eps)
        assertEquals(0.4f, rotated.bottom, eps)
    }

    @Test
    fun rotateRect_180IsMirrorOfBothAxes() {
        val rect = RectF2(0.1f, 0.2f, 0.4f, 0.6f)
        val rotated = CropGeometry.rotateRect(rect, 180)
        assertEquals(0.6f, rotated.left, eps)
        assertEquals(0.4f, rotated.top, eps)
        assertEquals(0.9f, rotated.right, eps)
        assertEquals(0.8f, rotated.bottom, eps)
    }

    @Test
    fun rotatedSize_swapsOnQuarterTurns() {
        assertEquals(300, CropGeometry.rotatedSize(400, 300, 90)[0])
        assertEquals(400, CropGeometry.rotatedSize(400, 300, 90)[1])
        assertEquals(400, CropGeometry.rotatedSize(400, 300, 180)[0])
        assertEquals(400, CropGeometry.rotatedSize(400, 300, 0)[0])
    }

    @Test
    fun normalizeRotation_wrapsNegative() {
        assertEquals(270, CropGeometry.normalizeRotation(-90))
        assertEquals(0, CropGeometry.normalizeRotation(360))
        assertEquals(90, CropGeometry.normalizeRotation(450))
    }

    @Test
    fun minScale_ensuresCoverage() {
        val image = RectF2(0f, 0f, 1000f, 750f)
        val frame = RectF2(0f, 0f, 500f, 500f)
        // 高度需要 500/750，宽度需要 0.5 → 取较大值
        assertEquals(500f / 750f, CropGeometry.minScale(image, frame), eps)
    }

    @Test
    fun clampFrame_withAspectKeepsRatio() {
        val bounds = RectF2(0f, 0f, 1000f, 400f)
        val frame = RectF2(0f, 0f, 800f, 800f)
        val clamped = CropGeometry.clampFrame(frame, bounds, minSize = 60f, aspect = 1f)
        assertEquals(400f, clamped.width, eps)
        assertEquals(400f, clamped.height, eps)
        assertTrue(bounds.contains(clamped))
    }

    @Test
    fun isEmpty_detectsDegenerateRect() {
        assertTrue(RectF2(10f, 10f, 10f, 20f).isEmpty())
        assertFalse(RectF2(0f, 0f, 10f, 10f).isEmpty())
    }
}
