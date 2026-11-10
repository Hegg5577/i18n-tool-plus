package com.hegg5577.i18n.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.hegg5577.i18n.locale.LocaleRepository
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 字符串提取对话框。
 *
 * 用户可以：
 * 1. 输入新的 key 名称
 * 2. 为每个语言输入翻译（源语言预填入原文）
 * 3. 或选择已有的 key（替换其值）
 */
class ExtractStringDialog(
    private val project: com.intellij.openapi.project.Project,
    private val sourceString: String,
    private val repo: LocaleRepository,
    private val sourceLocale: String,
) : DialogWrapper(project, true) {

    private val keyNameField = JBTextField(30).apply {
        val namespaces = repo.resolvedFiles().filter { it.locale == sourceLocale }.mapNotNull { it.namespace }.distinct()
        text = if (repo.namespaceEnabled && namespaces.size == 1)
            "${namespaces.single()}${repo.namespaceDelimiter}${suggestKeyName(sourceString)}" else suggestKeyName(sourceString)
    }
    private val locales = repo.scanLocales().map { it.locale }.sorted()
    private val translationFields = mutableMapOf<String, JBTextField>()

    var selectedKey: String? = null
    var translations: Map<String, String>? = null

    init {
        title = "提取字符串为 i18n Key"
        setOKButtonText("保存")
        setCancelButtonText("取消")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout())
        root.border = JBUI.Borders.empty(10)

        // Key 名称输入
        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Key 名称"), keyNameField, 1, false)
            .addSeparator()
            .addComponent(JBLabel("各语言翻译"))

        // 为每种语言添加输入框
        for (locale in locales) {
            val field = JBTextField(if (locale == sourceLocale) sourceString else "", 40)
            translationFields[locale] = field
            form.addLabeledComponent(
                JBLabel(Flags.displayName(locale)),
                field,
                1,
                false,
            )
        }

        val formPanel = form.panel
        root.add(JBScrollPane(formPanel), BorderLayout.CENTER)
        root.preferredSize = Dimension(JBUI.scale(500), JBUI.scale(400))
        return root
    }

    /** 使用项目的分隔符规则，允许明确指定新 Key 的命名空间。 */
    override fun doValidate(): ValidationInfo? {
        val key = keyNameField.text.trim()
        if (key.isEmpty()) {
            return ValidationInfo("Key 名称不能为空", keyNameField)
        }
        if (!com.hegg5577.i18n.editor.I18nKeySupport.isValidKey(key, com.hegg5577.i18n.settings.I18nContext.of(project).keyPattern)) {
            return ValidationInfo("Key 名称含有不支持的字符，请使用已配置的命名空间分隔符", keyNameField)
        }

        try {
            if (repo.localeFile(sourceLocale, key, sourceLocale) == null) {
                return ValidationInfo("无法定位源语言文件，请明确命名空间和语言包目录", keyNameField)
            }
        } catch (e: IllegalStateException) {
            return ValidationInfo(e.message ?: "语言包归属不明确", keyNameField)
        }
        val sourceValue = translationFields[sourceLocale]?.text?.trim()
        if (sourceValue.isNullOrEmpty()) {
            return ValidationInfo("源语言（${Flags.displayName(sourceLocale)}）不能为空", translationFields[sourceLocale])
        }

        return null
    }

    override fun doOKAction() {
        selectedKey = keyNameField.text.trim()
        translations = mutableMapOf<String, String>().apply {
            for (locale in locales) {
                val value = translationFields[locale]?.text?.trim() ?: ""
                if (value.isNotEmpty()) {
                    put(locale, value)
                }
            }
        }
        super.doOKAction()
    }

    companion object {
        private fun suggestKeyName(text: String): String {
            // 简单的建议：取前几个单词作为 key
            return text.trim()
                .split(Regex("[^\\w]+"))
                .filter { it.isNotEmpty() }
                .take(3)
                .joinToString(".")
                .lowercase()
                .ifEmpty { "new_key" }
        }
    }
}
