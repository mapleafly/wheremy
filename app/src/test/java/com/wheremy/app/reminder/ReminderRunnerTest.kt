package com.wheremy.app.reminder

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.wheremy.app.data.db.WheremyDatabase
import com.wheremy.app.data.photos.PhotoStore
import com.wheremy.app.data.repo.ItemDraft
import com.wheremy.app.data.repo.ItemRepository
import com.wheremy.app.data.settings.SettingsRepository
import com.wheremy.app.data.testJpeg
import com.wheremy.app.domain.ReminderEngine
import com.wheremy.app.util.localStartOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 设置 + 跑一次检查（`RULES.md` §11.4/§11.5）。
 *
 * 这一支的重头是那句 **"发不出去就不回写"**：安装后先拒了通知权限的人，
 * 那几件物品必须还被说出来，不能因为"发不出去"就悄悄把账欠掉。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReminderRunnerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: WheremyDatabase
    private lateinit var store: PhotoStore
    private lateinit var items: ItemRepository
    private lateinit var settings: SettingsRepository
    private lateinit var runner: ReminderRunner
    private lateinit var app: Application
    private lateinit var dataStoreFile: File

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 3, 10)
    private val now: Long = ZonedDateTime.of(today, LocalTime.of(10, 0), zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        app = ApplicationProvider.getApplicationContext()
        val context = app as Context
        db = Room.inMemoryDatabaseBuilder(context, WheremyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PhotoStore(File(tmp.root, "photos"))
        items = ItemRepository(db, store)
        dataStoreFile = File(tmp.root, "settings.preferences_pb")
        settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)) {
                dataStoreFile
            },
        )
        runner = ReminderRunner(settings, ReminderEngine(db.itemDao()))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun addExpiring(name: String, at: LocalDate): String = runBlocking {
        val id = items.newDraft(testJpeg())
        items.confirm(ItemDraft(id = id, name = name, expiresAt = localStartOfDay(at, zone)))
        id
    }

    private fun notifiedAt(id: String): Long? = runBlocking { db.itemDao().byId(id)?.expireNotifiedAt }

    private fun denyNotifications() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun allowNotifications() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(app.getSystemService(android.app.NotificationManager::class.java)).setNotificationsEnabled(true)
    }

    @Test
    fun `窗口空时说清楚当前提前几天`() {
        allowNotifications()
        assertEquals("窗口里没有临近到期的物品（提前 3 天）", runBlocking { runner.runOnce(app, now = now) })

        runBlocking { settings.setReminderLeadDays(30) }
        assertEquals("窗口里没有临近到期的物品（提前 30 天）", runBlocking { runner.runOnce(app, now = now) })
    }

    @Test
    fun `发不出去就不回写`() {
        denyNotifications()
        val id = addExpiring("牛奶", today.plusDays(1))

        val sentence = runBlocking { runner.runOnce(app, now = now) }

        assertTrue("要说清发不出去", sentence.startsWith("1 件临近到期，但通知发不出去："))
        assertTrue(sentence.contains(ReminderNotifier.PROBLEM_PERMISSION))
        assertNull("★ 关键：没发出去就不能标记成已提醒", notifiedAt(id))
        // 所以下一次它还在窗口里
        assertTrue(runBlocking { runner.runOnce(app, now = now) }.startsWith("1 件临近到期"))
    }

    @Test
    fun `发得出去才回写并且只说一次`() {
        allowNotifications()
        val id = addExpiring("牛奶", today.plusDays(1))

        val sentence = runBlocking { runner.runOnce(app, now = now) }

        assertEquals("已发一条通知：1 件物品临近到期，最近的还剩 1 天", sentence)
        assertNotNull(notifiedAt(id))
        // 同一天再跑：已经说过了
        assertEquals("窗口里没有临近到期的物品（提前 3 天）", runBlocking { runner.runOnce(app, now = now) })
    }

    @Test
    fun `通知真的发出去了且只占一格`() {
        allowNotifications()
        addExpiring("牛奶", today.plusDays(1))

        runBlocking { runner.runOnce(app, now = now) }

        val manager = shadowOf(app.getSystemService(android.app.NotificationManager::class.java))
        assertEquals("一条聚合通知只占一格", 1, manager.allNotifications.size)
        assertNotNull(
            "固定 ID，重复跑只更新同一格",
            manager.getNotification(ReminderNotifier.NOTIFICATION_ID),
        )
    }

    @Test
    fun `调试按钮不是只读：照样回写但额度只吃提前那次`() {
        allowNotifications()
        val id = addExpiring("牛奶", today.plusDays(2))

        val first = runBlocking { runner.runOnce(app, force = true, now = now) }
        assertTrue("调试按钮应当真发一条：$first", first.startsWith("已发一条通知："))
        assertNotNull("按调试按钮也算真的说过一次", notifiedAt(id))
        // 提前那次额度被调试吃掉了
        val second = runBlocking { runner.runOnce(app, force = false, now = now) }
        assertTrue("额度已经被吃掉：$second", second.startsWith("窗口里没有"))

        // 但到期当天那一次仍然会来（第二分支只看 expiresAt 落没落在 [今天, 明天)）
        val onDueDate = ZonedDateTime.of(today.plusDays(2), LocalTime.of(9, 0), zone).toInstant().toEpochMilli()
        assertEquals(
            "已发一条通知：1 件物品今天到期",
            runBlocking { runner.runOnce(app, now = onDueDate) },
        )
    }

    @Test
    fun `提前天数是每次现读的`() {
        allowNotifications()
        addExpiring("牛奶", today.plusDays(10))

        assertEquals("默认 3 天：不在窗口里", "窗口里没有临近到期的物品（提前 3 天）", runBlocking { runner.runOnce(app, now = now) })

        // 改设置立刻生效（排班请求里不带 leadDays）
        runBlocking { settings.setReminderLeadDays(30) }
        assertTrue(runBlocking { runner.runOnce(app, now = now) }.startsWith("已发一条通知：1 件物品临近到期"))
    }
}
