package com.up3d.i18n.editor

/**
 * `t('key')` 用法扫描：在文件文本里找出所有待提示的位置。
 *
 * 收集器（[I18nInlayCollector]）与诊断动作共用同一套判断，
 * 避免"诊断说应该显示、实际却不显示"这种两套逻辑不一致的情况。
 *
 * 沿用 i18n-ally-next 的做法：正则匹配文本而非解析 PSI，
 * 好处是 js / jsx / ts / tsx / vue / html 可以共用同一套逻辑。
 */
object KeyUsageScanner {

    /** 一次 `t('key')` 的出现。 */
    data class Usage(
        /** 点号路径形式的 key，如 `common.cancel`。 */
        val key: String,
        /** 提示插入位置（整个调用结束之后）。 */
        val offset: Int,
        /** 供诊断展示的原文片段。 */
        val raw: String,
    )

    /** 超过此长度的文件不做扫描，保护编辑流畅度。 */
    const val MAX_FILE_LENGTH = 400_000

    /** 单文件最多产出的提示数量。 */
    const val MAX_HINTS_PER_FILE = 300

    /** 寻找调用右括号时最多向前扫描的字符数。 */
    private const val MAX_CALL_SCAN = 2_000

    /**
     * 匹配 `t('a.b')` 与 `i18n.t('a.b')` 两种写法，组 1 为 key。
     */
    private val KEY_REGEX = Regex("""(?:\bi18n\s*\.\s*)?\bt\s*\(\s*['"]([\w.\-]+)['"]""")

    fun isTooLarge(textLength: Int): Boolean = textLength > MAX_FILE_LENGTH

    /**
     * 扫描文本，返回所有命中项（不做 key 是否存在于语言包的过滤）。
     */
    fun scan(text: String): Sequence<Usage> = sequence {
        var produced = 0
        for (match in KEY_REGEX.findAll(text)) {
            if (produced >= MAX_HINTS_PER_FILE) break
            val quoteEnd = match.range.last + 1
            val end = hintOffset(text, quoteEnd)
            yield(Usage(match.groupValues[1], end, match.value))
            produced++
        }
    }

    /**
     * 提示插入位置：定位到整个 `t(...)` 调用的右括号之后，
     * 使带参数的调用也能渲染成 `t('a.b', {...}) 译文`。
     *
     * 从 key 右引号之后开始按括号配对向前扫描（跳过字符串，避免被字符串里的括号干扰）。
     * 扫描有长度上限，遇到不配对或超长时退回「紧贴右引号」的位置，保证总有结果。
     */
    fun hintOffset(text: String, quoteEnd: Int): Int {
        var depth = 1 // 起点位于 t( 内部
        var quote: Char? = null
        var i = quoteEnd
        val limit = minOf(text.length, quoteEnd + MAX_CALL_SCAN)

        while (i < limit) {
            val c = text[i]
            when {
                quote != null -> when {
                    c == '\\' -> i++ // 跳过转义字符
                    c == quote -> quote = null
                }

                c == '\'' || c == '"' || c == '`' -> quote = c
                c == '(' -> depth++
                c == ')' -> if (--depth == 0) return i + 1
            }
            i++
        }
        return quoteEnd
    }
}
