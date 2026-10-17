package com.up3d.i18n.locale

import java.io.File

/**
 * 语言包路径匹配器：把配置里的模式串还原成磁盘上的真实路径。
 *
 * 沿用 i18n-ally-next 的 `pathMatcher` 思路，用占位符描述任意目录结构，
 * 从而覆盖不同项目的语言包布局，而不必改代码：
 *
 * - `{locale}` 语言代码，如 `en` / `zh-CN`
 * - `{namespace}` 命名空间，如 `common` / `auth`
 * - `{file}` 语言包文件名，取自配置，默认 `index.json`
 *
 * 常见示例：
 * - `{locale}/{file}`           → `locales/en/index.json`（每语言一目录）
 * - `{locale}.json`             → `locales/en.json`（每语言一文件）
 * - `{locale}/{namespace}.json` → `locales/en/common.json`
 * - `{namespace}.{locale}.json` → `locales/common.en.json`
 */
class PathMatcher(
    val pattern: String,
    private val defaultFileName: String = LocaleRepository.DEFAULT_FILE_NAME,
) {

    private val segments: List<String> =
        pattern.trim().split('/').map { it.trim() }.filter { it.isNotEmpty() }

    /** 模式是否含 `{locale}` 占位符。不含则无法自动发现语言。 */
    val usesLocale: Boolean get() = pattern.contains(LOCALE_TOKEN)

    /** 模式是否含 `{namespace}` 占位符。 */
    val usesNamespace: Boolean get() = pattern.contains(NAMESPACE_TOKEN)

    /**
     * 枚举该模式下所有语言包文件。
     *
     * 做法是**逐层下钻目录树**，并且把「当前已知的语言」跟着路径一起传递：
     * - `{locale}` 作为独立路径段 → 该层的子目录名即语言，记录后继续下钻
     * - `{file}` / `{namespace}` → 该层的 json 文件即语言包
     * - `{locale}.json` 这类「占位符 + 扩展名」 → 目录名是语言，文件名按模式拼出
     * - 普通段 → 按字面量匹配
     *
     * 关键点：只有**整个模式走完**才返回结果。之前的实现在遇到 `{locale}` 时
     * 就提前 return，导致默认模式 `{locale}/{file}` 里的 `{file}` 永远处理不到，
     * 最终返回空列表 —— 表现为「一个国际化都显示不出来」。
     */
    fun resolve(root: File): List<ResolvedLocale> {
        if (!root.isDirectory || segments.isEmpty()) return emptyList()

        // 每层下钻的结果：语言（可能为 null）+ 当前目录
        var level: List<Level> = listOf(Level(null, root))

        for (segment in segments) {
            val next = when {
                // `{locale}.json`：当前目录里直接躺着一批 `xx.json` 文件，
                // 语言来自文件名本身（en.json → en），不需要有子目录
                segment.startsWith(LOCALE_TOKEN) && hasExtension(segment) -> {
                    level.flatMap { (_, dir) ->
                        jsonFiles(dir).mapNotNull { file ->
                            val name = segment.removePrefix(LOCALE_TOKEN).removePrefix(".")
                            // 只保留与模式后缀一致的文件，避免把common.json 也当成语言
                            if (name.isNotEmpty() && !file.name.endsWith(name, ignoreCase = true)) return@mapNotNull null
                            Level(localeFromFileName(file), file)
                        }
                    }
                }

                // `{namespace}.json`：同样是当前目录的文件，语言沿用外层记录的
                segment.startsWith(NAMESPACE_TOKEN) && hasExtension(segment) -> {
                    level.flatMap { (locale, dir) ->
                        val suffix = segment.removePrefix(NAMESPACE_TOKEN).removePrefix(".")
                        jsonFiles(dir)
                            .filter { suffix.isEmpty() || it.name.endsWith(suffix, ignoreCase = true) }
                            .map { Level(locale, it) }
                    }
                }

                // 独立的 `{locale}` 段：子目录名即语言，记录后继续往下走
                segment == LOCALE_TOKEN -> level.flatMap { (_, dir) ->
                    childDirs(dir).map { Level(it.name, it) }
                }

                // `{namespace}` 段：该层的 json 文件即语言包
                segment == NAMESPACE_TOKEN -> level.flatMap { (locale, dir) ->
                    jsonFiles(dir).map { Level(locale, it) }
                }

                // `{file}` 段：在当前目录里取配置的文件名；取不到就退回第一个 json
                segment == FILE_TOKEN -> level.flatMap { (locale, dir) ->
                    val exact = File(dir, defaultFileName)
                    val file = if (exact.isFile) exact else jsonFiles(dir).firstOrNull()
                    if (file == null) emptyList() else listOf(Level(locale, file))
                }

                // 普通路径段：按字面量下钻
                else -> level.mapNotNull { (locale, dir) ->
                    File(dir, segment).takeIf { it.exists() }?.let { Level(locale, it) }
                }
            }

            if (next.isEmpty()) return emptyList()
            level = next
        }

        return level
            .filter { it.dir.isFile }
            .map { (locale, file) -> ResolvedLocale(locale ?: localeFromFileName(file), file) }
            .distinctBy { it.locale to it.file.path }
            .sortedBy { it.locale }
    }

    /** 下钻过程中的状态：已知语言（未定则为 null）+ 当前路径。 */
    private data class Level(val locale: String?, val dir: File)

    /** 该片段自带扩展名（说明它描述的是文件名而非目录名）。 */
    private fun hasExtension(segment: String): Boolean =
        segment.substringAfterLast('/').substringAfterLast('.', "").isNotEmpty()

    private fun childDirs(dir: File): List<File> =
        dir.listFiles { f: File -> f.isDirectory }?.toList().orEmpty()

    private fun jsonFiles(dir: File): List<File> =
        dir.listFiles { f: File -> f.isFile && f.name.endsWith(".json", ignoreCase = true) }
            ?.toList()
            .orEmpty()

    /** 模式里没写明语言时，从文件名里推断：`en.json` → `en`，`common.en.json` → `en`。 */
    private fun localeFromFileName(file: File): String {
        val name = file.name.removeSuffix(".json")
        // 取最后一段：common.en → en
        val candidate = name.substringAfterLast('.', name)
        return candidate.ifBlank { name }
    }

    companion object {
        /** 默认模式：每语言一个目录，目录内index.json 为该语言全部文案。 */
        const val DEFAULT_PATTERN = "{locale}/{file}"

        const val LOCALE_TOKEN = "{locale}"
        const val NAMESPACE_TOKEN = "{namespace}"
        const val FILE_TOKEN = "{file}"
    }
}

/** 一个语言包文件：语言代码 + 磁盘路径。 */
data class ResolvedLocale(val locale: String, val file: File)