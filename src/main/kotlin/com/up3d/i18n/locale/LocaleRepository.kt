package com.up3d.i18n.locale

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/** 单个语言的 key 集合。 */
data class LocaleKeys(
    val locale: String,
    val file: File,
    val keys: Set<String>,
)

/** 多语言 key 一致性报告。 */
data class LocaleReport(
    val locales: List<LocaleKeys>,
    /** locale -> 相比基准语言缺失的 key */
    val missing: Map<String, List<String>>,
    /** locale -> 相比基准语言多出的 key */
    val extra: Map<String, List<String>>,
) {
    val isEmpty: Boolean get() = locales.isEmpty()
    val isConsistent: Boolean get() = missing.isEmpty() && extra.isEmpty()
}

/** key 在语言包里的组织形式。 */
enum class KeyStyle {
    /** 嵌套 JSON：`{"a":{"b":"x"}}` 对应 key `a.b`。 */
    NESTED,

    /** 单层 JSON：key 原样作为顶层字段名，`{"a.b":"x"}` 的 key 就是 `a.b`。 */
    FLAT,

    /** 两种都支持：查找时先试整体 key，再试点号路径展开。 */
    AUTO;

    companion object {
        fun from(name: String?): KeyStyle = when (name?.lowercase()) {
            "flat" -> FLAT
            "auto" -> AUTO
            else -> NESTED
        }
    }
}

/**
 * 语言包仓库：读取语言包并做结构比对。
 *
 * 刻意不依赖任何 IDE API —— 纯文件操作，便于单元测试。
 *
 * 路径与 key 风格全部由构造参数决定，不再硬编码：
 * - [localesDir] 语言包根目录
 * - [pathMatcher] 语言包文件名的匹配模式，支持 `{locale}` / `{namespace}` / `{file}`
 * - [keyStyle] key 是嵌套展开还是平铺
 */
class LocaleRepository(
    private val projectRoot: File,
    private val localesDir: String = DEFAULT_LOCALES_DIR,
    private val pathMatcher: String = PathMatcher.DEFAULT_PATTERN,
    private val keyStyle: KeyStyle = KeyStyle.NESTED,
    private val fileName: String = DEFAULT_FILE_NAME,
) {
    companion object {
        const val DEFAULT_LOCALES_DIR = "src/i18n/locales"
        const val DEFAULT_FILE_NAME = "index.json"
    }

    private val matcher = PathMatcher(pathMatcher, fileName)

    /** 扫描所有语言包。路径布局完全由 pathMatcher 决定。 */
    fun scanLocales(): List<LocaleKeys> =
        matcher.resolve(File(projectRoot, localesDir)).mapNotNull { resolved ->
            val keys = runCatching { parseKeys(resolved.file) }.getOrNull() ?: return@mapNotNull null
            LocaleKeys(resolved.locale, resolved.file, keys)
        }

    /**
     * 以 [sourceLocale] 为基准，比对其余语言的 key 完整性。
     * 基准语言缺失时退化为 key 最多的语言（而非字母序首个 —— 那可能是阿拉伯语等
     * 恰好排在前面的语言，导致整份报告以错误的原文为基准）。
     */
    fun buildReport(sourceLocale: String = "en"): LocaleReport {
        val all = scanLocales()
        val source = all.firstOrNull { it.locale == sourceLocale }
            ?: all.maxByOrNull { it.keys.size }
            ?: return LocaleReport(emptyList(), emptyMap(), emptyMap())

        val missing = LinkedHashMap<String, List<String>>()
        val extra = LinkedHashMap<String, List<String>>()

        for (item in all) {
            if (item.locale == source.locale) continue
            val miss = (source.keys - item.keys).sorted()
            val ext = (item.keys - source.keys).sorted()
            if (miss.isNotEmpty()) missing[item.locale] = miss
            if (ext.isNotEmpty()) extra[item.locale] = ext
        }

        return LocaleReport(all, missing, extra)
    }

    /**
     * 读取某语言的 `key -> 原文` 映射（只取字符串叶子，且排除空字符串）。
     * 批量翻译时用它取得待翻译的原文。
     */
    fun valuesOf(locale: String): Map<String, String> {
        val file = localeFile(locale) ?: return emptyMap()
        // 按扩展名分派：JSON 走 gson，JS 走正则规整后的解析器
        return if (JsLocaleParser.supports(file)) {
            JsLocaleParser.parse(file, keyStyle)
        } else {
            runCatching {
                flattenValues(JsonParser.parseString(file.readText()).asJsonObject)
            }.getOrDefault(emptyMap())
        }
    }

    /** 某语言的语言包文件；路径布局不符模式时返回 null。 */
    fun localeFile(locale: String): File? =
        scanLocales().firstOrNull { it.locale == locale }?.file

    private fun flattenValues(obj: JsonObject, prefix: String = ""): Map<String, String> {
        val result = LinkedHashMap<String, String>()

        // 平铺模式：顶层字段名本身就是完整 key，不再往下拼接
        if (keyStyle == KeyStyle.FLAT) {
            for ((name, value) in obj.entrySet()) {
                if (value.isJsonPrimitive) result[name] = value.asString
            }
            return result
        }

        for ((name, value) in obj.entrySet()) {
            val key = if (prefix.isEmpty()) name else "$prefix.$name"
            when {
                value.isJsonObject -> result += flattenValues(value.asJsonObject, key)
                value.isJsonPrimitive -> result[key] = value.asString
            }
        }
        return result
    }

    private fun parseKeys(file: File): Set<String> = valuesOfFile(file).keys

    /** 按扩展名分派的读取。 */
    private fun valuesOfFile(file: File): Map<String, String> =
        if (JsLocaleParser.supports(file)) {
            JsLocaleParser.parse(file, keyStyle)
        } else {
            runCatching {
                flattenValues(JsonParser.parseString(file.readText()).asJsonObject)
            }.getOrDefault(emptyMap())
        }
}