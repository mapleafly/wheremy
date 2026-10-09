package com.wheremy.app.ui

import com.wheremy.app.data.repo.PhotoDeletion
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 文案的唯一生成处（`RULES.md` §9.3 的四档 + `UiFormat.kt` 的到期三句）。
 * 这些断言是**逐字**的：改一句话要连测试一起改（`PROCESS.md` §3.4）。
 */
class UiFormatTest {

    @Test
    fun `删除框四档逐字`() {
        assertEquals(
            "这条记录会被删除，无法恢复。",
            UiFormat.deleteWarningOf(PhotoDeletion(hasPhoto = false, otherItems = 0, otherOwners = 0)),
        )
        assertEquals(
            "这张照片还有 2 件物品在用，删掉这一件只会去掉这一件的记录，照片留着。",
            UiFormat.deleteWarningOf(PhotoDeletion(hasPhoto = true, otherItems = 2, otherOwners = 0)),
        )
        assertEquals(
            "这张照片还有别的记录在用，删掉这一件只会去掉这一件的记录，照片留着。",
            UiFormat.deleteWarningOf(PhotoDeletion(hasPhoto = true, otherItems = 0, otherOwners = 1)),
        )
        assertEquals(
            "照片和记录会一起删除，无法恢复。",
            UiFormat.deleteWarningOf(PhotoDeletion(hasPhoto = true, otherItems = 0, otherOwners = 0)),
        )
    }

    @Test
    fun `物品与别的持有者两档不许混`() {
        // 对着一张只被某个位置共用的图说"还有 N 件物品在用"就是假话
        val onlyOwners = UiFormat.deleteWarningOf(PhotoDeletion(true, otherItems = 0, otherOwners = 3))
        assertEquals("这张照片还有别的记录在用，删掉这一件只会去掉这一件的记录，照片留着。", onlyOwners)
    }

    @Test
    fun `到期三种说法`() {
        assertEquals("已经过期 3 天", UiFormat.expireHint(-3))
        assertEquals("就是今天", UiFormat.expireHint(0))
        assertEquals("还剩 28 天", UiFormat.expireHint(28))
    }
}
