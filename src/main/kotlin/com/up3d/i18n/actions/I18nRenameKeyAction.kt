package com.up3d.i18n.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction
import com.up3d.i18n.settings.I18nContext

/**
 * i18n Key 重命名 Action。
 *
 * 通过 Refactor → Rename 或 Shift+F6 触发，支持重命名代码中和语言包中的 key。
 */
class I18nRenameKeyAction : BaseRefactoringAction() {

    override fun isAvailableInEditorOnly(): Boolean = false

    override fun isEnabledOnElements(elements: Array<PsiElement>): Boolean {
        return elements.size == 1 && isI18nKeyString(elements[0])
    }

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler {
        return I18nRenameKeyHandler()
    }

    private fun isI18nKeyString(element: PsiElement): Boolean {
        val text = element.text
        return (text.startsWith("\"") && text.endsWith("\"")) ||
               (text.startsWith("'") && text.endsWith("'"))
    }
}

/**
 * i18n Key 重命名的重构处理程序。
 *
 * 暂时实现基础框架，完整的多文件重构需要后续扩展。
 */
class I18nRenameKeyHandler : RefactoringActionHandler {

    override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext?) {
        if (elements.isEmpty()) return

        val element = elements[0]
        val oldKey = extractKey(element) ?: return

        // 弹出重命名对话框
        val newKey = showRenameDialog(project, oldKey) ?: return
        if (newKey == oldKey) return

        // 执行重命名
        performRename(project, oldKey, newKey)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        if (editor == null || file == null) return

        val element = file.findElementAt(editor.caretModel.offset) ?: return
        invoke(project, arrayOf(element), dataContext)
    }

    private fun extractKey(element: PsiElement): String? {
        val text = element.text
        return when {
            text.startsWith("\"") && text.endsWith("\"") && text.length > 2 ->
                text.substring(1, text.length - 1)
            text.startsWith("'") && text.endsWith("'") && text.length > 2 ->
                text.substring(1, text.length - 1)
            else -> null
        }
    }

    private fun showRenameDialog(project: Project, oldKey: String): String? {
        val validator = object : com.intellij.openapi.ui.InputValidator {
            override fun checkInput(input: String): Boolean {
                return input.isNotEmpty() && input.matches(Regex("""[\w.\-]+"""))
            }

            override fun canClose(input: String): Boolean {
                return checkInput(input)
            }
        }

        return Messages.showInputDialog(
            project,
            "Enter new key name:",
            "Rename i18n Key",
            Messages.getQuestionIcon(),
            oldKey,
            validator,
        )
    }

    private fun performRename(project: Project, oldKey: String, newKey: String) {
        val context = I18nContext.of(project)
        val repo = context.repository() ?: return

        try {
            // 收集所有语言中 oldKey 的值
            val allLocales = repo.scanLocales().map { it.locale }
            val translations = mutableMapOf<String, String>()

            for (locale in allLocales) {
                val values = repo.valuesOf(locale)
                values[oldKey]?.let { translations[locale] = it }
            }

            if (translations.isEmpty()) {
                Messages.showWarningDialog(project, "Key '$oldKey' not found in any locale", "Rename Failed")
                return
            }

            // 删除旧 key，添加新 key（通过替换）
            val changes = mutableMapOf<String, Map<String, String>>()
            for ((locale, value) in translations) {
                changes[locale] = mapOf(newKey to value)
            }

            val result = com.up3d.i18n.locale.TranslationSaver.save(project, repo, context.sourceLocale, changes)
            if (!result.isSuccess) {
                val detail = result.failed.entries.joinToString("；") { "${it.key}: ${it.value}" }
                Messages.showErrorDialog(project, "Failed to save:\n$detail", "Rename Failed")
            } else {
                Messages.showInfoMessage(project, "✓ Renamed '$oldKey' to '$newKey'", "Rename Complete")

                // 刷新 UI
                project.getService(com.up3d.i18n.ui.I18nPanelService::class.java).refresh()
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Rename failed: ${e.message}", "Rename Error")
        }
    }
}

