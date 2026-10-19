package com.hegg5577.i18n.editor

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.hegg5577.i18n.locale.TranslationIndex
import com.hegg5577.i18n.settings.I18nContext
import com.hegg5577.i18n.ui.Flags

/**
 * Hover 多语言预览。
 *
 * 当鼠标悬停在 `t('key')` 上时，显示所有语言的翻译。
 * 采用 HTML 格式，用 tab 切换不同语言，便于快速查看。
 */
class I18nHoverDocumentationProvider : DocumentationProvider {

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        if (originalElement == null) return null
        return findTranslationHtml(originalElement)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (originalElement == null) return null
        return findTranslationHtml(originalElement)
    }

    private fun findTranslationHtml(element: PsiElement): String? {
        val file = element.containingFile ?: return null
        val project = element.project
        val context = I18nContext.of(project)

        if (!context.isValid) return null
        if (!TranslationIndex.isSupported(file.name, context.supportedExtensions)) return null

        // 获取光标位置的文本（通常是字符串或标识符）
        val text = element.text
        val key = extractKeyFromText(text) ?: return null

        // 从语言包里查找这个 key 的所有翻译
        val allValues = mutableMapOf<String, String>()
        for (locale in context.displayLocales.ifEmpty { listOf(context.sourceLocale) }) {
            val fallback = if (locale != context.sourceLocale) context.sourceLocale else ""
            val values = TranslationIndex.values(
                projectRoot = context.projectRoot?.path ?: "",
                locale = locale,
                fallbackLocale = fallback,
                localesDir = context.localesPath,
                pathMatcher = context.pathMatcher,
                keyStyle = context.keyStyle,
            )
            allValues[locale] = values[key] ?: ""
        }

        // 至少要有一个翻译才显示
        if (allValues.all { it.value.isEmpty() }) return null

        return buildHoverHtml(key, allValues)
    }

    private fun extractKeyFromText(text: String): String? {
        // 从 "key" 或 'key' 中提取 key
        return when {
            text.startsWith("\"") && text.endsWith("\"") && text.length > 2 ->
                text.substring(1, text.length - 1)
            text.startsWith("'") && text.endsWith("'") && text.length > 2 ->
                text.substring(1, text.length - 1)
            text.startsWith("`") && text.endsWith("`") && text.length > 2 ->
                text.substring(1, text.length - 1)
            // 也可能是标识符本身，不带引号
            text.matches(Regex("""[\w.\-]+""")) -> text
            else -> null
        }
    }

    private fun buildHoverHtml(key: String, translations: Map<String, String>): String {
        val sb = StringBuilder()
        sb.append("<html><body style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", sans-serif;'>")

        // 标题
        sb.append("<div style='margin-bottom: 8px;'><b>i18n Key:</b> <code>$key</code></div>")

        // 翻译表格
        sb.append("<table border='1' cellpadding='8' cellspacing='0' style='border-collapse: collapse;'>")
        sb.append("<tr style='background-color: #f0f0f0;'>")
        sb.append("<th style='text-align: left; padding: 4px 8px;'>Language</th>")
        sb.append("<th style='text-align: left; padding: 4px 8px;'>Translation</th>")
        sb.append("</tr>")

        for ((locale, value) in translations) {
            val displayLocale = Flags.displayName(locale)
            val displayValue = if (value.isEmpty()) "<i style='color: #999;'>(empty)</i>" else escapeHtml(value)
            sb.append("<tr>")
            sb.append("<td style='padding: 4px 8px;'><b>$displayLocale</b></td>")
            sb.append("<td style='padding: 4px 8px;'>$displayValue</td>")
            sb.append("</tr>")
        }

        sb.append("</table>")

        // 链接：打开 Key 编辑对话框
        sb.append("<div style='margin-top: 8px; font-size: 12px;'>")
        sb.append("<a href='action:i18n.edit?key=$key'>Edit this key</a>")
        sb.append("</div>")

        sb.append("</body></html>")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}

