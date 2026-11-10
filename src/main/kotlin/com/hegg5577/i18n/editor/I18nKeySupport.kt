package com.hegg5577.i18n.editor

import com.hegg5577.i18n.locale.DEFAULT_SOURCE_DIRS
import com.hegg5577.i18n.locale.KeyStyle
import com.hegg5577.i18n.locale.TranslationIndex
import com.hegg5577.i18n.settings.I18nContext
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonLiteral
import com.intellij.json.psi.JsonNullLiteral
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.io.File

/** 编辑器功能共用的 Key 识别与定义定位，避免跳转、查找引用和重命名各自猜测。 */
object I18nKeySupport {
    data class Usage(val key: String, val range: TextRange, val literal: PsiElement)
    data class Definition(val key: String, val value: String, val locale: String, val property: JsonProperty)

    private data class UsageCache(val stamp: Long, val patterns: List<String>, val keyPattern: String, val usages: List<Usage>)
    private data class DefinitionCache(val stamp: Long, val style: KeyStyle, val values: Map<String, JsonProperty>)
    private val usageCache = Key.create<UsageCache>("i18n.key.usages")
    private val definitionCache = Key.create<DefinitionCache>("i18n.key.definitions")
    private val skippedDirectories = setOf("node_modules", "dist", "build", "out", ".git", ".idea",
        "coverage", ".next", ".nuxt", ".output", ".cache")

    /** Key 只允许当前扫描器可识别的字符，避免重命名后产生失效调用。 */
    fun isValidKey(key: String, pattern: String = KeyUsageScanner.DEFAULT_KEY_PATTERN): Boolean =
        key.length <= 200 && Regex(pattern).matches(key)

    /** 缓存当前 PSI 文本的精确字符串范围；不把注释、普通字符串或第二个参数当成 Key。 */
    fun usages(file: PsiFile, context: I18nContext = I18nContext.of(file.project)): List<Usage> {
        if (!TranslationIndex.isSupported(file.name, context.supportedExtensions) ||
            KeyUsageScanner.isTooLarge(file.textLength)) return emptyList()
        val patterns = context.usageMatchRegex
        file.getUserData(usageCache)?.let {
            if (it.stamp == file.modificationStamp && it.patterns == patterns && it.keyPattern == context.keyPattern) return it.usages
        }
        val text = file.text
        val found = linkedMapOf<TextRange, Usage>()
        for (rule in KeyUsageScanner.compileRules(patterns, keyPattern = context.keyPattern)) {
            // 允许空 Key，才能在刚输入 t('') 时提供补全；其他入口会排除空引用。
            val completionPattern = context.keyPattern.replace(KeyUsageScanner.DEFAULT_KEY_PATTERN, """[\w.\-]*""")
            val regex = Regex(rule.regex.pattern.replace(context.keyPattern, completionPattern), rule.regex.options)
            for (match in regex.findAll(text)) {
                ProgressManager.checkCanceled()
                val group = KeyUsageScanner.keyGroup(match) ?: continue
                val key = group.value
                // 输入完命名空间分隔符时仍提供候选，但不能让任意字符进入引用范围。
                val namespacePrefix = context.namespaceEnabled && key.endsWith(context.namespaceDelimiter) &&
                    isValidKey(key.removeSuffix(context.namespaceDelimiter))
                if (key.isNotEmpty() && !isValidKey(key, context.keyPattern) && !namespacePrefix) continue
                val range = TextRange(group.range.first, group.range.first + key.length)
                val literal = literalAt(file, range, text) ?: continue
                found.putIfAbsent(range, Usage(key, range, literal))
            }
        }
        val result = found.values.sortedBy { it.range.startOffset }
        file.putUserData(usageCache, UsageCache(file.modificationStamp, patterns.toList(), context.keyPattern, result))
        return result
    }

    /** 选取完整字符串的最外层 PSI 节点，保证平台在字符串表达式上能取到引用。 */
    private fun literalAt(file: PsiFile, range: TextRange, fileText: String): PsiElement? {
        var element = file.findElementAt(range.startOffset) ?: return null
        var literal: PsiElement? = null
        while (element !is PsiFile) {
            if (element is PsiComment) return null
            if (element.textRange.startOffset + 1 == range.startOffset &&
                element.textRange.endOffset - 1 == range.endOffset) {
                val text = element.text
                if (text.length >= 2 && text.first() in "\"'`" && text.last() == text.first()) literal = element
            }
            element = element.parent ?: break
        }
        val found = literal ?: return null
        var next = found.textRange.endOffset
        while (next < fileText.length && fileText[next].isWhitespace()) next++
        if (next < fileText.length && fileText[next] in "+-*/?.[(`") return null
        return found
    }

    /** 重命名也复用字符串范围校验，避免改动动态拼接和包裹调用示例的字符串。 */
    fun isStaticReference(file: PsiFile, range: IntRange): Boolean {
        check(file.language.id != "TEXT") { "缺少源码语言解析支持：${file.name}" }
        if (literalAt(file, TextRange(range.first, range.last + 1), file.text) != null) return true
        var element = checkNotNull(file.findElementAt(range.first)) { "无法解析源码引用：${file.name}" }
        while (element !is PsiFile) {
            if (element is PsiComment) return false
            val text = element.text
            if (text.length >= 2 && text.first() in "\"'`" && text.last() == text.first()) return false
            element = element.parent ?: break
        }
        // 模板缺少完整的字符串 PSI 时不能静默跳过，否则删除旧 Key 会留下失效调用。
        error("无法安全定位源码字符串：${file.name}:${range.first}")
    }

    /** 光标可以位于引号或空字符串中，便于补全及标准编辑器快捷键定位。 */
    fun usageAt(file: PsiFile, offset: Int, allowEmpty: Boolean = false): Usage? =
        usages(file).firstOrNull {
            (allowEmpty || it.key.isNotEmpty()) && offset >= it.range.startOffset - 1 && offset <= it.range.endOffset
        }

    /** 使用平台 JSON PSI 读取最新编辑内容，保留可导航的属性节点。 */
    fun definitions(context: I18nContext, project: com.intellij.openapi.project.Project): List<Definition> {
        val repo = context.repository() ?: return emptyList()
        val manager = PsiManager.getInstance(project)
        val resolvedFiles = try {
            repo.resolvedFiles()
        } catch (_: IllegalStateException) {
            return emptyList()
        }
        return resolvedFiles.flatMap { resolved ->
            ProgressManager.checkCanceled()
            val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(resolved.file) ?: return@flatMap emptyList()
            val file = manager.findFile(virtualFile) as? JsonFile ?: return@flatMap emptyList()
            properties(file, context.keyStyle).map { (key, property) ->
                val value = property.value
                Definition(repo.logicalKey(resolved, key), (value as? JsonStringLiteral)?.value ?: value?.text.orEmpty(), resolved.locale, property)
            }
        }
    }

    /** 嵌套、平铺与 AUTO 的优先级与语言包读取保持一致，文件变化后缓存自动失效。 */
    private fun properties(file: JsonFile, style: KeyStyle): Map<String, JsonProperty> {
        file.getUserData(definitionCache)?.let {
            if (it.stamp == file.modificationStamp && it.style == style) return it.values
        }
        val result = linkedMapOf<String, JsonProperty>()
        val root = file.topLevelValue as? JsonObject ?: return emptyMap()
        /** 递归保留完整路径和属性节点，便于嵌套 Key 跳转到真实定义。 */
        fun visit(value: JsonObject, prefix: String) {
            for (property in value.propertyList) {
                ProgressManager.checkCanceled()
                val key = if (prefix.isEmpty()) property.name else "$prefix.${property.name}"
                val child = property.value
                if (child is JsonObject && style != KeyStyle.FLAT) visit(child, key)
                else if (child is JsonLiteral && child !is JsonNullLiteral) result[key] = property
            }
        }
        visit(root, "")
        if (style == KeyStyle.AUTO) {
            for (property in root.propertyList) {
                val value = property.value
                if (value is JsonLiteral && value !is JsonNullLiteral) result[property.name] = property
            }
        }
        file.putUserData(definitionCache, DefinitionCache(file.modificationStamp, style, result))
        return result
    }

    /** 优先跳到源语言定义；同一语言中存在同名 Key 时拒绝猜测文件归属。 */
    fun resolve(element: PsiElement, key: String): JsonProperty? {
        val context = I18nContext.of(element.project)
        val matches = definitions(context, element.project).filter { it.key == key }
        if (matches.groupBy { it.locale }.any { it.value.size > 1 }) return null
        return (matches.firstOrNull { it.locale == context.sourceLocale } ?: matches.firstOrNull())?.property
    }

    /** 只接受已配置语言包里的叶子属性名，不把译文内容或整个父节点当成 Key。 */
    fun definitionAt(element: PsiElement): Definition? {
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            if (current is JsonProperty) {
                if (element != current && !current.nameElement.textRange.contains(element.textRange)) return null
                val property = current
                return definitions(I18nContext.of(element.project), element.project)
                    .firstOrNull { it.property == property }
            }
            current = current.parent
        }
        return null
    }

    /** 源码引用与 JSON 定义都使用完整点号 Key，嵌套属性不会被截成最后一级。 */
    fun keyAt(element: PsiElement): String? {
        definitionAt(element)?.let { return it.key }
        val file = element.containingFile ?: return null
        return usageAt(file, element.textRange.startOffset)?.key
    }

    /** 统一遍历查找引用与重命名的源码范围，跳过依赖目录并响应取消。 */
    fun processSourceFiles(context: I18nContext, processor: (VirtualFile) -> Boolean): Boolean {
        val root = context.projectRoot ?: return true
        val visited = hashSetOf<String>()
        for (dir in context.sourceDirs.ifEmpty { DEFAULT_SOURCE_DIRS }) {
            val path = File(dir).let { if (it.isAbsolute) it else File(root, dir) }
            val virtualDir = LocalFileSystem.getInstance().findFileByIoFile(path)
            if (virtualDir == null) {
                check(!path.exists()) { "无法读取源码目录：$path" }
                continue
            }
            val completed = VfsUtilCore.iterateChildrenRecursively(virtualDir, { file ->
                !file.isDirectory || file.name.lowercase() !in skippedDirectories
            }) { file ->
                ProgressManager.checkCanceled()
                if (!file.isDirectory && visited.add(file.path) &&
                    TranslationIndex.isSupported(file.name, context.supportedExtensions)) processor(file) else true
            }
            if (!completed) return false
        }
        return true
    }
}
