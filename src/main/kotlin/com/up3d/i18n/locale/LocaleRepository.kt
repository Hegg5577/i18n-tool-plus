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

/**
 * 语言包仓库：读取 `{localesDir}/{locale}/index.json` 并做结构比对。
 *
 * 刻意不依赖任何 IDE API —— 纯文件操作，便于单元测试。
 */
class LocaleRepository(
    private val projectRoot: File,
    private val localesDir: String = DEFAULT_LOCALES_DIR,
) {
    companion object {
        const val DEFAULT_LOCALES_DIR = "src/i18n/locales"
        const val LOCALE_FILE_NAME = "index.json"
    }

    /** 扫描目录下所有语言包，按其目录名排序。 */
    fun scanLocales(): List<LocaleKeys> {
        val dir = File(projectRoot, localesDir)
        if (!dir.isDirectory) return emptyList()

        return dir.listFiles { f -> f.isDirectory }
            .orEmpty()
            .sortedBy { it.name }
            .mapNotNull { localeDir ->
                val file = File(localeDir, LOCALE_FILE_NAME)
                if (!file.isFile) return@mapNotNull null
                LocaleKeys(localeDir.name, file, parseKeys(file))
            }
    }

    /**
     * 以 [sourceLocale] 为基准，比对其余语言的 key 完整性。
     * 基准语言缺失时退化为列表中的第一个语言。
     */
    fun buildReport(sourceLocale: String = "en"): LocaleReport {
        val all = scanLocales()
        val source = all.firstOrNull { it.locale == sourceLocale }
            ?: all.firstOrNull()
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
     * 批量翻译时用它取得待翻译的英文原文。
     */
    fun valuesOf(locale: String): Map<String, String> {
        val file = File(File(projectRoot, localesDir), "$locale/${LOCALE_FILE_NAME}")
        if (!file.isFile) return emptyMap()
        return runCatching {
            flattenValues(JsonParser.parseString(file.readText()).asJsonObject)
        }.getOrDefault(emptyMap())
    }

    private fun flattenValues(obj: JsonObject, prefix: String = ""): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for ((name, value) in obj.entrySet()) {
            val key = if (prefix.isEmpty()) name else "$prefix.$name"
            when {
                value.isJsonObject -> result += flattenValues(value.asJsonObject, key)
                value.isJsonPrimitive -> result[key] = value.asString
            }
        }
        return result
    }

    /** 单个语言的语言包文件。 */
    fun localeFile(locale: String): File =
        File(File(projectRoot, localesDir), "$locale/${LOCALE_FILE_NAME}")

    private fun parseKeys(file: File): Set<String> =
        flatten(JsonParser.parseString(file.readText()).asJsonObject)

    /**
     * 将嵌套 JSON 展平为点号 key：
     * `{"common":{"cancel":"取消"}}` -> `common.cancel`
     */
    private fun flatten(obj: JsonObject, prefix: String = ""): Set<String> {
        val result = LinkedHashSet<String>()
        for ((name, value) in obj.entrySet()) {
            val key = if (prefix.isEmpty()) name else "$prefix.$name"
            if (value.isJsonObject) {
                result += flatten(value.asJsonObject, key)
            } else {
                result += key
            }
        }
        return result
    }
}
