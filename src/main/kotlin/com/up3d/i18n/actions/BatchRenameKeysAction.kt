package com.up3d.i18n.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.up3d.i18n.locale.LocaleRepository
import com.up3d.i18n.locale.TranslationSaver
import com.up3d.i18n.settings.I18nContext
import com.up3d.i18n.ui.BatchOperationDialog
import com.up3d.i18n.ui.TranslationTablePanel

/**
 * 批量重命名选中的 Key。
 *
 * 当前实现支持：
 * - 单个 Key 的重命名（会弹出输入框）
 * - 多个 Key 的统一前缀/后缀替换（高级）
 */
class BatchRenameKeysAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val tablePanel = findTablePanel(project) ?: run {
            notify(project, "未找到翻译表格", NotificationType.WARNING)
            return
        }

        val selectedKeys = tablePanel.getSelectedKeys()
        if (selectedKeys.isEmpty()) {
            notify(project, "请先选择要重命名的 Key", NotificationType.INFO)
            return
        }

        val context = I18nContext.of(project)
        val repository = context.repository() ?: run {
            notify(project, "未找到语言包", NotificationType.WARNING)
            return
        }

        // 单个 key 直接重命名
        if (selectedKeys.size == 1) {
            performSingleRename(project, selectedKeys[0], repository, context)
            return
        }

        // 多个 key 提供批量操作选项
        performBatchRename(project, selectedKeys, repository, context)
    }

    private fun performSingleRename(
        project: Project,
        oldKey: String,
        repository: LocaleRepository,
        context: I18nContext,
    ) {
        val newKey = showRenameDialog(project, oldKey) ?: return
        if (newKey == oldKey) return

        val result = TranslationSaver.renameKey(project, repository, oldKey, newKey)

        if (result.isSuccess) {
            notify(
                project,
                "成功重命名 Key，可按 Ctrl+Z 撤销",
                NotificationType.INFORMATION,
            )
            RefreshPanelAction().actionPerformed(AnActionEvent.createEvent(
                com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT_CONTEXT,
                emptyMap(),
                com.intellij.openapi.actionSystem.ActionPlaces.UNKNOWN,
                emptyMap(),
            ))
        } else {
            val message = buildString {
                appendLine("重命名失败。详情：")
                for ((locale, error) in result.failed) {
                    appendLine("$locale：$error")
                }
            }
            Messages.showErrorDialog(project, message, "重命名 Key 失败")
        }
    }

    private fun performBatchRename(
        project: Project,
        selectedKeys: List<String>,
        repository: LocaleRepository,
        context: I18nContext,
    ) {
        val choice = Messages.showDialog(
            project,
            "选择批量重命名的方式",
            "批量重命名",
            arrayOf("前缀替换", "后缀替换", "正则替换", "取消"),
            0,
            Messages.getQuestionIcon(),
        )

        when (choice) {
            0 -> performPrefixReplace(project, selectedKeys, repository)
            1 -> performSuffixReplace(project, selectedKeys, repository)
            2 -> performRegexReplace(project, selectedKeys, repository)
        }
    }

    private fun performPrefixReplace(
        project: Project,
        selectedKeys: List<String>,
        repository: LocaleRepository,
    ) {
        val validator = InputValidator { input ->
            input.isNotEmpty() && input.matches(Regex("""[\w.\-]*"""))
        }

        val oldPrefix = Messages.showInputDialog(
            project,
            "输入要替换的旧前缀：",
            "前缀替换",
            Messages.getQuestionIcon(),
            "",
            validator,
        ) ?: return

        val newPrefix = Messages.showInputDialog(
            project,
            "输入新前缀：",
            "前缀替换",
            Messages.getQuestionIcon(),
            "",
            validator,
        ) ?: return

        val keysToRename = selectedKeys.filter { it.startsWith(oldPrefix) }
        if (keysToRename.isEmpty()) {
            notify(project, "没有匹配的 Key", NotificationType.INFO)
            return
        }

        val mapping = keysToRename.associate { oldKey ->
            oldKey to oldKey.replaceFirst(oldPrefix, newPrefix)
        }

        executeBatchRename(project, repository, mapping)
    }

    private fun performSuffixReplace(
        project: Project,
        selectedKeys: List<String>,
        repository: LocaleRepository,
    ) {
        val validator = InputValidator { input ->
            input.isNotEmpty() && input.matches(Regex("""[\w.\-]*"""))
        }

        val oldSuffix = Messages.showInputDialog(
            project,
            "输入要替换的旧后缀：",
            "后缀替换",
            Messages.getQuestionIcon(),
            "",
            validator,
        ) ?: return

        val newSuffix = Messages.showInputDialog(
            project,
            "输入新后缀：",
            "后缀替换",
            Messages.getQuestionIcon(),
            "",
            validator,
        ) ?: return

        val keysToRename = selectedKeys.filter { it.endsWith(oldSuffix) }
        if (keysToRename.isEmpty()) {
            notify(project, "没有匹配的 Key", NotificationType.INFO)
            return
        }

        val mapping = keysToRename.associate { oldKey ->
            oldKey to oldKey.replaceFirst(Regex("${Regex.escape(oldSuffix)}$"), newSuffix)
        }

        executeBatchRename(project, repository, mapping)
    }

    private fun performRegexReplace(
        project: Project,
        selectedKeys: List<String>,
        repository: LocaleRepository,
    ) {
        val validator = InputValidator { input ->
            runCatching { Regex(input); true }.getOrDefault(false)
        }

        val pattern = Messages.showInputDialog(
            project,
            "输入正则表达式：",
            "正则替换",
            Messages.getQuestionIcon(),
            "",
            validator,
        ) ?: return

        val replacement = Messages.showInputDialog(
            project,
            "输入替换文本：",
            "正则替换",
            Messages.getQuestionIcon(),
            "",
        ) ?: return

        val regex = Regex(pattern)
        val mapping = selectedKeys.associate { oldKey ->
            oldKey to oldKey.replace(regex, replacement)
        }.filter { (old, new) -> old != new }

        if (mapping.isEmpty()) {
            notify(project, "没有匹配的 Key", NotificationType.INFO)
            return
        }

        executeBatchRename(project, repository, mapping)
    }

    private fun executeBatchRename(
        project: Project,
        repository: LocaleRepository,
        oldToNewMapping: Map<String, String>,
    ) {
        if (oldToNewMapping.isEmpty()) return

        val dialog = BatchOperationDialog(
            project,
            BatchOperationDialog.OperationType.RENAME,
            oldToNewMapping.keys.toList(),
            I18nContext.of(project).displayLocales(),
        )

        if (!dialog.showAndGet()) return

        // 逐个重命名
        var succeeded = 0
        var failed = 0

        for ((oldKey, newKey) in oldToNewMapping) {
            val result = TranslationSaver.renameKey(project, repository, oldKey, newKey)
            if (result.isSuccess) succeeded++ else failed++
        }

        val message = when {
            failed == 0 -> "成功重命名 $succeeded 个 Key，可按 Ctrl+Z 撤销"
            else -> "重命名结果：成功 $succeeded 个，失败 $failed 个"
        }

        notify(project, message, NotificationType.INFORMATION)
        RefreshPanelAction().actionPerformed(AnActionEvent.createEvent(
            com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT_CONTEXT,
            emptyMap(),
            com.intellij.openapi.actionSystem.ActionPlaces.UNKNOWN,
            emptyMap(),
        ))
    }

    private fun showRenameDialog(project: Project, oldKey: String): String? {
        val validator = InputValidator { input ->
            input.isNotEmpty() && input.matches(Regex("""[\w.\-]+"""))
        }

        return Messages.showInputDialog(
            project,
            "输入新 Key：",
            "重命名 Key",
            Messages.getQuestionIcon(),
            oldKey,
            validator,
        )
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val tablePanel = project?.let { findTablePanel(it) }
        val hasSelection = tablePanel?.getSelectedKeys()?.isNotEmpty() == true
        e.presentation.isEnabled = hasSelection
        e.presentation.text = when (val count = tablePanel?.getSelectedKeys()?.size) {
            null, 0 -> "重命名选中的 Key"
            1 -> "重命名 1 个 Key"
            else -> "批量重命名 $count 个 Key"
        }
    }

    private fun findTablePanel(project: Project): TranslationTablePanel? {
        val toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
            .getToolWindow("i18n-helper") ?: return null
        val content = toolWindow.contentManager.getContent(0) ?: return null
        return content.component as? TranslationTablePanel
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("i18n.helper.notifications")
            .createNotification(message, type)
            .notify(project)
    }
}
