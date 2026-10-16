package com.up3d.i18n.actions

import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBScrollPane
import com.up3d.i18n.editor.I18nInlayCollector
import com.up3d.i18n.editor.I18nInlayProviderFactory
import com.up3d.i18n.editor.KeyUsageScanner
import com.up3d.i18n.locale.TranslationIndex
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JComponent
import javax.swing.JTextArea

/**
 * 诊断当前文件的行内译文提示链路。
 *
 * 存在的意义：提示没显示时，问题可能出在任意一环（文件类型、语言 ID、扩展点注册、
 * 语言包路径、key 不存在、Inlay Hints 未勾选）。与其猜，不如一次性把状态全列出来。
 *
 * 判定逻辑全部复用 [KeyUsageScanner] 与 [TranslationIndex]，与真正生效的收集器同源，
 * 保证"诊断说会显示"和"实际会显示"是同一套判断。
 */
class DiagnoseCurrentFileAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        if (project == null) {
            Messages.showWarningDialog("没有打开的项目。", DIALOG_TITLE)
            return
        }

        val psiFile = event.getData(CommonDataKeys.PSI_FILE)
        if (psiFile == null) {
            Messages.showWarningDialog("请先把光标放到编辑器里的某个文件中再执行诊断。", DIALOG_TITLE)
            return
        }

        // PSI 读取必须在读锁内进行；computeBlocking 是 compute 的非废弃替代
        val file = ReadAction.computeBlocking<PsiFile, RuntimeException> { psiFile }
        val text = ReadAction.computeBlocking<String, RuntimeException> { file.text }
        val report = diagnose(project, file, text)

        DiagnosticDialog(project, file.name, report).show()
    }

    private fun diagnose(project: Project, file: PsiFile, text: String): String {
        val projectRoot = project.basePath
        val languageId = file.language.id
        val values = TranslationIndex.values(projectRoot)

        val hits = ArrayList<KeyUsageScanner.Usage>()
        val resolved = ArrayList<Pair<KeyUsageScanner.Usage, String>>()
        val missed = LinkedHashSet<String>()
        for (usage in KeyUsageScanner.scan(text)) {
            hits += usage
            val translated = values[usage.key]
            if (translated == null) missed += usage.key else resolved += usage to translated
        }

        val tooLarge = KeyUsageScanner.isTooLarge(text.length)
        val extSupported = TranslationIndex.isSupported(file.name)
        val langSupported = I18nInlayProviderFactory.supports(languageId)
        val providerVisible = providerRegistered(file)

        val willShow = extSupported && langSupported && providerVisible && !tooLarge

        return buildString {
            appendSection("当前文件") {
                line("文件", file.name)
                line("后缀", file.name.substringAfterLast('.', "（无）"))
                line("语言 ID", languageId)
                line("大小", "${fmt(text.length)} 字符")
            }

            appendSection("加载判定") {
                check(extSupported, "后缀在支持列表内", "后缀不受支持，该文件不会被扫描")
                check(langSupported, "语言 ID 已绑定 Provider", "语言 ID 未绑定，需补进 LANGUAGE_IDS")
                check(
                    providerVisible,
                    "扩展点已生效：${I18nInlayCollector.PROVIDER_ID}",
                    "扩展点未生效，检查 plugin.xml 的 declarativeInlayProviderFactory",
                )
                check(!tooLarge, "文件大小在上限 ${fmt(KeyUsageScanner.MAX_FILE_LENGTH)} 内", "文件过大，已跳过")
            }

            appendSection("项目与语言包") {
                line("项目根", projectRoot ?: "无法获取")
                if (projectRoot == null) return@appendSection

                for (locale in listOf(TranslationIndex.DEFAULT_LOCALE, TranslationIndex.FALLBACK_LOCALE)) {
                    val count = TranslationIndex.countFor(projectRoot, locale)
                    check(count > 0, "$locale 可用（${fmt(count)} 个 key）", "$locale 语言包未找到或解析失败")
                }
                line("可用于提示的 key", "${fmt(values.size)} 个")
            }

            appendSection("扫描结果") {
                line("t('...') 命中", "${fmt(hits.size)} 处")
                line("能解析出译文", "${fmt(resolved.size)} 处")
                line("未解析", "${fmt(missed.size)} 处")
            }

            if (missed.isNotEmpty()) {
                appendSection("未解析的 key（语言包里查不到）") {
                    missed.take(20).forEach { line("", it) }
                    if (missed.size > 20) line("", "… 其余 ${missed.size - 20} 个省略")
                }
            }

            if (resolved.isNotEmpty()) {
                appendSection("将显示的提示（最多预览 10 条）") {
                    resolved.take(10).forEach { (usage, translated) -> line("", "${usage.raw} → $translated") }
                }
            }

            appendSection("结论") {
                if (willShow && resolved.isNotEmpty()) {
                    appendLine("  [OK] 该文件预计显示 ${resolved.size} 条译文提示。")
                    appendLine()
                    appendLine("  若编辑器里仍看不到，检查：")
                    appendLine("    Settings → Editor → Inlay Hints → $languageId")
                    appendLine("    确认「${I18nInlayProviderFactory.PROVIDER_NAME}」处于勾选状态。")
                } else {
                    appendLine("  [!!] 该文件当前不会显示译文提示，请对照上面标 [!!] 的项处理。")
                }
            }
        }
    }

    /**
     * 通过平台的聚合入口查询，确认我们的 Provider 真的被扩展点注册进去了。
     * 这一步能区分「收集器代码有问题」和「plugin.xml 没生效」。
     */
    private fun providerRegistered(file: PsiFile): Boolean {
        val infos = runCatching {
            InlayHintsProviderFactory.getProvidersForLanguage(file.language)
        }.getOrNull().orEmpty()
        return infos.any { it.providerId == I18nInlayCollector.PROVIDER_ID }
    }

    private fun fmt(n: Int): String = String.format("%,d", n)

    // region 报告排版小工具

    private fun StringBuilder.appendSection(title: String, body: StringBuilder.() -> Unit) {
        appendLine()
        appendLine("── $title ──")
        body()
    }

    /**
     * 刻意不做列对齐：中文标签在等宽字体下占两格，padEnd 按字符数补空格反而会错位。
     */
    private fun StringBuilder.line(label: String, value: String) {
        if (label.isEmpty()) {
            appendLine("  $value")
        } else {
            appendLine("  $label：$value")
        }
    }

    private fun StringBuilder.check(ok: Boolean, pass: String, fail: String) =
        line(if (ok) "[OK]" else "[!!]", if (ok) pass else fail)

    // endregion

    private companion object {
        const val DIALOG_TITLE = "i18n 诊断"
    }
}

/** 可滚动的报告弹窗，纯文本便于复制排查。 */
private class DiagnosticDialog(
    project: Project?,
    fileName: String,
    private val report: String,
) : DialogWrapper(project, false) {

    init {
        title = "i18n 诊断 · $fileName"
        setOKButtonText("复制报告")
        setCancelButtonText("关闭")
        init()
    }

    override fun doOKAction() {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(report), null)
        }
        super.doOKAction()
    }

    override fun createCenterPanel(): JComponent {
        val area = JTextArea(report).apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            caretPosition = 0
        }
        return JBScrollPane(area).apply { preferredSize = Dimension(760, 540) }
    }
}
