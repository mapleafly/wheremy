package com.wheremy.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.wheremy.app.data.db.SearchTextBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 偏好设置的**逻辑**：默认值、上下界卡在写入侧、四样齐了才可用、trim、清空 key。
 *
 * ⚠️ 为什么不用真的 `PreferenceDataStoreFactory`（**F23**）：DataStore 落盘是"写临时文件再原子改名"，
 * 而 Windows 上"改名覆盖一个已存在的文件"会失败（真机是 Linux，没这个问题），于是同一个测试里
 * 第二次写入就会报 "multiple instances of DataStore"——那是**测试环境**的坑，不是产品 bug。
 * 文件形态的持久化由真机验证兜：改完设置杀掉应用再进来，值还在（见 `CHANGELOG.md` P5 记录）。
 */
class SettingsRepositoryTest {

    /** 内存版 DataStore：只替掉"怎么存"，被测的是仓储自己的逻辑。 */
    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    private val store = MemoryStore()

    /** 每次新建仓储 = 重新进设置页；同一个 store 保证能看到已写入的值。 */
    private fun repo(): SettingsRepository = SettingsRepository(store)

    /**
     * FR-CFG-06：四套词表。
     *
     * 三条要点：**默认是空的**（空 = 用代码里的预设，所以"没改过"和"改回默认"是同一件事）、
     * **写入侧就清洗**（去空白/丢空项/去重/封顶）、**清空 = 回到预设**。
     */
    @Test
    fun `词表默认是空的，等于用预设`() {
        runBlocking {
            val settings = repo().current()
            assertEquals(emptyList<String>(), settings.categories)
            assertEquals(emptyList<String>(), settings.seasons)
            assertEquals(emptyList<String>(), settings.expireKinds)
            assertEquals(emptyList<String>(), settings.locationKinds)

            val effective = repo().wordLists(
                presetCategories = listOf("衣物", "数码"),
                presetSeasons = listOf("春", "夏"),
                presetExpireKinds = listOf("保质期"),
                presetLocationKinds = listOf("抽屉"),
            )
            assertEquals("没存过就用预设", listOf("衣物", "数码"), effective.categories)
            assertEquals(listOf("春", "夏"), effective.seasons)
        }
    }

    @Test
    fun `改一套词表之后按存的来，其余仍用预设`() {
        runBlocking {
            repo().setWordList(WordListKind.CATEGORIES, listOf("手办", " 模型 ", "手办", "", "   "))
            val settings = repo().current()
            assertEquals("去空白、丢空项、去重", listOf("手办", "模型"), settings.categories)

            val effective = repo().wordLists(
                presetCategories = listOf("衣物", "数码"),
                presetSeasons = listOf("春", "夏"),
                presetExpireKinds = listOf("保质期"),
                presetLocationKinds = listOf("抽屉"),
            )
            assertEquals("改过的那套用存的", listOf("手办", "模型"), effective.categories)
            assertEquals("没改的仍是预设", listOf("春", "夏"), effective.seasons)
        }
    }

    @Test
    fun `词表封顶二十四项`() {
        runBlocking {
            repo().setWordList(WordListKind.CATEGORIES, (1..40).map { "第$it" })
            assertEquals(WordLists.MAX_ITEMS, repo().current().categories.size)
        }
    }

    @Test
    fun `清空一套等于回到预设`() {
        runBlocking {
            repo().setWordList(WordListKind.SEASONS, listOf("旱季", "雨季"))
            assertEquals(listOf("旱季", "雨季"), repo().current().seasons)

            repo().setWordList(WordListKind.SEASONS, emptyList())
            assertEquals("清空之后又变回空（= 用预设）", emptyList<String>(), repo().current().seasons)
        }
    }

    @Test
    fun `恢复默认把四套一起清掉`() {
        runBlocking {
            repo().setWordList(WordListKind.CATEGORIES, listOf("手办"))
            repo().setWordList(WordListKind.SEASONS, listOf("旱季"))
            repo().setWordList(WordListKind.EXPIRE_KINDS, listOf("借期"))
            repo().setWordList(WordListKind.LOCATION_KINDS, listOf("车库"))

            repo().resetWordLists()

            val after = repo().current()
            assertTrue("四套都该回到空", after.categories.isEmpty() && after.seasons.isEmpty())
            assertTrue(after.expireKinds.isEmpty() && after.locationKinds.isEmpty())
        }
    }

    @Test
    fun `默认值`() {
        val settings = runBlocking { repo().current() }

        assertTrue(settings.visionEnabled)
        assertEquals("https://api.deepseek.com", settings.baseUrl)
        assertEquals(3, settings.reminderLeadDays)
        assertEquals("", settings.apiKey)
        assertEquals("搜索字段版本默认跟上代码", SearchTextBuilder.FIELD_VERSION, settings.searchTextVersion)
        assertEquals("还没调用过 AI", 0, settings.aiCallCount)
        assertEquals(0L, settings.aiTokens)
        assertFalse("没 key 就不算能用", settings.hasUsableKey)
    }

    /**
     * FR-TRC-04：累计次数与 tokens。
     *
     * 钉住两件事：**重试也算一次**（只算成功的话用户看到的次数会比实际花的少），
     * 以及接口不报 tokens 时次数照涨、tokens 不动。
     */
    @Test
    fun `每次尝试都记一笔，重试也算`() {
        runBlocking {
            repo().recordAiCall(tokens = 1200)
            repo().recordAiCall(tokens = 800)
            val afterTwo = repo().current()
            assertEquals("两次调用记两次", 2, afterTwo.aiCallCount)
            assertEquals("tokens 累加", 2000L, afterTwo.aiTokens)

            repo().recordAiCall(tokens = 0)
            val afterThree = repo().current()
            assertEquals("接口不报 tokens 时次数照涨", 3, afterThree.aiCallCount)
            assertEquals("tokens 不涨", 2000L, afterThree.aiTokens)
        }
    }

    @Test
    fun `提前天数的上下界在写入侧就卡住`() {
        runBlocking {
            repo().setReminderLeadDays(-5)
            assertEquals(SettingsRepository.LEAD_DAYS_MIN, repo().current().reminderLeadDays)

            repo().setReminderLeadDays(9_999)
            assertEquals(SettingsRepository.LEAD_DAYS_MAX, repo().current().reminderLeadDays)
        }
    }

    @Test
    fun `四样齐了才算能用`() {
        runBlocking {
            repo().setApiKey("sk-test")
            assertNotNull("默认地址与模型都在，填了 key 就能用", repo().currentConfig())

            repo().setVisionEnabled(false)
            assertNull("开关关掉就不给 config", repo().currentConfig())
            assertFalse(repo().current().hasUsableKey)

            repo().setVisionEnabled(true)
            repo().setModel("   ")
            assertNull("模型是空白就不算填了", repo().currentConfig())
            assertEquals("空白会被 trim 掉", "", repo().current().model)
        }
    }

    @Test
    fun `写入会被读到且不缓存`() {
        runBlocking {
            repo().setReminderLeadDays(30)
            repo().setBaseUrl(" https://example.com/v1 ")
            repo().setSearchTextVersion(7)
        }

        // 换一个仓储实例读：值来自 store，不是内存缓存
        val again = runBlocking { repo().current() }
        assertEquals(30, again.reminderLeadDays)
        assertEquals("地址两头的空格要去掉", "https://example.com/v1", again.baseUrl)
        assertEquals(7, again.searchTextVersion)
    }

    @Test
    fun `清空 key 之后就不能再拿去调模型`() {
        runBlocking {
            repo().setApiKey("sk-secret")
            assertNotNull(repo().currentConfig())

            // 与设置页那颗「清空 key」同一动作
            repo().setApiKey("")
            assertNull(repo().currentConfig())
        }
    }
}
