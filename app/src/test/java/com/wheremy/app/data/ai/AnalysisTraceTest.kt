package com.wheremy.app.data.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 留痕的三条纪律（`RULES.md` §13.3）：一次尝试一条、**有耗时**（FR-TRC-05）、上限与截断。
 *
 * 这些都是**落盘行为**，光看代码不算——真机上线前摔过一跤（痕写了但目录不在预期位置），
 * 所以这里用真文件系统（临时目录）而不是内存替身。
 */
class AnalysisTraceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun trace() = AnalysisTrace(File(tmp.root, "ai-trace"))

    @Test
    fun `一次尝试一条，四条信息都在`() = runBlocking {
        val dir = File(tmp.root, "ai-trace")
        AnalysisTrace(dir).record("success", "提示词原文", "回应原文", elapsedMs = 1234)

        val files = dir.listFiles()!!.filter { it.isFile }
        assertEquals("一次尝试就一条", 1, files.size)
        val text = files.single().readText()
        assertTrue("要有 outcome", text.contains("outcome=success"))
        assertTrue("要有耗时（FR-TRC-05：排超时 vs 模型慢）", text.contains("elapsedMs=1234"))
        assertTrue("要有提示词原文", text.contains("prompt=提示词原文"))
        assertTrue("要有回应原文", text.contains("response=回应原文"))
        assertTrue("要能看出什么时候", text.contains("at="))
    }

    @Test
    fun `失败也留痕，outcome 是那一类的名字`() = runBlocking {
        val dir = File(tmp.root, "ai-trace")
        AnalysisTrace(dir).record(FailureReason.TIMEOUT.name, "p", null, elapsedMs = 60_000)

        val text = dir.listFiles()!!.single().readText()
        assertTrue(text.contains("outcome=TIMEOUT"))
        assertTrue("超时那条的耗时就是超时上限", text.contains("elapsedMs=60000"))
        assertTrue("没回应也要有一行，不能少字段", text.contains("response="))
    }

    @Test
    fun `只留最近十条，最旧的被删掉`() = runBlocking {
        val dir = File(tmp.root, "ai-trace")
        val trace = AnalysisTrace(dir)
        repeat(AnalysisTrace.MAX_TRACE + 3) { index ->
            trace.record("success", "第 $index 次", "ok", elapsedMs = index.toLong())
            // 文件名是按毫秒时间戳排序的，同一毫秒内会重名覆盖——这里错开一点
            Thread.sleep(2)
        }
        assertEquals(AnalysisTrace.MAX_TRACE, trace.count())
        val kept = dir.listFiles()!!.map { it.readText() }.joinToString("\n")
        assertTrue("最早那几次应当被删", !kept.contains("第 0 次"))
        assertTrue("最近一次必须在", kept.contains("第 ${AnalysisTrace.MAX_TRACE + 2} 次"))
    }

    @Test
    fun `超长字段被截断，但原长写在里面`() = runBlocking {
        val dir = File(tmp.root, "ai-trace")
        val long = "字".repeat(AnalysisTrace.MAX_CHARS + 500)
        AnalysisTrace(dir).record("success", long, long, elapsedMs = 1)

        val text = dir.listFiles()!!.single().readText()
        assertTrue("要标出被截断", text.contains("（截断，原长 ${AnalysisTrace.MAX_CHARS + 500} 字）"))
        assertTrue("痕迹不该无限大", text.length < AnalysisTrace.MAX_CHARS * 3)
    }

    @Test
    fun `目录写不进去也不抛异常`() = runBlocking {
        // 旁路：目录那一层被一个同名**文件**占着，mkdirs 必失败——也必须安静地过去
        val blocked = File(tmp.root, "blocked")
        blocked.writeText("我不是目录")
        AnalysisTrace(File(blocked, "ai-trace")).record("success", "p", "r", elapsedMs = 1)
        assertEquals("写不进去就是 0 条，不许抛", 0, AnalysisTrace(File(blocked, "ai-trace")).count())
    }

    @Test
    fun `留痕目录不在 photos 底下`() {
        assertTrue(
            "目录名不许以 photos 开头，否则它会随备份包出门（FR-TRC-03）",
            !AnalysisTrace.DIR_NAME.startsWith("photos"),
        )
    }
}
