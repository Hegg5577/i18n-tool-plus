package com.up3d.i18n.locale

import java.io.File

class PathMatcher(
    val pattern: String,
    private val defaultFileName: String = LocaleRepository.DEFAULT_FILE_NAME,
) {
    private val segments: List<String> =
        pattern.trim().split('/').map { it.trim() }.filter { it.isNotEmpty() }

    val usesLocale: Boolean get() = pattern.contains(LOCALE_TOKEN)
    val usesNamespace: Boolean get() = pattern.contains(NAMESPACE_TOKEN)

    fun resolve(root: File): List<ResolvedLocale> {
        if (!root.isDirectory || segments.isEmpty()) return emptyList()
        var level: List<Level> = listOf(Level(null, root))

        for (segment in segments) {
            val next = when {
                segment.startsWith(LOCALE_TOKEN) && hasExtension(segment) -> {
                    val ext = segment.substringAfterLast('.', "")
                    level.flatMap { (_, dir) ->
                        dataFiles(dir).mapNotNull { file ->
                            if (ext.isNotEmpty() && !file.extension.equals(ext, ignoreCase = true)) {
                                return@mapNotNull null
                            }
                            if (isAggregateFile(file.name)) return@mapNotNull null
                            Level(localeFromFileName(file), file)
                        }
                    }
                }
                segment.startsWith(NAMESPACE_TOKEN) && hasExtension(segment) -> {
                    level.flatMap { (locale, dir) ->
                        val suffix = segment.removePrefix(NAMESPACE_TOKEN).removePrefix(".")
                        dataFiles(dir)
                            .filter { suffix.isEmpty() || it.name.endsWith(suffix, ignoreCase = true) }
                            .map { Level(locale, it) }
                    }
                }
                segment == LOCALE_TOKEN -> level.flatMap { (_, dir) ->
                    childDirs(dir).map { Level(it.name, it) }
                }
                segment == NAMESPACE_TOKEN -> level.flatMap { (locale, dir) ->
                    dataFiles(dir).map { Level(locale, it) }
                }
                segment == FILE_TOKEN -> level.flatMap { (locale, dir) ->
                    val exact = File(dir, defaultFileName)
                    val file = if (exact.isFile) exact else dataFiles(dir).firstOrNull()
                    if (file == null) emptyList() else listOf(Level(locale, file))
                }
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

    private data class Level(val locale: String?, val dir: File)

    private fun hasExtension(segment: String): Boolean =
        segment.substringAfterLast('/').substringAfterLast('.', "").isNotEmpty()

    private fun childDirs(dir: File): List<File> =
        dir.listFiles { f: File -> f.isDirectory }?.toList().orEmpty()

    private fun dataFiles(dir: File): List<File> =
        dir.listFiles { f: File ->
            f.isFile && f.extension.lowercase() in DATA_EXTENSIONS
        }?.toList().orEmpty()

    private fun localeFromFileName(file: File): String {
        val base = file.name.substringBeforeLast('.', file.name)
        return base.substringAfterLast('.', base).ifBlank { base }
    }

    companion object {
        val DATA_EXTENSIONS = setOf("json")

        private val AGGREGATE_NAMES = setOf("index", "index.json", "i18n", "dictionaries")

        private fun isAggregateFile(fileName: String): Boolean =
            fileName.substringBeforeLast('.') in AGGREGATE_NAMES

        const val DEFAULT_PATTERN = "{locale}/{file}"
        const val LOCALE_TOKEN = "{locale}"
        const val NAMESPACE_TOKEN = "{namespace}"
        const val FILE_TOKEN = "{file}"
    }
}

data class ResolvedLocale(val locale: String, val file: File)
