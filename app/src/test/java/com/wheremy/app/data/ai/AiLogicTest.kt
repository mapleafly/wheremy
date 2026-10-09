package com.wheremy.app.data.ai

import com.wheremy.app.data.repo.CATEGORIES
import com.wheremy.app.data.repo.SEASONS
import com.wheremy.app.domain.LocationPath
import com.wheremy.app.domain.SuggestLocationUseCase
import com.wheremy.app.ui.UiFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 层的纯逻辑（`RULES.md` §5.4–§5.6、§13）。
 *
 * 三块最容易写错的：**11 条文案逐字**、**状态码映射**（402/400+balance 走同一条路）、
 * **位置建议的取舍**（同分取更浅、都对不上就绝不绝虚构造）。
 */
class AiLogicTest {

    // —— 11 条文案（§13.2）——

    @Test
    fun `失败文案十一条逐字且都带下一步`() {
        val expected = mapOf(
            FailureReason.NO_CONFIG to "未启用 AI（还没配 API Key），字段留空，直接手填就行",
            FailureReason.OFFLINE to "网络不可用，AI 没连上，先手填，联网后可以重试",
            FailureReason.TIMEOUT to "AI 分析超时，先手填，稍后可以重试",
            FailureReason.UNAUTHORIZED to "API Key 无效或被拒，先手填，换 key 后再点重新分析",
            FailureReason.RATE_LIMITED to "AI 服务限流，稍后再试一次",
            FailureReason.SERVER to "AI 服务暂不可用，可点重新分析",
            FailureReason.BAD_RESPONSE to "AI 返回内容无法解析，可点重新分析",
            FailureReason.BAD_REQUEST to "请求被服务端拒了，多半是设置里的模型名或接口地址填错，改对后再点重新分析",
            FailureReason.INSUFFICIENT_BALANCE to "AI 账户没有可用余额或额度，充值或换账户后再点重新分析",
            FailureReason.TRUNCATED to "AI 的回应太长被切断了，可点重新分析，或换一张只拍一件物品的照片",
            FailureReason.NOTHING_FOUND to "AI 没从照片里认出物品，字段留空，直接手填就行",
        )
        assertEquals("11 类一个都不能少", 11, expected.size)
        expected.forEach { (reason, text) -> assertEquals(text, UiFormat.failureText(reason)) }
        assertNull("null 不显示", UiFormat.failureText(null))
    }

    @Test
    fun `超时文案里不写秒数`() {
        val text = UiFormat.failureText(FailureReason.TIMEOUT)!!
        assertFalse("抄一份秒数在文案里就是等着说错", text.contains("秒"))
    }

    // —— 状态码映射（§13.1）——

    @Test
    fun `状态码映射只有一处`() {
        assertNull(ApiPhotoAnalyzer.classifyStatus(200, "{}"))
        assertEquals(FailureReason.UNAUTHORIZED, ApiPhotoAnalyzer.classifyStatus(401, ""))
        assertEquals(FailureReason.UNAUTHORIZED, ApiPhotoAnalyzer.classifyStatus(403, ""))
        assertEquals(FailureReason.RATE_LIMITED, ApiPhotoAnalyzer.classifyStatus(429, ""))
        assertEquals(FailureReason.INSUFFICIENT_BALANCE, ApiPhotoAnalyzer.classifyStatus(402, ""))
        assertEquals(FailureReason.BAD_REQUEST, ApiPhotoAnalyzer.classifyStatus(400, "bad model"))
        assertEquals(FailureReason.SERVER, ApiPhotoAnalyzer.classifyStatus(500, ""))
    }

    @Test
    fun `400 加一句 balance 也算余额不足`() {
        // 只认状态码的话用户会被派去核对模型名，而真正要办的是充值
        listOf(
            """{"error":{"message":"Insufficient Balance"}}""",
            """{"error":{"message":"exceeded your quota"}}""",
            """{"error":{"message":"账户余额不足"}}""",
            """{"error":{"message":"额度已用完"}}""",
        ).forEach { body ->
            assertEquals(body, FailureReason.INSUFFICIENT_BALANCE, ApiPhotoAnalyzer.classifyStatus(400, body))
        }
    }

    @Test
    fun `可重试的只有四类`() {
        assertEquals(
            setOf(FailureReason.SERVER, FailureReason.OFFLINE, FailureReason.TIMEOUT, FailureReason.RATE_LIMITED),
            FailureReason.RETRYABLE,
        )
        assertFalse("鉴权重试没有意义", FailureReason.UNAUTHORIZED.retryable)
        assertFalse(FailureReason.NO_CONFIG.retryable)
    }

    // —— 解析（§5.5）——

    @Test
    fun `回显的 response_format 不会把 JSON 吞坏`() {
        // deepseek-flash 实测：先回显一个对象，再接真正的 JSON
        val text = """{"type": "json_object"}
            {"items": [{"name": "马克杯", "category": "厨具"}]}"""
        val parsed = AnalysisParser.parse(text)!!
        assertEquals(1, parsed.items.size)
        assertEquals("马克杯", parsed.items.single().name)
        assertTrue(parsed.echoedItemsKey)
    }

    @Test
    fun `围栏与散落对象都认`() {
        val fenced = """
            ```json
            {"items": [{"name": "数据线"}]}
            ```
        """.trimIndent()
        assertEquals("数据线", AnalysisParser.parse(fenced)!!.items.single().name)

        assertEquals(
            "整段文本里散落的那个对象也要认",
            1,
            AnalysisParser.parse("这是结果：{\"name\": \"耳机\"} 完").let { requireNotNull(it).items.size },
        )
    }

    @Test
    fun `items 空数组与读不懂是两件事`() {
        val echoedEmpty = AnalysisParser.parse("""{"items": []}""")!!
        assertTrue("模型照做了：回 items: []", echoedEmpty.echoedItemsKey)
        assertTrue(echoedEmpty.items.isEmpty())

        val nonsense = AnalysisParser.parse("""{"foo": "bar"}""")!!
        assertFalse("没有 items 键 = 读不懂", nonsense.echoedItemsKey)
        assertTrue(nonsense.items.isEmpty())
    }

    @Test
    fun `标量与数组的清洗不对称是现状`() {
        val parsed = AnalysisParser.parse(
            """{"items":[{"name":"null","color":"  白色  ","tags":["null","  冬装  "],"aliases":["NULL"]}]}""",
        )!!
        val item = parsed.items.single()
        assertNull("标量的字面 null 归一成 null", item.name)
        assertEquals("去空白", "白色", item.color)
        assertEquals("数组不认字面 null", listOf("null", "冬装"), item.tags)
        assertEquals(listOf("NULL"), item.aliases)
    }

    @Test
    fun `全空的物品不算一件`() {
        assertEquals(0, AnalysisParser.parse("""{"items":[{}]}""")!!.items.size)
    }

    // —— 位置建议（§5.4）——

    private val paths = listOf(
        LocationPath("home", "家", "家", "房间", 0),
        LocationPath("home/wardrobe", "家 / 衣柜", "衣柜", "衣柜", 1),
        LocationPath("home/drawer", "家 / 抽屉", "抽屉", "抽屉", 1),
        LocationPath("work/drawer", "工作间 / 抽屉", "抽屉", "抽屉", 1),
    )

    @Test
    fun `整条路径原样命中优先，空白不算差异`() {
        assertEquals("work/drawer", SuggestLocationUseCase.suggest(paths, "工作间/抽屉"))
        assertEquals("work/drawer", SuggestLocationUseCase.suggest(paths, " 工作间 / 抽屉 "))
    }

    @Test
    fun `只给末级词时取更浅的那个`() {
        // 两个 drawer 同分，取更浅的；同深同分不换 → 先序里最早的那一个
        assertEquals("home/drawer", SuggestLocationUseCase.suggest(paths, "抽屉"))
    }

    @Test
    fun `都对不上就给 null，绝不虚构`() {
        assertNull(SuggestLocationUseCase.suggest(paths, "地下室"))
        assertNull(SuggestLocationUseCase.suggest(paths, "   "))
        assertNull(SuggestLocationUseCase.suggest(paths, null))
    }

    @Test
    fun `按类型命中优先于按名字包含`() {
        val mixed = listOf(
            LocationPath("a", "家 / 收纳盒", "纸箱", "收纳盒", 1),
            LocationPath("b", "家 / 大收纳盒", "大收纳盒", "抽屉", 1),
        )
        // "收纳盒" 与第一个的 kind 完全相等（3 分），与第二个的名字互相包含（1 分）
        assertEquals("a", SuggestLocationUseCase.suggest(mixed, "收纳盒"))
    }

    @Test
    fun `位置三句互斥`() {
        val filled = UiFormat.locationGuessLine("家 / 衣柜", com.wheremy.app.ui.LocationGuessOutcome.FILLED)
        val kept = UiFormat.locationGuessLine("家 / 衣柜", com.wheremy.app.ui.LocationGuessOutcome.KEPT_USER_CHOICE)
        val none = UiFormat.locationGuessLine("家 / 衣柜", com.wheremy.app.ui.LocationGuessOutcome.NO_MATCH)

        assertTrue(filled.contains("已填进「放在哪里」"))
        assertTrue(kept.contains("已经选好位置了，没有改动"))
        assertTrue(none.contains("没有对得上的"))
        assertEquals("三句话互不相同", 3, setOf(filled, kept, none).size)
    }

    // —— 提示词（§5.6）——

    @Test
    fun `提示词带位置清单时用替换问法`() {
        val withList = Prompt.build(CATEGORIES, SEASONS, listOf("家 / 衣柜", "家 / 抽屉"))
        assertTrue("整句替换：不许只填末级那个词", withList.contains("不要只填末级那个词"))
        assertTrue("承托面不算一件（真机跑出来的那句）", withList.contains("不要把桌面、地板、床面、抽屉内壁这类承托面也算成一件"))
        assertTrue("品牌不许猜", withList.contains("绝对不要猜测品牌"))
        assertTrue(withList.contains("家 / 衣柜"))

        val withoutList = Prompt.build(CATEGORIES, SEASONS, emptyList())
        assertFalse("空清单时那一整段不出现", withoutList.contains("可选位置"))
        assertTrue("字段行退回通用问法", withoutList.contains("container_guess"))
    }

    @Test
    fun `提示词里写上十二个分类与四个季节`() {
        val prompt = Prompt.build(CATEGORIES, SEASONS, emptyList())
        assertTrue(prompt.contains("衣物"))
        assertTrue(prompt.contains("春"))
    }

    /**
     * FR-CFG-06 的关键一条：**用户改过词表，提示词必须跟着改**。
     *
     * 不跟的后果很具体：模型会回一堆词表外的值，芯片行上显示不出选中态（`RULES.md` §5.7 那个偏差
     * 就是这么来的）。所以这里钉的是"提示词里出现的是用户那份，不是代码里那份"。
     */
    @Test
    fun `用户改过词表时提示词用用户那份`() {
        val prompt = Prompt.build(
            categories = listOf("手办", "模型"),
            seasons = listOf("旱季", "雨季"),
            locationPaths = emptyList(),
        )
        assertTrue("要用用户的类别", prompt.contains("手办") && prompt.contains("模型"))
        assertTrue("要用用户的季节", prompt.contains("旱季") && prompt.contains("雨季"))
        assertFalse("不许再提代码里的预设类别", prompt.contains("衣物"))
        assertTrue("项数也要跟着变", prompt.contains("从这 2 个里选一个"))
    }

    @Test
    fun `留痕上限是十条`() {
        assertEquals(10, AnalysisTrace.MAX_TRACE)
        assertNotNull("目录名不能在 photos 底下，否则随备份出门", AnalysisTrace.DIR_NAME)
        assertFalse(AnalysisTrace.DIR_NAME.startsWith("photos"))
    }
}
