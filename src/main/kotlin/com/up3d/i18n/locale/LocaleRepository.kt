package com.up3d.i18n.locale

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

data class LocaleKeys(
    val locale: String,
    val file: File,
    val keys: Set<String>,
)

data class LocaleReport(
    val locales: List<LocaleKeys>,
    val missing: Map<String, List<String>>,
    val extra: Map<String, List<String>>,
) {
    val isEmpty: Boolean get() = locales.isEmpty()
    val isConsistent: Boolean get() = missing.isEmpty() && extra.isEmpty()
}

enum class KeyStyle {
    NESTED,
    FLAT,
    AUTO;

    companion object {
        fun from(name: String?): KeyStyle = when (name?.lowercase()) {
            "flat" -> FLAT
            "auto" -> AUTO
            else -> NESTED
        }
    }
}

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

    fun scanLocales(): List<LocaleKeys> =
        matcher.resolve(File(projectRoot, localesDir)).mapNotNull { resolved ->
            val keys = runCatching { parseKeys(resolved.file) }.getOrNull() ?: return@mapNotNull null
            LocaleKeys(resolved.locale, resolved.file, keys)
        }

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

    fun valuesOf(locale: String): Map<String, String> {
        val file = localeFile(locale) ?: return emptyMap()
        return valuesOfFile(file)
    }

    fun localeFile(locale: String): File? =
        scanLocales().firstOrNull { it.locale == locale }?.file

    private fun flattenValues(obj: JsonObject, prefix: String = ""): Map<String, String> {
        val result = LinkedHashMap<String, String>()
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

    private fun valuesOfFile(file: File): Map<String, String> = runCatching {
        flattenValues(JsonParser.parseString(file.readText()).asJsonObject)
    }.getOrDefault(emptyMap())
}
