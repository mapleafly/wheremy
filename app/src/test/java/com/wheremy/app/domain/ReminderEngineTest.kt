package com.wheremy.app.domain

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.testJpeg
import com.wheremy.app.util.localStartOfDay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 到期窗口与措辞（`RULES.md` §11.2/§11.3）。
 *
 * 三条各挡一种骚扰的分支、以及四变体**逐字**都要钉住——这几句是通知正文，
 * 说错一次就是"每天骚扰一遍"。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReminderEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore
    private lateinit var items: ItemRepository
    private lateinit var engine: ReminderEngine

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 固定"现在"= 2026-03-10 10:00 本地时间，免得像按真实日期算导致跨天飘。 */
    private val today: LocalDate = LocalDate.of(2026, 3, 10)
    private val now: Long = ZonedDateTime.of(today, java.time.LocalTime.of(10, 0), zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PhotoStore(File(tmp.root, "photos"))
        items = ItemRepository(db, store)
        engine = ReminderEngine(db.itemDao())
    }

    @After
    fun tearDown() = db.close()

    private fun addExpiring(name: String, at: LocalDate): String = runBlocking {
        val id = items.newDraft(testJpeg())
        items.confirm(ItemDraft(id = id, name = name, expiresAt = localStartOfDay(at, zone), expireKind = "保质期"))
        id
    }

    private suspend fun notifiedAt(id: String): Long? = db.itemDao().byId(id)?.expireNotifiedAt

    @Test
    fun `正文四变体逐字`() {
        // 已过期
        assertEquals(
            "3 件物品到期或已过期，最早的已过期 2 天",
            engine.body(count = 3, nearestDays = -2, todayCount = 1),
        )
        // 每一件都是今天
        assertEquals("2 件物品今天到期", engine.body(count = 2, nearestDays = 0, todayCount = 2))
        // 有今天也有别的
        assertEquals(
            "5 件物品临近到期，其中 1 件今天到期",
            engine.body(count = 5, nearestDays = 0, todayCount = 1),
        )
        // 都还没到
        assertEquals(
            "4 件物品临近到期，最近的还剩 3 天",
            engine.body(count = 4, nearestDays = 3, todayCount = 0),
        )
    }

    @Test
    fun `今天到期只在每一件都是今天时才成立`() {
        // 一件今天、一件后天：不能说"2 件今天到期"
        assertTrue(engine.body(2, 0, 1) != "2 件物品今天到期")
        assertEquals("2 件物品今天到期", engine.body(2, 0, 2))
    }

    @Test
    fun `窗口里的都进来并且按到期日升序`() {
        val soon = addExpiring("快到期", today.plusDays(2))
        val expired = addExpiring("已过期", today.minusDays(3))
        addExpiring("还很远", today.plusDays(60))

        val plan = runBlocking { engine.plan(now, leadDays = 3) }!!

        assertEquals(2, plan.count)
        assertEquals(listOf(expired, soon), plan.ids)
        assertEquals("2 件物品到期或已过期，最早的已过期 3 天", plan.body)
    }

    @Test
    fun `提前 N 天边界：正好第 N 天的那件要进来`() {
        addExpiring("正好第 3 天", today.plusDays(3))

        // ⚠️ 少了 expireWindowEnd 的 +1/-1，这一件就会被漏掉
        assertNotNull(runBlocking { engine.plan(now, leadDays = 3) })
        assertNull(runBlocking { engine.plan(now, leadDays = 2) })
    }

    @Test
    fun `说过一次之后同一天不再进来`() {
        val id = addExpiring("牛奶", today.plusDays(1))
        val first = runBlocking { engine.plan(now, leadDays = 3) }!!
        assertEquals(listOf(id), first.ids)

        runBlocking { engine.markNotified(first.ids, now) }

        // 同一天再跑：已经说过，不再进来（这是"最多两次"里的第一次额度）
        assertNull(runBlocking { engine.plan(now, leadDays = 3) })
    }

    @Test
    fun `提前提醒过、到期当天还会再说一次`() {
        val id = addExpiring("牛奶", today.plusDays(2))
        val first = runBlocking { engine.plan(now, leadDays = 3) }!!
        runBlocking { engine.markNotified(first.ids, now) }

        // 到了到期当天（两小时后就是那一天）
        val onDueDate = ZonedDateTime.of(today.plusDays(2), java.time.LocalTime.of(9, 0), zone)
            .toInstant()
            .toEpochMilli()
        val second = runBlocking { engine.plan(onDueDate, leadDays = 3) }

        assertEquals("到期当天允许再说一次", listOf(id), second?.ids)
    }

    @Test
    fun `过期很久且已说过的不会一直占着限制`() {
        val stale = addExpiring("去年就过期了", today.minusDays(200))
        val first = runBlocking { engine.plan(now, leadDays = 3) }!!
        assertEquals(listOf(stale), first.ids)
        runBlocking { engine.markNotified(first.ids, now) }

        // 之后无论跑多少次都不再进来（否则 ORDER BY expiresAt 会让它长期占满 LIMIT 50）
        assertNull(runBlocking { engine.plan(now.plus(86_400_000L), leadDays = 3) })
        assertNull(runBlocking { engine.plan(now.plus(2 * 86_400_000L), leadDays = 3) })
    }

    @Test
    fun `改过到期日会重新武装`() {
        val id = addExpiring("保修", today.plusDays(1))
        val first = runBlocking { engine.plan(now, leadDays = 3) }!!
        runBlocking { engine.markNotified(first.ids, now) }
        assertNull(runBlocking { engine.plan(now, leadDays = 3) })

        // 把到期日改到三个月后：没有 reArm 的话它永远不会再被提一句
        runBlocking {
            items.confirm(ItemDraft(id = id, name = "保修", expiresAt = localStartOfDay(today.plusDays(90), zone)))
        }
        assertNull("还没进窗口", runBlocking { engine.plan(now, leadDays = 3) })
        assertNotNull(
            "进了新窗口就要能再提一句",
            runBlocking { engine.plan(now.plus(88 * 86_400_000L), leadDays = 3) },
        )
    }

    @Test
    fun `force 只放宽今天说过几次，不翻沉默的旧账`() {
        val stale = addExpiring("早过期了", today.minusDays(200))
        val first = runBlocking { engine.plan(now, leadDays = 3) }!!
        runBlocking { engine.markNotified(first.ids, now) }

        // 日常排班：不进来
        assertNull(runBlocking { engine.plan(now, leadDays = 3) })
        // 调试按钮：过期很久且已说过的**仍然不进来**
        assertNull(runBlocking { engine.plan(now, leadDays = 3, force = true) })
    }

    @Test
    fun `force 能把今天说过的再拿回来`() {
        val id = addExpiring("牛奶", today.plusDays(1))
        val first = runBlocking { engine.plan(now, leadDays = 3) }!!
        runBlocking { engine.markNotified(first.ids, now) }

        val forced = runBlocking { engine.plan(now, leadDays = 3, force = true) }

        assertEquals(listOf(id), forced?.ids)
    }

    @Test
    fun `回写只写命中的那几件`() {
        val hit = addExpiring("牛奶", today.plusDays(1))
        val other = addExpiring("还很远", today.plusDays(60))
        val plan = runBlocking { engine.plan(now, leadDays = 3) }!!

        runBlocking { engine.markNotified(plan.ids, now) }

        assertNotNull(runBlocking { notifiedAt(hit) })
        assertNull("没命中的不能被顺手标记", runBlocking { notifiedAt(other) })
    }
}
