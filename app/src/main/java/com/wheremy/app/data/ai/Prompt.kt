package com.wheremy.app.data.ai

/**
 * 提示词（`RULES.md` §5.6）。
 *
 * 🔒 **发出去的那份和留痕里的那份必须是同一串字**：在 [build] 里算一次、往下传。
 * 否则读留痕时核对的是"我们以为发了的"，不是"真发了的"。
 *
 * 🗣 引号里的句子是文档给的**逐字**要求，不许改词；其余是从这些要求拼起来的叙述。
 *
 * 🔗 `FR-CFG-06`：类别与季节词表**由调用方传进来**（用户改过就用用户那份）——
 * 提示词里必须写用户自己那份，否则模型会回一堆词表外的值，芯片行上显示不出选中态。
 */
object Prompt {

    private const val ROLE =
        "你是帮用户把家里物品记下来的助手。看这张照片，把里面的物品列出来，最多 4 件；" +
            "只有一件就只回一件，同一件不要重复列。"

    /** 🗣 逐字：批次 AJ 真机跑出来的——桌上放一副耳机，模型会把桌子也列成第二件。 */
    private const val SUPPORT_SURFACE =
        "物品放在别的东西上面时，只列物品本身，不要把桌面、地板、床面、抽屉内壁这类承托面也算成一件。"

    private const val OUTPUT_RULE =
        "认不出物品时 items 填 []。只输出一个 JSON 对象，不要 Markdown 围栏。"

    /** 🔒 位置清单那一版**整句替换** `container_guess` 的问法（不是补充）：两句并存时模型会贴着字段回"橱柜"。 */
    private const val CONTAINER_WITH_LIST =
        "从文末「可选位置」清单里原样抄一条完整路径（连父层级一起，一个字都不要改），" +
            "没有一个合适就填 null；不要只填末级那个词，也不要发明清单外的位置。"

    private const val CONTAINER_GENERIC =
        "如果看得出来它平时收在哪里，填一条位置路径；看不出来就填 null。"

    /**
     * @param categories 生效的物品类别词表。
     * @param seasons 生效的季节词表。
     * @param locationPaths 喂给模型的候选：`LocationTree.selectablePaths()`（先序、**根不列**）。
     *   空清单时「可选位置」整段不出现、`container_guess` 退回通用问法（`RULES.md` §5.6）。
     */
    fun build(categories: List<String>, seasons: List<String>, locationPaths: List<String>): String = buildString {
        appendLine(ROLE)
        appendLine(SUPPORT_SURFACE)
        appendLine(OUTPUT_RULE)
        appendLine("回一个 JSON 对象，形如 {\"items\": [ {...}, {...} ]}。每个物品的字段：")
        appendLine("- name：必填，口语叫法（比如「数据线」而不是「USB Type-C 线缆」）")
        appendLine("- category：从这 ${categories.size} 个里选一个：${categories.joinToString(" ")}")
        appendLine("- seasons：只填 ${seasons.joinToString("/")} 里确实相关的，可以多个")
        appendLine("- color / material：看得出来的话")
        appendLine("- brand：仅当照片上文字清晰可读时才填，否则填 null。绝对不要猜测品牌。")
        appendLine("- aliases：0–3 个其它叫法")
        appendLine("- description：≤60 字")
        appendLine("- tags：3–8 个短标签")
        if (locationPaths.isEmpty()) {
            appendLine("- container_guess：$CONTAINER_GENERIC")
        } else {
            appendLine("- container_guess：$CONTAINER_WITH_LIST")
            appendLine()
            appendLine("可选位置：")
            locationPaths.forEach { appendLine("- $it") }
        }
        append("所有不确定的字段一律填 null，不要编造。")
    }
}
