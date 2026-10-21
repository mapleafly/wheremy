package com.wheremy.app.ui.review

/**
 * 别名输入框的**原文**与**列表**之间的唯一换算处（`RULES.md` §15 W9、`UI.md` §6.5）。
 *
 * 为什么输入框要单独存原文：如果每按一键都拿 `aliases` 用「、」拼回去，
 * `、` / `，` / `,` / 空格会在同一帧被 [parse] 吃掉——
 * 结尾空段被 `filter` 丢掉、每段被 `trim()`，于是这几个键在真机上"按了没反应"。
 * 句号不是分隔符，所以它能留下。这不是两套规则，只是**输入框显示原文**：
 * 列表（`draft.aliases`）每次按键都从原文算出来，保存/落库时用的就是它。
 *
 * 落库仍是英文逗号一列（`ItemRepository.csv`），读回来按英文逗号拆（`ItemRepository.splitList`）。
 */
internal object AliasText {
    /** 显示时的分隔符：界面只用顿号，输入时也认另外两个。 */
    const val SEPARATOR = "、"

    /** 三个分隔符；**换行不在里面**（W9 的那一半由 `oneLine` 负责）。 */
    private val SEPARATORS = charArrayOf('、', '，', ',')

    /** 列表 → 输入框原文。 */
    fun join(aliases: List<String>): String = aliases.joinToString(SEPARATOR)

    /**
     * 单行框的兜底：换行**不是**分隔符，也不许留在原文里。
     *
     * 实测（`ReviewAliasInputTest`）：Compose 的 `singleLine = true` 只改渲染，
     * 粘进来的 `\n` 仍会进 `onValueChange`；不折成空格的话，它会成为某个别名的一部分。
     */
    fun oneLine(raw: String): String = raw.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')

    /** 输入框原文 → 落库用的列表：每段 `trim()`，空段丢掉。 */
    fun parse(raw: String): List<String> = raw.split(*SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }
}
