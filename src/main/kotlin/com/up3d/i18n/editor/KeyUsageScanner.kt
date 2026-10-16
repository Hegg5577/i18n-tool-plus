package com.up3d.i18n.editor

import com.up3d.i18n.framework.Frameworks
import com.up3d.i18n.framework.I18nFramework

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
        /** 命中的框架 id，诊断时用于说明「为什么这条被识别」。 */
        val frameworkId: String = "",
    )

    /** 超过此长度的文件不做扫描，保护编辑流畅度。 */
    const val MAX_FILE_LENGTH = 400_000

    /** 单文件最多产出的提示数量。 */
    const val MAX_HINTS_PER_FILE = 300

    /** 寻找调用右括号时最多向前扫描的字符数。 */
    private const val MAX_CALL_SCAN = 2_000

    /**
     * 默认框架集合。
     *
     * 四个内置框架的正则并集。任何一条命中即算识别成功 —— 因为同一个项目里
     * 经常混用 `t('a')` 与 `$t('b')`，按"或"匹配比判定"当前文件属于哪个框架"更实用。
     */
    val DEFAULT_FRAMEWORKS: List<I18nFramework> = Frameworks.ALL

    /**
     * 兜底正则：只认裸 `t('key')`，且 key 限于点号路径形态。
     *
     * 存在的意义是当语言包加载失败、框架推断不出时，诊断仍能给出有效信息，
     * 而不是"什么都没扫到"。
     */
    private val FALLBACK_REGEX = Regex("""(?:\bi18n\s*\.\s*)?\bt\s*\(\s*['"]([\w.\-]+)['"]""")

    fun isTooLarge(textLength: Int): Boolean = textLength > MAX_FILE_LENGTH

    /**
     * 扫描文本，返回所有命中项（不做 key 是否存在于语言包的过滤）。
     *
     * @param frameworks 参与匹配的框架；为空时回退到内置四框架。
     */
    fun scan(text: String, frameworks: List<I18nFramework> = DEFAULT_FRAMEWORKS): Sequence<Usage> =
        scanWith(text, frameworks.ifEmpty { DEFAULT_FRAMEWORKS })

    /**
     * 用指定框架集合扫描。
     *
     * 多个框架的正则会命中同一处调用（例如 `i18next.t('a')` 同时被裸`t` 与
     * `i18next.t` 两种写法匹配），这里按「偏移量 + key」去重，保证一个调用只产出一条提示。
     */
    fun scanWith(text: String, frameworks: List<I18nFramework>): Sequence<Usage> = sequence {
        if (text.length > MAX_FILE_LENGTH) return@sequence

        val seen = HashSet<Int>()
        var produced = 0

        outer@ for (framework in frameworks) {
            for (regex in framework.usagePatterns) {
                for (match in regex.findAll(text)) {
                    if (produced >= MAX_HINTS_PER_FILE) break@outer
                    val key = match.groups["key"]?.value?.trim()?.trim('"', '\'')
                    if (key.isNullOrBlank()) continue
                    // 过短的 key 多半是 `t('a')` 这类噪声，按需过滤但保留常见短名
                    if (key.length > 200) continue

                    val quoteEnd = match.range.last + 1
                    val offset = hintOffset(text, quoteEnd)
                    if (!seen.add(offset)) continue

                    yield(Usage(key, offset, match.value, framework.id))
                    produced++
                }
            }
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
