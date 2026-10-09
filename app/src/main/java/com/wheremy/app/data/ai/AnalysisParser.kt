package com.wheremy.app.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * 把模型回的那段文本读成物品清单（`RULES.md` §5.5）。
 *
 * 三处**都是真实模型行为换来的**，别当成过度设计：
 * 1. **候选顺序**：围栏内 JSON → 最外层 `{...}` → 整段文本 → 散落的每个小对象，然后去重。
 *    `deepseek-flash` 会把 `response_format` 回显成 `{"type": "json_object"}` 再接真正的 JSON，
 *    贪婪的最外层匹配会把两段吞成一段非法文本；不去重就是一件物品数出三件。
 * 2. **先找带 `items` 数组的那个对象**：找不到才退回"所有含已知键的对象"。
 * 3. **`echoedItemsKey`**：把"模型按提示词回了 `items: []`"（它照做了 → `NOTHING_FOUND`）
 *    与"回了一段读不懂的"（→ `BAD_RESPONSE`）分开。
 *
 * 📌 标量与数组的清洗**不对称**（现状，钉住）：`string()` 去空白、丢空串、把字面 `"null"` 归一成 null；
 * `strings()` 只去空白和丢空串，**不**认字面 `"null"`。
 */
object AnalysisParser {

    private val KNOWN_KEYS = setOf(
        "name",
        "category",
        "seasons",
        "color",
        "material",
        "brand",
        "aliases",
        "description",
        "tags",
        "container_guess",
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    data class Parsed(val items: List<AnalysisItem>, val echoedItemsKey: Boolean)

    /** 解析失败给 null（调用方据此判 `BAD_RESPONSE` 或 `NOTHING_FOUND`）。 */
    fun parse(text: String): Parsed? {
        val objects = candidates(text).mapNotNull { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        if (objects.isEmpty()) return null

        val echoed = objects.any { it.containsKey("items") }
        val withItems = objects.firstOrNull { it["items"] is JsonArray }
        val items = if (withItems != null) {
            withItems["items"]!!.jsonArray.mapNotNull { element ->
                (element as? JsonObject)?.let { toItem(it) }
            }
        } else {
            objects.map { toItem(it) }
        }
        return Parsed(items.filterNot { it.isEmpty }, echoed)
    }

    /**
     * 多种候选（`RULES.md` §5.5）：围栏内 → 每个**顶层** `{...}` → 整段，然后去重。
     *
     * ⚠️ 顶层对象要**配平**着切（跳过字符串里的花括号），不能用 `\{[^{}]*\}` 那种简单正则：
     * `{"items":[{...}]}` 里有嵌套，正则匹配不到它——于是"回显的 `{"type":"json_object"}`
     * 后面接真正的 JSON"这种真实回应会被判成"读不懂"。
     */
    fun candidates(text: String): List<String> {
        val out = mutableListOf<String>()
        Regex("```(?:json)?\\s*([\\s\\S]*?)```").findAll(text).forEach { out += it.groupValues[1].trim() }
        out += topLevelObjects(text)
        out += text.trim()
        return out.filter { it.isNotBlank() }.distinct()
    }

    /** 切出文本里每一个**顶层**的完整 JSON 对象（配平花括号、跳过字符串字面量里的括号）。 */
    private fun topLevelObjects(text: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        text.forEachIndexed { index, ch ->
            when {
                escaped -> escaped = false
                inString && ch == '\\' -> escaped = true
                ch == '"' -> inString = !inString
                inString -> Unit
                ch == '{' -> {
                    if (depth == 0) start = index
                    depth++
                }

                ch == '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out += text.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return out
    }

    private fun toItem(obj: JsonObject): AnalysisItem = AnalysisItem(
        name = string(obj["name"]),
        category = string(obj["category"]),
        seasons = strings(obj["seasons"]),
        color = string(obj["color"]),
        material = string(obj["material"]),
        brand = string(obj["brand"]),
        aliases = strings(obj["aliases"]),
        description = string(obj["description"]),
        tags = strings(obj["tags"]),
        containerGuess = string(obj["container_guess"]),
    )

    /** 标量：去空白、丢空串、字面 `"null"`（不分大小写）归一成 null。 */
    private fun string(element: JsonElement?): String? {
        val value = (element as? JsonPrimitive)?.takeIf { !it.isString || true }?.content?.trim()
            ?: (element as? JsonPrimitive)?.content?.trim()
            ?: return null
        if (value.isEmpty()) return null
        if (value.equals("null", ignoreCase = true)) return null
        return if (element is JsonNull) null else value
    }

    /** 数组：只去空白和丢空串，**不**认字面 `"null"`（现状，别顺手统一）。 */
    private fun strings(element: JsonElement?): List<String> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }.filter { it.isNotEmpty() }
    }
}
