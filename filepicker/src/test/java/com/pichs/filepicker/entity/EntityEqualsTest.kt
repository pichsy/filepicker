package com.pichs.filepicker.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * equals/hashCode 修复的回归测试（纯 JVM，不依赖 Android 运行时）。
 *
 * 注意：这里刻意不使用 `Uri`。单元测试跑在 mockable android.jar 上，
 * `Uri.parse` 只会返回 null（testOptions.unitTests.isReturnDefaultValues = true），
 * 用 Uri 的用例放在仪器测试 EntityEqualsInstrumentedTest 里。
 */
class EntityEqualsTest {

    // ---------------- MediaEntity ----------------

    /** 回归：旧实现 `path == other.path` 在两个 path 都为 null 时成立，导致所有无 path 实体互相"相等" */
    @Test
    fun twoPathlessEntities_areNotEqual() {
        assertNotEquals(MediaEntity(), MediaEntity())
    }

    @Test
    fun emptyPathEntities_areNotEqual() {
        assertNotEquals(MediaEntity(path = ""), MediaEntity(path = ""))
    }

    /** 回归：pathless 实体不能再"意外命中"列表里的元素（这是 indexOf 错位的来源） */
    @Test
    fun pathlessEntity_doesNotAccidentallyMatchInsideList() {
        val list = mutableListOf(MediaEntity(path = null), MediaEntity(path = null))
        assertFalse(list.contains(MediaEntity(path = null)))
    }

    @Test
    fun samePath_isEqual_andSameHashCode() {
        val a = MediaEntity(path = "/sdcard/a.jpg")
        val b = MediaEntity(path = "/sdcard/a.jpg")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun differentPath_isNotEqual() {
        assertNotEquals(MediaEntity(path = "/sdcard/a.jpg"), MediaEntity(path = "/sdcard/b.jpg"))
    }

    /** path 仍必须是主键：有 path vs 没 path 不能相等 */
    @Test
    fun pathVersusPathless_isNotEqual() {
        assertNotEquals(MediaEntity(path = "/sdcard/a.jpg"), MediaEntity(path = null))
    }

    /** 用户预置的选中项是 fromPath 造出来的（只有 path），必须能和扫描出来的实体按 path 匹配上 */
    @Test
    fun fromPathEntity_matchesScannedEntityByPath() {
        val fromUser = MediaEntity.fromPath("/sdcard/a.jpg")
        val scanned = MediaEntity(path = "/sdcard/a.jpg", name = "a.jpg", mimeType = "image/jpeg")
        assertEquals(fromUser, scanned)
        assertEquals(fromUser.hashCode(), scanned.hashCode())
    }

    // ---------------- MediaFolder ----------------

    /** 回归：旧实现三个字段都写反了（相等时 return false），除同一引用外永远不相等 */
    @Test
    fun folder_sameValues_areEqual_andSameHashCode() {
        val a = MediaFolder(name = "相机", folderPath = "/dcim/camera")
        val b = MediaFolder(name = "相机", folderPath = "/dcim/camera")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun folder_differentName_isNotEqual() {
        assertNotEquals(MediaFolder(name = "a", folderPath = "/x"), MediaFolder(name = "b", folderPath = "/x"))
    }

    @Test
    fun folder_differentPath_isNotEqual() {
        assertNotEquals(MediaFolder(name = "a", folderPath = "/x"), MediaFolder(name = "a", folderPath = "/y"))
    }

    @Test
    fun folder_differentMediaList_isNotEqual() {
        val a = MediaFolder(name = "a", folderPath = "/x").apply { add(MediaEntity(path = "/x/1.jpg")) }
        val b = MediaFolder(name = "a", folderPath = "/x")
        assertNotEquals(a, b)
    }

    /** 行为回归：`mediaFolders.remove(folder)` 现在真的删得掉（旧代码恒为 false） */
    @Test
    fun folder_removeByValue_actuallyRemoves() {
        val folders = mutableListOf(
            MediaFolder(name = "a", folderPath = "/x"),
            MediaFolder(name = "b", folderPath = "/y"),
        )
        assertTrue(folders.remove(MediaFolder(name = "a", folderPath = "/x")))
        assertEquals(1, folders.size)
        assertEquals("/y", folders[0].folderPath)
    }
}
