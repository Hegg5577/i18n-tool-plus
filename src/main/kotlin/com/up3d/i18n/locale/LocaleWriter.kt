package com.up3d.i18n.locale

import java.io.File

/**
 * 语言包写入器：支持 JSON 与 JS 两种格式。
 *
 * 存在的原因是批量翻译与手动编辑都需要「改一个 key 的值，其余原样不动」，
 * 而两种格式的写法完全不同：
 * - JSON 用 gson 解析成对象树再序列化，能天然保序
 * - JS 是 `export default {...}`，gson 处理不了，只能在文本层面做**精确插入**
 *
 * JS 的写入刻意不做「反序列化再序列化」—— 那会把引号风格、缩进、
 * 注释全部重排，diff 会变得无法review。只在目标位置插入新行、
 * 或就地替换已有值，其余字节完全不动。
 */
interface LocaleWriter {
    /** 读取语言包为点号 key 到文本的映射。 */
    fun read(): Map<String, String>

    /**
     * 写入一条译文。
     *
     * @return 成功返回 true；key 所在结构无法定位（如父节点是字符串）返回 false
     */
    fun set(dottedKey: String, value: String): Boolean

    /** 序列化回文件内容。 */
    fun render(): String
}

/**
 * JSON 语言包写入器。
 *
 * 依赖 gson 的 LinkedTreeMap 保序特性：遍历顺序即原文件顺序，
 * 新增的 key 追加到同级末尾，不重排既有内容。
 */
class JsonLocaleWriter(private val text: String) : LocaleWriter {

    private val root = com.google.gson.JsonParser.parseString(text).asJsonObject

    override fun read(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        fun walk(obj: com.google.gson.JsonObject, prefix: String) {
            for ((name, value) in obj.entrySet()) {
                val key = if (prefix.isEmpty()) name else "$prefix.$name"
                when {
                    value.isJsonObject -> walk(value.asJsonObject, key)
                    value.isJsonPrimitive -> out[key] = value.asString
                }
            }
        }
        walk(root, "")
        return out
    }

    override fun set(dottedKey: String, value: String): Boolean = runCatching {
        LocaleFileWriter.setNested(root, dottedKey, value)
        true
    }.getOrDefault(false)

    override fun render(): String = LocaleFileWriter.render(root)
}

/**
 * JS 语言包写入器：在文本层面做精确插入 / 就地替换。
 *
 * 支持的写入位置由三个条件共同确定：
 * 1. key 在文件中已存在 —— 直接替换那一行的值
 * 2. key 的父对象存在但 key 缺失 —— 在该对象的闭合括号前插入新行
 * 3. 父对象都不存在 —— 视为不可写，返回 false（避免猜错结构把文件改坏）
 */
class JsLocaleWriter(text: String) : LocaleWriter {

    /** 当前文本，set() 会在其上做精确修改。 */
    private var current: String = text

    // 读current 而非构造参数 text：写入后文本已变，
    // 读旧值会导致 set() 返回 true 但 read() 仍是旧值，看起来像写入没生效
    override fun read(): Map<String, String> = runCatching {
        val json = JsLocaleParser.toJsonLike(current)
        val root = com.google.gson.JsonParser.parseString(json).asJsonObject
        val out = LinkedHashMap<String, String>()

        fun walk(obj: com.google.gson.JsonObject, prefix: String) {
            for ((name, value) in obj.entrySet()) {
                val key = if (prefix.isEmpty()) name else "$prefix.$name"
                when {
                    value.isJsonObject -> walk(value.asJsonObject, key)
                    value.isJsonPrimitive -> out[key] = value.asString
                }
            }
        }
        walk(root, "")
        out
    }.getOrDefault(emptyMap())

    /**
     * 写入一条译文。
     *
     * 优先走「文本精确编辑」：能定位到原有行就替换或插入，
     * 定位不到时**不做任何改动**并返回 false —— 这是刻意的，
     * 宁可让用户手动加，也不能把语言包结构改坏。
     */
    override fun set(dottedKey: String, value: String): Boolean {
        if (replaceExisting(dottedKey, value)) return true
        return insertIntoParent(dottedKey, value)
    }

    /**
     * 已存在该 key 时就地替换其值。
     *
     * 匹配的是**叶子键名**（`public.title` 里的 `title`）而不是完整点号路径 ——
     * JS 文件里嵌套结构就是一层层的对象字面量，文本中根本不存在 `public.title`
     * 这个字面量。早期版本用完整路径去 find，永远匹配不到。
     *
     * 为了避免 `title` 在别的分支下重名导致改错地方，必须先定位父对象的括号范围，
     * 只在该范围内搜索叶子键名。
     */
    private fun replaceExisting(dottedKey: String, value: String): Boolean {
        val parts = dottedKey.split('.')
        val leafKey = parts.last()

        // 顶层 key（没有点号）在整个对象字面量范围内搜索；
        // 带点号的必须限定在父对象的括号范围内，避免改到同名兄弟分支
        val scope = if (parts.size < 2) {
            rootObjectRange() ?: return false
        } else {
            parentObjectRange(parts.dropLast(1).joinToString(".")) ?: return false
        }
        val scopeText = current.substring(scope.first, scope.last + 1)

        // 叶子键名：允许带引号或裸写，后面跟冒号与字符串字面量
        val leafPattern = Regex(
            """(?:["']${Regex.escape(leafKey)}["']|${Regex.escape(leafKey)})\s*:\s*(["'])(?:\\.|(?!\1)[^\\])*\1"""
        )
        val match = leafPattern.find(scopeText) ?: return false

        val absoluteStart = scope.first + match.range.first
        val absoluteEnd = scope.first + match.range.last + 1
        val replacement = "${leafKey}: ${quoteForJs(value)}"
        applyText(current.substring(0, absoluteStart) + replacement + current.substring(absoluteEnd))
        return true
    }

    /** 定位最外层对象字面量的下标范围（含首尾花括号）。 */
    private fun rootObjectRange(): IntRange? {
        val start = current.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var i = start
        while (i < current.length) {
            when (current[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return start..i
                }
            }
            i++
        }
        return null
    }

    /**
     * 定位父对象字面量在文本中的下标范围（含首尾花括号）。
     *
     * 逐级下钻：`a.b.c` 先找到 `a: {`，再在其中找到 `b: {`。
     * 找不到任何一级就返回 null —— 此时不写入，绝不猜。
     */
    private fun parentObjectRange(dottedKey: String): IntRange? {
        val parts = dottedKey.split('.')
        var searchFrom = 0
        var openBrace = -1

        for (part in parts) {
            val keyPattern = Regex("""(?:["']${Regex.escape(part)}["']|${Regex.escape(part)})\s*:\s*\{""")
            val keyMatch = keyPattern.find(current, searchFrom) ?: return null
            openBrace = current.indexOf('{', keyMatch.range.first)
            if (openBrace < 0) return null
            searchFrom = openBrace + 1
        }

        // 从开括号做配对，找到对应的闭括号
        var depth = 0
        var i = openBrace
        while (i < current.length) {
            when (current[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return openBrace..i
                }
            }
            i++
        }
        return null
    }

    /** 父对象存在但 key 缺失时，在该对象的闭合括号前插入一行。 */
    private fun insertIntoParent(dottedKey: String, value: String): Boolean {
        val parts = dottedKey.split('.')
        val leafKey = parts.last()

        val scope = if (parts.size < 2) {
            rootObjectRange() ?: return false
        } else {
            parentObjectRange(parts.dropLast(1).joinToString(".")) ?: return false
        }
        val closeBrace = scope.last

        // 闭合括号前的内容：只有空白（多行格式）时才沿用其缩进
        val lineStart = current.lastIndexOf('\n', closeBrace - 1).let { if (it < 0) 0 else it + 1 }
        val beforeBrace = current.substring(lineStart, closeBrace)
        val isMultiline = beforeBrace.isBlank()

        val insert = if (isMultiline) {
            // 多行：闭合括号独占一行，沿用同级缩进
            val indent = beforeBrace.takeWhile { it == ' ' || it == '\t' }
            "\n$indent$leafKey: ${quoteForJs(value)},\n$indent"
        } else {
            // 单行（如 `export default { public: { title: "x" } }`）：
            // 闭合括号前不是空白，直接就地插入，前面补逗号、后面留一个空格。
            // 早期版本在这里无条件插 `\n`，会把单行文件从中间截断成非法 JS。
            val needsComma = beforeBrace.isNotBlank() &&
                !beforeBrace.trimEnd().endsWith('{') &&
                !beforeBrace.trimEnd().endsWith(',')
            val separator = if (needsComma) ", " else if (beforeBrace.trimEnd().endsWith(',')) " " else " "
            "$separator$leafKey: ${quoteForJs(value)} "
        }

        applyText(current.substring(0, closeBrace) + insert + current.substring(closeBrace))
        return true
    }

    private fun applyText(updated: String) {
        current = updated
    }

    override fun render(): String = current

    /** 渲染成 JS 字面量：双引号 + 转义。 */
    private fun quoteForJs(value: String): String {
        val sb = StringBuilder("\"")
        for (c in value) {
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
}