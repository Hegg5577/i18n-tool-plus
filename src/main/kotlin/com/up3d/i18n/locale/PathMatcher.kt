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
 * - `{locale}/{file}`→ `locales/en/index.json`（每语言一目录）
 * - `{locale}.json`          → `locales/en.json`（每语言一文件）
 * - `{locale}/{namespace}.json` → `locales/en/common.json`
 * - `{namespace}.{locale}.json` → `locales/common.en.json`
 */
class PathMatcher(val pattern: String, val defaultFileName: String = "index.json") {

    private val segments: List<String> = pattern.trim().split('/').filter { it.isNotBlank() }

    /** 模式是否含 `{locale}` 占位符。不含则无法自动发现语言。 */
    val usesLocale: Boolean = pattern.contains(LOCALE_TOKEN)

    /** 模式是否含 `{namespace}` 占位符。 */
    val usesNamespace: Boolean = pattern.contains(NAMESPACE_TOKEN)

    /**
     * 枚举该模式下所有语言包文件。
     *
     * 逐层下钻目录树：片段含 `{locale}` 时，该层的**子目录名**即为候选语言；
     * 片段含 `{namespace}` 时，该层的**文件名**即为语言包；其余片段按字面量匹配。
     * 最后一段若不是 `{file}`，说明模式自带扩展名（如 `{locale}.json`），
     * 此时 `{locale}` 层拿到的是目录，需要再拼上文件名。
     */
    fun resolve(root: File): List<ResolvedLocale> {
        if (!root.isDirectory || segments.isEmpty()) return emptyList()

        var current: List<File> = listOf(root)
        // 每个语言包文件记录它归属的语言，填在遇到 {locale} 的那一层
        val out = ArrayList<ResolvedLocale>()
        var locale: String? = null

        for (part in segments) {
            when {
                part.contains(LOCALE_TOKEN) -> {
                    // 该层的子目录名即语言；若这一层同时是最后一段，则目录内还需再取文件
                    val dirs: List<File> = current.flatMap { it.listFiles { f: File -> f.isDirectory }?.toList().orEmpty() }
                    val isLast = part === segments.last()
                    if (isLast && !endsWithFileName(part)) {
                        // 形如 {locale} ——目录本身就是语言，需在目录内按 {file} 规则找文件
                        for (dir in dirs) {
                            val file = pickFileIn(dir, defaultFileName)
                            if (file != null) out += ResolvedLocale(dir.name, file)
                        }
                    } else {
                        // 形如 {locale}.json ——目录是语言，再拼出文件名
                        for (dir in dirs) {
                            out += ResolvedLocale(dir.name, File(dir, expandFileName(part, dir.name)))
                        }
                    }
                    return out.filter { it.file.isFile }.sortedBy { it.locale }
                }

                part == NAMESPACE_TOKEN || part == FILE_TOKEN -> {
                    // 该层的文件名即语言包，语言沿用外层记录的
                    current = current.flatMap { it.listFiles { f: File -> f.isFile }?.toList().orEmpty() }
                    val l = locale ?: return emptyList()
                    return current.mapNotNull { f ->
                        ResolvedLocale(l, f).takeIf { it.file.isFile }
                    }
                }

                else -> {
                    current = current.mapNotNull { File(it, part).takeIf { f -> f.exists() } }
                    if (current.isEmpty()) return emptyList()
                }
            }
        }
        return emptyList()
    }

    /** 该片段是否自带文件名（末尾是 `{file}` 或带扩展名）。 */
    private fun endsWithFileName(part: String): Boolean =
        part == FILE_TOKEN || part.substringAfterLast('/').contains('.')

    /** 片段含 `{locale}` 且自带扩展名时，展开成实际文件名。 */
    private fun expandFileName(part: String, locale: String): String =
        part.replace(LOCALE_TOKEN, locale)

    /** 在目录内按文件名取语言包，取不到则取第一个 json 文件。 */
    private fun pickFileIn(dir: File, fileName: String): File? {
        val exact = File(dir, fileName)
        if (exact.isFile) return exact
        return dir.listFiles { f: File ->
            f.isFile && f.name.endsWith(".json", ignoreCase = true)
        }?.firstOrNull()
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