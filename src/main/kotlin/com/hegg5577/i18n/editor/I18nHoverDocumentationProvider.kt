package com.hegg5577.i18n.editor

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.openapi.application.ApplicationManager
import com.hegg5577.i18n.locale.TranslationIndex
import com.hegg5577.i18n.settings.I18nSettings
import com.hegg5577.i18n.ui.Flags
import com.hegg5577.i18n.ui.KeyEditorDialog
import com.hegg5577.i18n.locale.LocaleRepository
import com.hegg5577.i18n.locale.KeyStyle
import java.io.File

/**
 * Hover 多语言预览。
 *
 * 当鼠标悬停在 `t('key')` 上时，显示所有语言的翻译。
 * 采用 HTML 格式，用 tab 切换不同语言，便于快速查看。
 */
class I18nHoverDocumentationProvider : DocumentationProvider {

    init {
        System.err.println("[I18nHoverDocumentationProvider] Initialized")
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        if (originalElement == null) return null
        return findTranslationHtml(originalElement)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (originalElement == null) return null
        return findTranslationHtml(originalElement)
    }

    override fun getDocumentationElementForLink(
        psiManager: com.intellij.psi.PsiManager?,
        link: String?,
        context: PsiElement?,
    ): PsiElement? {
        // 处理 i18n.edit?key=xxx 链接
        if (link == null || context == null || !link.startsWith("i18n.edit?key=")) {
            return null
        }

        try {
            val key = link.substringAfter("key=").urlDecode()
            val project = context.project
            val settings = I18nSettings.getInstance(project)
            val projectRoot = File(project.basePath ?: return null)

            ApplicationManager.getApplication().invokeLater {
                try {
                    // 使用 LocaleRepository 获取完整的数据
                    val repo = LocaleRepository(
                        projectRoot,
                        settings.localesPath,
                        settings.state.pathMatcher,
                        KeyStyle.NESTED,
                    )

                    val locales = repo.scanLocales()
                    val values = mutableMapOf<String, String?>()
                    val localeFiles = mutableMapOf<String, File>()
                    val allLocales = settings.state.displayLocales.ifEmpty { listOf(settings.sourceLocale) }

                    // 收集所有语言的值和文件
                    for (localeKeys in locales) {
                        val value = localeKeys.keys.find { it == key }
                        values[localeKeys.locale] = value
                        localeFiles[localeKeys.locale] = localeKeys.file
                    }

                    // 打开编辑对话框
                    val dialog = KeyEditorDialog(
                        project = project,
                        key = key,
                        sourceLocale = settings.sourceLocale,
                        values = values,
                        locales = allLocales,
                        localeFiles = localeFiles,
                        onSaved = { /* 刷新 UI */ }
                    )
                    dialog.show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun findTranslationHtml(element: PsiElement): String? {
        try {
            val file = element.containingFile ?: return null
            val project = element.project
            val settings = I18nSettings.getInstance(project)

            // 获取光标位置的文本（通常是字符串或标识符）
            val text = element.text
            val key = extractKeyFromText(text) ?: return null

            val projectRoot = project.basePath ?: return null

            // 获取展示的语言列表（优先使用 displayLocales，否则使用源语言）
            val displayLocalesList = if (settings.state.displayLocales.isNotEmpty()) {
                settings.state.displayLocales
            } else {
                listOf(settings.sourceLocale)
            }

            // 从语言包里查找这个 key 的所有翻译
            val allValues = mutableMapOf<String, String>()
            for (locale in displayLocalesList) {
                try {
                    val fallback = if (locale != settings.sourceLocale) settings.sourceLocale else ""
                    val values = TranslationIndex.values(
                        projectRoot = projectRoot,
                        locale = locale,
                        fallbackLocale = fallback,
                        localesDir = settings.localesPath,
                        pathMatcher = settings.state.pathMatcher,
                        keyStyle = com.hegg5577.i18n.locale.KeyStyle.NESTED,
                    )
                    allValues[locale] = values[key] ?: ""
                } catch (e: Exception) {
                    allValues[locale] = ""
                }
            }

            // 至少要有一个翻译才显示
            if (allValues.all { it.value.isEmpty() }) return null

            return buildHoverHtml(key, allValues)
        } catch (e: Exception) {
            return null
        }
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
        sb.append("<div style='margin-bottom: 8px;'><b>i18n Key:</b> <code>${escapeHtml(key)}</code></div>")

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
        val encodedKey = java.net.URLEncoder.encode(key, "UTF-8")
        sb.append("<div style='margin-top: 8px; font-size: 12px;'>")
        sb.append("<a href='i18n.edit?key=$encodedKey'>Edit this key</a>")
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

    private fun String.urlDecode(): String {
        return java.net.URLDecoder.decode(this, "UTF-8")
    }
}

