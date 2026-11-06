package com.hegg5577.i18n.editor

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.documentation.ExternalDocumentationHandler
import com.intellij.psi.PsiElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.Messages
import com.hegg5577.i18n.locale.TranslationIndex
import com.hegg5577.i18n.settings.I18nContext
import com.hegg5577.i18n.ui.Flags
import com.hegg5577.i18n.ui.I18nPanelService
import com.hegg5577.i18n.ui.openKeyEditor

/**
 * Hover 多语言预览。
 *
 * 当按 Ctrl+Q 查看文档时，显示所有语言的翻译。
 * 采用 HTML 格式，用点击链接打开编辑对话框。
 */
class I18nHoverDocumentationProvider : DocumentationProvider, ExternalDocumentationHandler {

    /** 原始元素缺失时仍允许从文档目标读取 key。 */
    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        return findTranslationHtml(originalElement ?: element ?: return null)
    }

    /** 悬停与快捷文档使用同一份项目配置。 */
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        return findTranslationHtml(originalElement ?: element ?: return null)
    }

    /** 只在点击时打开编辑器，并消费链接，避免多个 Provider 重复排队打开弹窗。 */
    override fun handleExternalLink(
        psiManager: com.intellij.psi.PsiManager?,
        link: String?,
        context: PsiElement?,
    ): Boolean {
        if (link == null || !link.startsWith("i18n.edit?key=")) {
            return false
        }
        val project = context?.project ?: psiManager?.project ?: return true
        val key = try {
            link.substringAfter("key=").urlDecode()
        } catch (_: IllegalArgumentException) {
            return true
        }
        if (key.isBlank() || project.isDisposed) return true

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            try {
                openKeyEditor(project, key, project.getService(I18nPanelService::class.java))
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                Logger.getInstance(I18nHoverDocumentationProvider::class.java).warn("打开翻译编辑器失败", e)
                Messages.showErrorDialog(project, "无法打开翻译编辑器：${e.message}", "i18n 编辑失败")
            }
        }
        return true
    }

    /** 遵循项目设置读取译文，避免悬停与行内提示使用不同的 key 风格。 */
    private fun findTranslationHtml(element: PsiElement): String? {
        try {
            val project = element.project
            val context = I18nContext.of(project)

            // 获取光标位置的文本（通常是字符串或标识符）
            val text = element.text
            val key = extractKeyFromText(text) ?: return null

            val projectRoot = project.basePath ?: return null

            // 获取展示的语言列表（优先使用 displayLocales，否则使用源语言）
            val displayLocalesList = context.displayLocales.ifEmpty { listOf(context.sourceLocale) }

            // 从语言包里查找这个 key 的所有翻译
            val allValues = mutableMapOf<String, String>()
            for (locale in displayLocalesList) {
                try {
                    val fallback = if (context.fallbackToSource && locale != context.sourceLocale) context.sourceLocale else ""
                    val values = TranslationIndex.values(
                        projectRoot = projectRoot,
                        locale = locale,
                        fallbackLocale = fallback,
                        localesDir = context.localesPath,
                        pathMatcher = context.pathMatcher,
                        keyStyle = context.keyStyle,
                    )
                    allValues[locale] = values[key] ?: ""
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) {
                    allValues[locale] = ""
                }
            }

            // 至少要有一个翻译才显示
            if (allValues.all { it.value.isEmpty() }) return null

            return buildHoverHtml(key, allValues)
        } catch (e: ProcessCanceledException) {
            throw e
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

    /** 编辑链接走点击处理入口，避免文档目标解析阶段触发弹窗。 */
    internal fun buildHoverHtml(key: String, translations: Map<String, String>): String {
        val sb = StringBuilder()
        sb.append("<html><body style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", sans-serif;'>")

        // 标题
        sb.append("<div style='margin-bottom: 6px;'><b>i18n Key:</b> <code>${escapeHtml(key)}</code></div>")

        // 翻译表格：不硬编码背景色 / 边框色，随主题自适应。
        // 早期用 border='1' + 表头 #f0f0f0，深色主题下是刺眼的白条 + 粗黑框。
        sb.append("<table style='border-collapse: collapse; width: 100%;'>")
        sb.append("<tr>")
        sb.append("<th style='text-align: left; padding: 3px 10px 3px 0; font-weight: bold;'>Language</th>")
        sb.append("<th style='text-align: left; padding: 3px 10px; font-weight: bold;'>Translation</th>")
        sb.append("</tr>")

        for ((locale, value) in translations) {
            val displayLocale = Flags.displayName(locale)
            val displayValue = if (value.isEmpty()) "<i style='opacity: 0.55;'>(empty)</i>" else escapeHtml(value)
            sb.append("<tr style='border-top: 1px solid rgba(128, 128, 128, 0.25);'>")
            sb.append("<td style='padding: 4px 10px 4px 0; white-space: nowrap;'>$displayLocale</td>")
            sb.append("<td style='padding: 4px 10px;'>$displayValue</td>")
            sb.append("</tr>")
        }

        sb.append("</table>")

        // 链接：打开 Key 编辑对话框
        val encodedKey = java.net.URLEncoder.encode(key, "UTF-8")
        sb.append("<div style='margin-top: 10px;'>")
        sb.append("<a href=\"i18n.edit?key=$encodedKey\">✎ Edit this key</a>")
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
