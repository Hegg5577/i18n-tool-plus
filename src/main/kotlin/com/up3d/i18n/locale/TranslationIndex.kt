package com.up3d.i18n.locale

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 译文查询索引：把语言包预解析成 `key -> value` 的扁平映射，供编辑器高频查询。
 *
 * 缓存键为「项目根路径 + 语言」，并记录语言包文件的最后修改时间，
 * 文件内容变了才重新解析，避免每次重绘都读盘。
 */
object TranslationIndex {

    /** 首选显示语言。 */
    const val DEFAULT_LOCALE = "zh"

    /** 首选语言缺 key 时回退到源语言，行为与 i18n-ally 一致（未翻译时显示原文）。 */
    const val FALLBACK_LOCALE = "en"

    /** 允许显示行内译文的文件后缀。 */
    private val SUPPORTED_EXTENSIONS = setOf(
        "js", "jsx", "mjs", "cjs", "ts", "tsx", "vue", "html", "htm"
    )

    private data class Entry(val stamp: String, val values: Map<String, String>)

    private val cache = ConcurrentHashMap<String, Entry>()

    fun isSupported(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS

    /** 清空缓存（语言包被修改后可调用）。 */
    fun invalidate() = cache.clear()

    /**
     * 返回某项目的译文映射，key 为点号路径（如 `common.cancel`）。
     * 解析失败或语言包不存在时返回空表，调用方据此静默跳过提示。
     */
    fun values(projectRoot: String?): Map<String, String> {
        if (projectRoot == null) return emptyMap()

        // 缓存指纹同时包含两个语言包的修改时间，任一变化都会重新解析
        val stamp = buildString {
            for (locale in listOf(DEFAULT_LOCALE, FALLBACK_LOCALE)) {
                append(localeFile(projectRoot, locale).lastModified()).append('|')
            }
        }
        // 缓存键只按项目区分，指纹放在 Entry 里比对，避免文件每次改动都留下一条永不清理的记录
        cache[projectRoot]?.let { if (it.stamp == stamp) return it.values }

        val values = LinkedHashMap<String, String>()
        // 先放源语言，再用首选语言覆盖：未翻译的 key 自动显示英文原文
        values.putAll(load(projectRoot, FALLBACK_LOCALE))
        values.putAll(load(projectRoot, DEFAULT_LOCALE))

        cache[projectRoot] = Entry(stamp, values)
        return values
    }

    /**
     * 单个语言包解析出的 key 数量，0 表示不存在或解析失败。
     * 仅供诊断展示用，不进 [values] 的合并缓存。
     */
    fun countFor(projectRoot: String, locale: String): Int = load(projectRoot, locale).size

    private fun localeFile(projectRoot: String, locale: String): File =
        File(
            File(projectRoot, LocaleRepository.DEFAULT_LOCALES_DIR),
            "$locale/${LocaleRepository.LOCALE_FILE_NAME}"
        )

    private fun load(projectRoot: String, locale: String): Map<String, String> {
        val file = localeFile(projectRoot, locale)
        if (!file.isFile) return emptyMap()
        // 平台自带 gson；万一不可用也只会导致不显示提示，不会让 IDE 报错中断
        return runCatching { flatten(file) }.getOrDefault(emptyMap())
    }

    /** 将嵌套 JSON 展平为点号 key 到字符串值的映射。 */
    private fun flatten(file: File): Map<String, String> {
        val root = JsonParser.parseString(file.readText()).asJsonObject
        val out = LinkedHashMap<String, String>()

        fun walk(obj: JsonObject, prefix: String) {
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
}
