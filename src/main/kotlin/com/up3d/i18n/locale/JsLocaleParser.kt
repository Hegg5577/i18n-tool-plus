package com.up3d.i18n.locale

import java.io.File

/**
 * JS 语言包解析器：把 `export default {...}` 读成 `key -> value` 映射。
 *
 * 前端项目里除了 JSON，语言包也常写成 JS 文件，例如
 * `src/lang/{locale}.js`（vue-i18n 生态尤其常见）。这类文件形如：
 * ```js
 * 文件开头常有一段头部块注释（koroFileHeader 自动生成的那种）
 * export default {
 *     public: { title: "MAKE DENTAL DESIGN AN ART" },
 *     common: { ok: '确定', tip: `模板字符串` },
 * }
 * ```
 *
 * 刻意**不引入完整 JS 解析器**（无 Node、无 GraalVM），
 * 而是把内容规整成 JSON 再交给 gson —— 因为语言包在实际项目中
 * 几乎只用对象字面量，不含函数调用或表达式。这一点是安全的，
 * 解析失败会返回空表并静默跳过，不会让插件出错。
 *
 * 需要处理的差异：
 * - 头部/行尾注释
 * - `export default` / `module.exports =` / `export {}` 前缀
 * - 键名可以不带引号（`title:`）
 * - 单引号与双引号混用
 * - 模板字符串与字符串拼接（含占位符 `{name}`）
 * - 尾随逗号
 */
object JsLocaleParser {

    /** 语言包文件扩展名。 */
    val SUPPORTED_EXTENSIONS = setOf("js", "mjs", "cjs")

    /** 判断文件是否是 JS 语言包。 */
    fun supports(file: File): Boolean =
        file.extension.lowercase() in SUPPORTED_EXTENSIONS

    /**
     * 解析 JS 语言包。
     *
     * @param keyStyle nested 时把嵌套对象展平为点号 key；flat 时只取顶层
     * @return `key -> value`；解析失败返回空表
     */
    fun parse(file: File, keyStyle: KeyStyle = KeyStyle.NESTED): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            val json = toJsonLike(file.readText())
            val root = com.google.gson.JsonParser.parseString(json).asJsonObject
            val out = LinkedHashMap<String, String>()

            if (keyStyle == KeyStyle.FLAT) {
                for ((name, value) in root.entrySet()) {
                    if (value.isJsonPrimitive) out[name] = value.asString
                }
            } else {
                fun walk(obj: com.google.gson.JsonObject, prefix: String) {
                    for ((name, value) in obj.entrySet()) {
                        val key = if (prefix.isEmpty()) name else "$prefix.$name"
                        when {
                            value.isJsonObject -> walk(value.asJsonObject, key)
                            value.isJsonPrimitive -> out[key] = value.asString
                            // 数组、数字、布尔等一律转字符串，保证「有没有译文」这个判断是完整的
                            else -> out[key] = value.toString()
                        }
                    }
                }
                walk(root, "")
            }
            out
        }.getOrDefault(emptyMap())
    }

    /**
     * 把 JS 源码规整成合法 JSON 文本。
     *
     * 步骤：剥注释 → 截出对象字面量 → 规范化键名与引号 → 去掉尾随逗号。
     *
     * 关键的一步是**把单引号与反引号统一成双引号** —— JSON 只认双引号，
     * 而 JS 三种引号都用。只处理裸键名是不够的：`ok: '确定'` 照样解析失败。
     */
    internal fun toJsonLike(source: String): String {
        val stripped = stripComments(source)
        val body = extractObjectLiteral(stripped)
        val quoted = quoteBareKeys(body)
        val normalizedQuotes = normalizeQuotes(quoted)
        return removeTrailingCommas(normalizedQuotes)
    }

    /**
     * 把单引号与反引号字符串统一为双引号。
     *
     * 需要同时处理字符串内部的转义：
     * - 单引号字符串里的 `\"` 是字面量双引号，输出时不必转义
     * - 反引号字符串里的 `${...}` 是模板插值，语言包里一般只放纯文本占位符
     *   （如 `{name}`），保留原样即可；遇到真实插值时降级为字面量文本。
     */
    private fun normalizeQuotes(body: String): String {
        val out = StringBuilder(body.length)
        var i = 0

        while (i < body.length) {
            val c = body[i]
            when (c) {
                '\'' -> {
                    val parsed = readSingleQuoted(body, i)
                    if (parsed != null) {
                        out.append(parsed.first)
                        i = parsed.second
                    } else {
                        out.append(c)
                        i++
                    }
                }

                '`' -> {
                    val end = findClosing(body, i, '`')
                    if (end < 0) {
                        out.append(c)
                        i++
                    } else {
                        out.append(escapeForJson(body.substring(i + 1, end)))
                        i = end + 1
                    }
                }

                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    /**
     * 读取单引号字符串。
     * @return 输出片段与结束下标；遇到未闭合时返回 null
     */
    private fun readSingleQuoted(body: String, start: Int): Pair<String, Int>? {
        var i = start + 1
        val sb = StringBuilder("\"")
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> {
                    val next = body[i + 1]
                    when (next) {
                        // \' 在单引号字符串里就是普通单引号
                        '\'' -> sb.append('\'')
                        '"' -> sb.append("\\\"")
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        else -> sb.append('\\').append(next)
                    }
                    i += 2
                }
                c == '\'' -> {
                    sb.append('"')
                    return sb.toString() to (i + 1)
                }
                c == '\n' -> return null // 单引号字符串不跨行，说明解析状态已错
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return null
    }

    /** 从 [start] 起找到 [quote] 的闭合位置，返回其下标；未闭合返回 -1。 */
    private fun findClosing(body: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < body.length) {
            val c = body[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == quote) return i
            i++
        }
        return -1
    }

    /** 把字符串内容转成 JSON 字符串字面量（含首尾双引号）。 */
    private fun escapeForJson(inner: String): String {
        val sb = StringBuilder("\"")
        for (c in inner) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /**
     * 剥离 JS 注释，同时保留字符串字面量里的行注释符与块注释符。
     *
     * 状态机：normal / lineComment / blockComment / single / double / template。
     * 用状态机而非正则，是因为字符串里出现注释符是常态（如 URL 里的双斜杠）。
     */
    private fun stripComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var state = State.NORMAL
        // 记录字符串起始位置，用于原样拷贝字符串内容
        var quoteStart = -1

        while (i < source.length) {
            val c = source[i]
            val next = source.getOrNull(i + 1)

            when (state) {
                State.NORMAL -> when {
                    c == '/' && next == '/' -> {
                        state = State.LINE_COMMENT
                        i++
                    }
                    c == '/' && next == '*' -> {
                        state = State.BLOCK_COMMENT
                        i++
                    }
                    c == '\'' || c == '"' || c == '`' -> {
                        state = when (c) {
                            '\'' -> State.SINGLE
                            '"' -> State.DOUBLE
                            else -> State.TEMPLATE
                        }
                        quoteStart = out.length
                        out.append(c)
                    }
                    else -> out.append(c)
                }

                State.LINE_COMMENT -> {
                    if (c == '\n') {
                        state = State.NORMAL
                        out.append(c)
                    }
                }

                State.BLOCK_COMMENT -> {
                    if (c == '*' && next == '/') {
                        state = State.NORMAL
                        i++
                    } else if (c == '\n') {
                        // 保留换行，避免行号信息全丢（对错误定位有用）
                        out.append(c)
                    }
                }

                State.SINGLE, State.DOUBLE, State.TEMPLATE -> {
                    out.append(c)
                    when {
                        c == '\\' && i + 1 < source.length -> {
                            out.append(next)
                            i++
                        }
                        (state == State.SINGLE && c == '\'') ||
                            (state == State.DOUBLE && c == '"') ||
                            (state == State.TEMPLATE && c == '`') -> state = State.NORMAL
                    }
                    // 模板字符串的 ${} 里可能嵌套字符串，简单处理：原样保留即可，
                    // 因为 ${} 内部内容通常是变量名，最终不参与 key 结构
                }
            }
            i++
        }
        return out.toString()
    }

    private enum class State { NORMAL, LINE_COMMENT, BLOCK_COMMENT, SINGLE, DOUBLE, TEMPLATE }

    /**
     * 截出对象字面量。
     *
     * 先按行去掉 `import` / `export default` / `module.exports =` 这类顶层语句，
     * 再从第一个 `{` 开始按括号配对截取 —— 括号配对同样走状态机，跳过字符串里的括号。
     */
    private fun extractObjectLiteral(source: String): String {
        // 去掉以 import 开头的行（语言包里几乎不会出现真正的 import 值）
        val withoutImports = source.lines()
            .filterNot { it.trimStart().startsWith("import ") || it.trimStart().startsWith("import{") }
            .joinToString("\n")

        // 去掉 module.exports = 与 export default 之前缀
        val withoutExport = withoutImports
            .replace(Regex("""^\s*export\s+default\s*""", RegexOption.MULTILINE), "")
            .replace(Regex("""^\s*module\.exports\s*=\s*""", RegexOption.MULTILINE), "")
            .replace(Regex("""^\s*export\s*""", RegexOption.MULTILINE), "")

        val start = withoutExport.indexOf('{')
        if (start < 0) return withoutExport.trim()

        var depth = 0
        var i = start
        var quote: Char? = null
        while (i < withoutExport.length) {
            val c = withoutExport[i]
            when {
                quote != null -> {
                    if (c == '\\') i++
                    else if (c == quote) quote = null
                }
                c == '\'' || c == '"' || c == '`' -> quote = c
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return withoutExport.substring(start, i + 1)
                }
            }
            i++
        }
        return withoutExport.substring(start)
    }

    /**
     * 给裸键名补引号：`title:` → `"title":`。
     *
     * 只处理「标识符 + 冒号」这个形态，且跳过已在字符串里的部分 ——
     * 否则 `"a:b"` 这样的值会被误改。
     */
    private fun quoteBareKeys(body: String): String {
        val out = StringBuilder(body.length)
        var i = 0
        var quote: Char? = null

        while (i < body.length) {
            val c = body[i]
            when {
                quote != null -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < body.length) {
                        out.append(body[i + 1])
                        i++
                    } else if (c == quote) {
                        quote = null
                    }
                }
                c == '\'' || c == '"' || c == '`' -> {
                    quote = c
                    out.append(c)
                }
                // 标识符起始字符
                c.isLetter() || c == '_' || c == '$' -> {
                    // 判断是否处于「键名位置」：往前跳过空白后若是 { 或 , ，则后面须紧跟冒号
                    val keyStart = i
                    var j = i
                    while (j < body.length && (body[j].isLetterOrDigit() || body[j] == '_' || body[j] == '$' || body[j] == '-')) {
                        j++
                    }
                    var k = j
                    while (k < body.length && body[k].isWhitespace()) k++
                    if (k < body.length && body[k] == ':') {
                        out.append('"').append(body, keyStart, j).append('"')
                        i = j
                        continue
                    }
                    out.append(body, keyStart, j)
                    i = j
                    continue
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /** 去掉对象/数组末尾的多余逗号，否则不是合法 JSON。 */
    private fun removeTrailingCommas(body: String): String {
        val out = StringBuilder(body.length)
        var quote: Char? = null

        for (i in body.indices) {
            val c = body[i]
            when {
                quote != null -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < body.length) {
                        out.append(body[i + 1])
                        // 跳过已追加的转义字符
                        out.deleteCharAt(out.length - 1)
                        out.append(body[i + 1])
                    } else if (c == quote) {
                        quote = null
                    }
                }
                c == '\'' || c == '"' || c == '`' -> {
                    quote = c
                    out.append(c)
                }
                c == ',' -> {
                    // 找到下一个非空白字符，若是 } 或 ] 就丢掉这个逗号
                    var j = i + 1
                    while (j < body.length && body[j].isWhitespace()) j++
                    if (j < body.length && (body[j] == '}' || body[j] == ']')) continue
                    out.append(c)
                }
                else -> out.append(c)
            }
        }
        return out.toString()
    }
}