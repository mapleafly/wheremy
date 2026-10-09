package com.wheremy.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AI 留痕（`RULES.md` §13.3）：一次尝试一条，重试就是两条。
 *
 * 三条纪律：
 * - **旁路**：目录写不了也必须把分析结果还给用户（调用方用 `runCatching` 包着）；
 * - 痕里**只有提示词原文和回应原文**——没有 key、没有那张 base64 的图；
 * - 上限 [MAX_TRACE] 条（滚动删最旧的）、单字段 [MAX_CHARS] 字（超出写成 `…（截断，原长 N 字）`）。
 *
 * 目录刻意**不在 `files/photos` 底下**：那样它会随备份包出门（`RULES.md` §12.1）。
 */
class AnalysisTrace(private val dir: File) {

    suspend fun record(outcome: String, prompt: String, response: String?, elapsedMs: Long = 0) =
        withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                val at = System.currentTimeMillis()
                val file = File(dir, "%013d-%s.txt".format(at, outcome.take(24)))
                file.writeText(
                    buildString {
                        appendLine("at=$at")
                        appendLine("outcome=$outcome")
                        // FR-TRC-05：排「超时 vs 模型慢」全靠这一行
                        appendLine("elapsedMs=$elapsedMs")
                        appendLine("prompt=${clip(prompt)}")
                        appendLine("response=${clip(response.orEmpty())}")
                    },
                )
                trim()
            }
        }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        dir.listFiles()?.count { it.isFile } ?: 0
    }

    private fun trim() {
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: return
        if (files.size > MAX_TRACE) {
            files.take(files.size - MAX_TRACE).forEach { it.delete() }
        }
    }

    private fun clip(text: String): String =
        if (text.length <= MAX_CHARS) text else "${text.take(MAX_CHARS)}…（截断，原长 ${text.length} 字）"

    companion object {
        /** 界面上说成"只留最近 10 条"（`UI.md` §10）。 */
        const val MAX_TRACE = 10
        const val MAX_CHARS = 8_000
        const val DIR_NAME = "ai-trace"
    }
}
