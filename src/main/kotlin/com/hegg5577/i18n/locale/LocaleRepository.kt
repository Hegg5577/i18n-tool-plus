package com.hegg5577.i18n.locale

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
    val keyStyle: KeyStyle = KeyStyle.NESTED,
    private val fileName: String = DEFAULT_FILE_NAME,
    private val localesDirs: List<String> = emptyList(),
    val namespaceEnabled: Boolean = false,
    val namespaceDelimiter: String = ":",
) {
    companion object {
        const val DEFAULT_LOCALES_DIR = "src/i18n/locales"
        const val DEFAULT_FILE_NAME = "index.json"
    }

    private val matcher = PathMatcher(pathMatcher, fileName)

    /** 缓存身份仅含配置，文件增删放入指纹，避免不断积累旧缓存条目。 */
    internal val configurationKey: String
        get() = listOf(localesDir, localesDirs, pathMatcher, keyStyle, fileName,
            namespaceEnabled, namespaceDelimiter).toString()

    /** 保留文件归属并拒绝有歧义的命名空间前缀，避免重命名写错文件。 */
    fun resolvedFiles(): List<ResolvedLocale> {
        check(!namespaceEnabled || (matcher.usesNamespace && namespaceDelimiter.isNotBlank() &&
            namespaceDelimiter.none { it.isWhitespace() || it in "\"'`\\" })) {
            "启用命名空间需要配置 {namespace} 和有效的分隔符"
        }
        val files = localesDirs.ifEmpty { listOf(localesDir) }.map { path ->
            File(path).let { if (it.isAbsolute) it else File(projectRoot, path) }.canonicalFile
        }.distinct().flatMap { root -> matcher.resolve(root).map { it.copy(root = root) } }
            .distinctBy { it.file.canonicalPath }
        if (namespaceEnabled) {
            val namespaces = files.mapNotNull { it.namespace }.distinct()
            check(namespaces.none { name -> namespaces.any { it != name && it.startsWith(name + namespaceDelimiter) } }) {
                "命名空间与分隔符存在前缀歧义，请调整分隔符或命名空间：${namespaces.joinToString()}"
            }
        }
        return files
    }

    /** 逻辑 Key 只在编辑器和界面使用，JSON 内始终保存原始字段名。 */
    fun logicalKey(resolved: ResolvedLocale, key: String): String =
        if (namespaceEnabled) "${checkNotNull(resolved.namespace)}$namespaceDelimiter$key" else key

    /** 仅对当前文件的命名空间解码，防止同名字段跨文件修改。 */
    fun rawKey(resolved: ResolvedLocale, key: String): String? {
        if (!namespaceEnabled) return key
        val prefix = "${resolved.namespace}$namespaceDelimiter"
        return key.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.takeIf { it.isNotEmpty() }
    }

    /** 将最新文档或磁盘字段统一转成逻辑 Key。 */
    fun logicalValues(resolved: ResolvedLocale, values: Map<String, String>): Map<String, String> =
        values.mapKeys { (key, _) -> logicalKey(resolved, key) }

    /** 相同语言的重复逻辑 Key 必须明确报错，避免按文件顺序覆盖。 */
    fun validateUnique(locale: String, values: Map<File, Map<String, String>>) {
        val duplicates = values.flatMap { (file, entries) -> entries.keys.map { it to file } }
            .groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
        check(duplicates.isEmpty()) {
            "语言「$locale」存在重复 Key：" + duplicates.entries.joinToString("；") { (key, files) ->
                "$key（${files.joinToString { it.path }}）"
            }
        }
    }

    /** 返回指定语言的全部文件，顺序与路径解析结果一致。 */
    fun filesFor(locale: String): List<File> =
        resolvedFiles().filter { it.locale == locale }.map { it.file }

    /** 按语言合并所有文件的 key，file 仅保留首个文件以兼容旧调用。 */
    fun scanLocales(): List<LocaleKeys> =
        resolvedFiles().groupBy { it.locale }.map { (locale, files) ->
            LocaleKeys(locale, files.first().file, valuesOf(locale).keys)
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

    /** 按命名空间合并并校验归属，冲突包含所有相关文件路径。 */
    fun valuesOf(locale: String): Map<String, String> {
        val values = resolvedFiles().filter { it.locale == locale }.associate { resolved ->
            resolved.file to logicalValues(resolved, valuesOfFile(resolved.file))
        }
        validateUnique(locale, values)
        return linkedMapOf<String, String>().apply { values.values.forEach { putAll(it) } }
    }

    /** 兼容旧的单文件调用；多文件写入应使用带 key 的重载。 */
    fun localeFile(locale: String): File? = filesFor(locale).firstOrNull()

    /**
     * 优先定位目标语言中 key 的唯一所属文件；新增 key 按源语言的命名空间定位。
     * 单文件布局仍允许新增 key，多文件布局缺少归属或存在歧义时拒绝猜测写入位置。
     */
    fun localeFile(
        locale: String,
        key: String,
        sourceLocale: String,
        currentValues: Map<File, Map<String, String>>? = null,
    ): File? {
        val files = resolvedFiles()
        /** 保存时使用未落盘文档，查询时沿用磁盘读取。 */
        fun entries(file: ResolvedLocale): Map<String, String> =
            currentValues?.get(file.file) ?: logicalValues(file, valuesOfFile(file.file))
        val targets = files.filter { it.locale == locale && rawKey(it, key) != null }
        val owners = targets.filter { key in entries(it) }
        check(owners.size <= 1) {
            "语言「$locale」的 key「$key」存在于多个文件：${owners.joinToString { it.file.path }}"
        }
        val sources = files.filter { it.locale == sourceLocale }
        val sourceOwners = sources.filter { key in entries(it) }
        check(sourceOwners.size <= 1) { "源语言「$sourceLocale」的 key「$key」所属文件不唯一" }
        val source = sourceOwners.singleOrNull()
        // 多目录下目标已有译文也必须属于源文件所在根目录。
        owners.singleOrNull()?.let { owner ->
            check(source == null || owner.root == source.root) { "key「$key」的源语言与目标语言分属不同目录" }
            return owner.file
        }
        if (!matcher.usesNamespace && files.map { it.root }.distinct().size <= 1 &&
            targets.size <= 1 && sources.size <= 1) return targets.singleOrNull()?.file
        if (source != null) {
            val matches = targets.filter { it.root == source.root && it.namespace == source.namespace }
            check(matches.size == 1) {
                "无法定位语言「$locale」的 key「$key」：namespace「${source.namespace}」对应文件不存在或不唯一（${source.root}）"
            }
            return matches.single().file
        }
        // 提取全新 Key 时只能由明确的命名空间和唯一根目录确定文件。
        if (namespaceEnabled && sources.count { rawKey(it, key) != null } == 1) {
            val origin = sources.single { rawKey(it, key) != null }
            return targets.singleOrNull { it.root == origin.root && it.namespace == origin.namespace }?.file
                ?: error("目标语言「$locale」缺少对应命名空间文件：$key")
        }
        error("无法定位语言「$locale」的 key「$key」：源语言「$sourceLocale」中该 key 的所属文件不存在或不唯一")
    }

    /** 兼容旧 API，每个语言仅返回首个文件；完整遍历请使用 resolvedFiles。 */
    fun localeFiles(): Map<String, File?> =
        resolvedFiles().groupBy { it.locale }.mapValues { (_, files) -> files.first().file }

    /** AUTO 最后覆盖顶层完整 key，避免嵌套展开结果受 JSON 属性顺序影响。 */
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
        if (keyStyle == KeyStyle.AUTO && prefix.isEmpty()) {
            for ((name, value) in obj.entrySet()) {
                if (value.isJsonPrimitive) result[name] = value.asString
            }
        }
        return result
    }

    /** 读取单个文件，供保存调用方判断 key 的实际归属。 */
    fun valuesOfFile(file: File): Map<String, String> = runCatching {
        flattenValues(JsonParser.parseString(file.readText()).asJsonObject)
    }.getOrDefault(emptyMap())
}
