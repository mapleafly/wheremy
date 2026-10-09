package com.wheremy.app.data.ai

import com.wheremy.app.data.settings.VisionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * 照片分析（`RULES.md` §13）。
 *
 * 三条纪律：
 * 1. `classify*` 是**唯一**的状态码映射：界面报的那句话和留痕里的标签都从它拿；
 * 2. 只有 [FailureReason.RETRYABLE] 才重试，最多 [MAX_ATTEMPTS] 次，重试**共享同一份 body 与提示词**
 *    （不重新序列化、不重读设置）；
 * 3. 留痕是旁路：写不进去也必须把结果还给用户。
 */
class ApiPhotoAnalyzer(private val configProvider: suspend () -> VisionConfig?, private val trace: AnalysisTrace) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyze(jpeg: ByteArray, systemPrompt: String): AnalysisOutcome {
        val config = configProvider() ?: return fail(FailureReason.NO_CONFIG, null)
        val body = buildBody(config, jpeg, systemPrompt)

        var attempt = 0
        var last: AnalysisOutcome = AnalysisOutcome.Failure(FailureReason.OFFLINE)
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            last = runCatching { once(config, body, systemPrompt) }
                .getOrElse { t -> fromThrowable(t) }
            if (last is AnalysisOutcome.Success) return last
            val reason = (last as AnalysisOutcome.Failure).reason
            if (!reason.retryable) return last
        }
        return last
    }

    private suspend fun once(config: VisionConfig, body: String, systemPrompt: String): AnalysisOutcome =
        withContext(Dispatchers.IO) {
            val url = URL(config.baseUrl.trimEnd('/') + "/chat/completions")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CALL_TIMEOUT_SECONDS * 1_000
                readTimeout = CALL_TIMEOUT_SECONDS * 1_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
            try {
                connection.outputStream.use { it.write(body.toByteArray()) }
                val code = connection.responseCode
                val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()

                val statusFailure = classifyStatus(code, text)
                if (statusFailure != null) {
                    trace.record(statusFailure.name, systemPrompt, text)
                    return@withContext AnalysisOutcome.Failure(statusFailure, text)
                }

                val finishReason = runCatching {
                    json.parseToJsonElement(text).jsonObject["choices"]!!
                        .let { it as kotlinx.serialization.json.JsonArray }[0]
                        .jsonObject["finish_reason"]?.jsonPrimitive?.content
                }.getOrNull()
                val content = runCatching {
                    json.parseToJsonElement(text).jsonObject["choices"]!!
                        .let { it as kotlinx.serialization.json.JsonArray }[0]
                        .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
                }.getOrNull()

                // ⚠️ 先判 finish_reason == "length"：被切断时 content 是半句 JSON 甚至没有，
                // 这时候要说的是"太长被切断"，不是"内容无法解析"
                if (finishReason == "length") {
                    trace.record(FailureReason.TRUNCATED.name, systemPrompt, content)
                    return@withContext AnalysisOutcome.Failure(FailureReason.TRUNCATED, content)
                }
                if (content == null) {
                    trace.record(FailureReason.BAD_RESPONSE.name, systemPrompt, text)
                    return@withContext AnalysisOutcome.Failure(FailureReason.BAD_RESPONSE, text)
                }

                val parsed = AnalysisParser.parse(content)
                when {
                    parsed == null -> {
                        trace.record(FailureReason.BAD_RESPONSE.name, systemPrompt, content)
                        AnalysisOutcome.Failure(FailureReason.BAD_RESPONSE, content)
                    }

                    parsed.items.isEmpty() && parsed.echoedItemsKey -> {
                        // 模型按提示词回了 items: []，它照做了——不能说"无法解析"
                        trace.record(FailureReason.NOTHING_FOUND.name, systemPrompt, content)
                        AnalysisOutcome.Failure(FailureReason.NOTHING_FOUND, content)
                    }

                    parsed.items.isEmpty() -> {
                        trace.record(FailureReason.BAD_RESPONSE.name, systemPrompt, content)
                        AnalysisOutcome.Failure(FailureReason.BAD_RESPONSE, content)
                    }

                    else -> {
                        trace.record("success", systemPrompt, content)
                        AnalysisOutcome.Success(parsed.items, content)
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

    private fun fail(reason: FailureReason, raw: String?): AnalysisOutcome {
        // 留痕是旁路：写不进去也照样返回
        kotlinx.coroutines.runBlocking { runCatching { trace.record(reason.name, "", raw) } }
        return AnalysisOutcome.Failure(reason, raw)
    }

    private fun fromThrowable(t: Throwable): AnalysisOutcome = when (t) {
        is InterruptedIOException -> AnalysisOutcome.Failure(FailureReason.TIMEOUT)
        is IOException -> AnalysisOutcome.Failure(FailureReason.OFFLINE)
        else -> AnalysisOutcome.Failure(FailureReason.OFFLINE)
    }

    private fun buildBody(config: VisionConfig, jpeg: ByteArray, systemPrompt: String): String {
        val base64 = Base64.getEncoder().encodeToString(jpeg)
        return buildJsonObject {
            put("model", JsonPrimitive(config.model))
            put("response_format", buildJsonObject { put("type", JsonPrimitive("json_object")) })
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("system"))
                            put("content", JsonPrimitive(systemPrompt))
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", JsonPrimitive("text"))
                                            put("text", JsonPrimitive("这张照片里有哪几件物品？"))
                                        },
                                    )
                                    add(
                                        buildJsonObject {
                                            put("type", JsonPrimitive("image_url"))
                                            put(
                                                "image_url",
                                                buildJsonObject {
                                                    put("url", JsonPrimitive("data:image/jpeg;base64,$base64"))
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }.toString()
    }

    companion object {
        /** 📐 超时由这一处说了算——文案里**故意**不写秒数（`RULES.md` §13.2 第 1 条）。 */
        const val CALL_TIMEOUT_SECONDS = 60

        /** 📐 最多两次：鉴权与配置重试没有意义。 */
        const val MAX_ATTEMPTS = 2

        /**
         * 非 2xx 的映射（`RULES.md` §13.1）——**只有这一处**。
         *
         * 两个必须钉住的细节：
         * - `402` 与"400 + 一句 balance"都算余额不足：只认状态码的话用户会被派去核对模型名，
         *   而真正要办的是充值（DeepSeek 报的是 400 加一句 balance）；
         * - `isBalanceComplaint` 找 `balance` / `quota` / `余额` / `额度`（前两个是实测两家的英文，
         *   后两个是同一句报错的本地化版本）。
         */
        fun classifyStatus(code: Int, body: String): FailureReason? = when {
            code in 200..299 -> null
            code == 401 || code == 403 -> FailureReason.UNAUTHORIZED
            code == 429 -> FailureReason.RATE_LIMITED
            code == 402 -> FailureReason.INSUFFICIENT_BALANCE
            code == 400 && isBalanceComplaint(body) -> FailureReason.INSUFFICIENT_BALANCE
            code == 400 -> FailureReason.BAD_REQUEST
            else -> FailureReason.SERVER
        }

        fun isBalanceComplaint(body: String): Boolean {
            val lower = body.lowercase()
            return listOf("balance", "quota", "余额", "额度").any { lower.contains(it) }
        }
    }
}
