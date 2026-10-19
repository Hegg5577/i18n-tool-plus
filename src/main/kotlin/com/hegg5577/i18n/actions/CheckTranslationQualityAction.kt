package com.hegg5577.i18n.actions

import com.hegg5577.i18n.translator.TranslationQualityChecker
import com.hegg5577.i18n.settings.I18nContext
import com.hegg5577.i18n.locale.LocaleRepository
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.DialogWrapper
import java.io.File
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.JScrollPane

/**
 * 检查翻译质量。
 *
 * 验证翻译中的参数一致性、占位符完整性等问题，
 * 生成详细的质量报告。
 */
class CheckTranslationQualityAction : AnAction("Check Translation Quality") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        try {
            val projectRoot = File(project.basePath ?: return)
            val context = I18nContext.getInstance(project)
            val config = context.config

            // 扫描所有翻译
            val repo = LocaleRepository(projectRoot, config.localesDir, config.pathMatcher, config.keyStyle)
            val locales = repo.scanLocales()

            // 收集所有源文本（假设第一个语言为源语言）
            val sourceLocale = locales.firstOrNull() ?: return
            val sources = sourceLocale.flatMap { it.keys }.associate { it to (it as? String) ?: "" }

            // 执行质量检查
            val allIssues = mutableListOf<TranslationQualityChecker.CheckResult>()
            for (locale in locales.drop(1)) {
                val langKey = locale.keys.firstOrNull() ?: continue
                val translations = locale.flatMap { it.keys }
                    .associate { it to (it as? String) ?: "" }

                for ((key, translation) in translations) {
                    val source = sources[key] ?: continue
                    val result = TranslationQualityChecker.check(
                        key = key,
                        language = langKey as? String ?: "unknown",
                        source = source,
                        translation = translation,
                    )

                    if (!result.isValid) {
                        allIssues.add(result)
                    }
                }
            }

            if (allIssues.isEmpty()) {
                Messages.showInfoMessage(project, "所有翻译质量检查通过！", "质量检查")
                return
            }

            // 生成报告
            val report = buildQualityReport(allIssues)

            // 显示详细报告
            val dialog = QualityReportDialog(project, report, allIssues.size)
            dialog.show()
        } catch (e: Exception) {
            Messages.showErrorDialog(project, e.message ?: "未知错误", "错误")
        }
    }

    private fun buildQualityReport(issues: List<TranslationQualityChecker.CheckResult>): String {
        return buildString {
            appendLine("=== 翻译质量检查报告 ===")
            appendLine("发现 ${issues.size} 个问题")
            appendLine()

            val byType = issues.groupBy { it.issues.firstOrNull()?.type }
            for ((type, typeIssues) in byType) {
                appendLine("## $type (${typeIssues.size})")

                for (issue in typeIssues.take(5)) {
                    appendLine("• ${issue.key} [${issue.language}]")
                    for (problem in issue.issues) {
                        appendLine("  - ${problem.message} (${problem.severity})")
                    }
                }

                if (typeIssues.size > 5) {
                    appendLine("... 还有 ${typeIssues.size - 5} 个")
                }

                appendLine()
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private class QualityReportDialog(
        project: Project,
        private val report: String,
        private val issueCount: Int,
    ) : DialogWrapper(project) {

        init {
            title = "翻译质量检查报告 - $issueCount 个问题"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val textArea = JTextArea(report)
            textArea.isEditable = false
            textArea.font = textArea.font.deriveFont(11f)
            textArea.lineWrap = true
            textArea.wrapStyleWord = true
            return JScrollPane(textArea)
        }
    }
}
