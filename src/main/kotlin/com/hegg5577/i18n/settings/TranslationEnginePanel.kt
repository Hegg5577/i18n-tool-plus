package com.hegg5577.i18n.settings

import com.hegg5577.i18n.translator.TranslationEngineManager
import com.hegg5577.i18n.translator.TranslationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/** 只显示当前引擎的配置并顶部对齐，避免禁用字段和纵向拉伸造成大块留白。 */
class TranslationEnginePanel(private val project: Project) {
    private val state get() = I18nSettings.getInstance(project).state

    private data class EngineOption(val id: String, val label: String) {
        /** 下拉框显示名称，配置中仍保存稳定 ID。 */
        override fun toString(): String = label
    }

    private val engineCombo = ComboBox(DefaultComboBoxModel(arrayOf(
        EngineOption("google", "Google 翻译"),
        EngineOption("google-cn", "Google 翻译（国内）"),
        EngineOption("deepl", "DeepL"),
        EngineOption("baidu", "百度翻译"),
        EngineOption("libretranslate", "LibreTranslate（自托管）"),
        EngineOption("openai", "OpenAI（兼容 API）"),
        EngineOption("ollama", "Ollama（本地模型）"),
    )))
    private val googleApiKeyField = JBPasswordField().apply { columns = 32 }
    private val googleApiUrlField = JBTextField(32)
    private val deeplApiKeyField = JBPasswordField().apply { columns = 32 }
    private val deeplUrlCombo = ComboBox(DefaultComboBoxModel(arrayOf(DEEPL_FREE_URL, DEEPL_PRO_URL))).apply {
        isEditable = true
    }
    private val baiduAppIdField = JBTextField(32)
    private val baiduApiSecretField = JBPasswordField().apply { columns = 32 }
    private val baiduApiUrlField = JBTextField(32)
    private val libreApiUrlField = JBTextField(32)
    private val libreApiKeyField = JBPasswordField().apply { columns = 32 }
    private val openaiApiKeyField = JBPasswordField().apply { columns = 32 }
    private val openaiModelField = JBTextField(32)
    private val openaiBaseUrlField = JBTextField(32)
    private val ollamaBaseUrlField = JBTextField(32)
    private val ollamaModelField = JBTextField(32)
    private val hintLabel = JBLabel()
    private val configurationPanel = JPanel(BorderLayout())
    private val testConnectionButton = JButton("测试连接").apply { addActionListener { testConnection() } }

    private val googleForm = FormBuilder.createFormBuilder()
        .addLabeledComponent("API Key（可选）", googleApiKeyField)
        .addLabeledComponent("完整请求地址（可选）", googleApiUrlField).panel
    private val forms = mapOf(
        "google" to googleForm,
        "google-cn" to googleForm,
        "deepl" to FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key", deeplApiKeyField)
            .addLabeledComponent("完整请求地址（可选或自定义）", deeplUrlCombo).panel,
        "baidu" to FormBuilder.createFormBuilder()
            .addLabeledComponent("App ID", baiduAppIdField)
            .addLabeledComponent("密钥", baiduApiSecretField)
            .addLabeledComponent("完整请求地址", baiduApiUrlField).panel,
        "libretranslate" to FormBuilder.createFormBuilder()
            .addLabeledComponent("完整请求地址", libreApiUrlField)
            .addLabeledComponent("API Key（可选）", libreApiKeyField).panel,
        "openai" to FormBuilder.createFormBuilder()
            .addLabeledComponent("API Key", openaiApiKeyField)
            .addLabeledComponent("模型", openaiModelField)
            .addLabeledComponent("API 基础 URL", openaiBaseUrlField).panel,
        "ollama" to FormBuilder.createFormBuilder()
            .addLabeledComponent("服务基础 URL", ollamaBaseUrlField)
            .addLabeledComponent("已下载的模型名称", ollamaModelField).panel,
    )

    /** 用可滚动容器容纳紧凑表单，窗口变高时不拉伸字段间距。 */
    fun createPanel(): JComponent {
        engineCombo.addActionListener { updateFieldsVisibility() }
        val buttons = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)).apply { add(testConnectionButton) }
        val content = FormBuilder.createFormBuilder()
            .addLabeledComponent("翻译引擎", engineCombo)
            .addComponent(configurationPanel)
            .addComponent(hintLabel)
            .addComponent(buttons).panel.apply { border = JBUI.Borders.empty(12) }
        reset()
        val panel = JPanel(BorderLayout()).apply { add(content, BorderLayout.NORTH) }
        return JBScrollPane(panel).apply { border = JBUI.Borders.empty() }
    }

    /** 切换时移除旧表单的占位，但保留字段内容供切回后继续编辑。 */
    private fun updateFieldsVisibility() {
        val selected = (engineCombo.selectedItem as? EngineOption)?.id ?: "google"
        configurationPanel.removeAll()
        forms[selected]?.let { configurationPanel.add(it, BorderLayout.NORTH) }
        hintLabel.text = "<html>" + when (selected) {
            "google" -> "留空密钥使用免费端点；填写密钥使用 Google Cloud API。<br/>自定义地址须匹配所选协议，Google 国际与国内选项共用这两个配置。"
            "google-cn" -> "国内选项使用 translate.google.cn，端点可用性取决于服务和网络。<br/>填写密钥后使用 Google Cloud API；地址留空时自动选择。"
            "deepl" -> "可选择官方免费版、专业版地址，或直接输入兼容 DeepL v2 的完整请求地址。<br/>自定义地址应包含翻译路径，例如 /v2/translate。"
            "baidu" -> "使用百度通用翻译开放平台的 App ID 和密钥；自定义地址须兼容其签名协议。"
            "libretranslate" -> "默认请求 http://localhost:5000/translate。<br/>自托管实例通常无需密钥，启用鉴权的实例需填写。"
            "openai" -> "支持官方或兼容 Chat Completions 的服务。<br/>基础 URL 通常包含 /v1，也可填写完整的 /chat/completions 地址。"
            "ollama" -> "默认服务为 http://localhost:11434，无需密钥。<br/>请先启动 Ollama，并填写本机已下载的完整模型名称。"
            else -> ""
        } + "</html>"
        configurationPanel.revalidate()
        configurationPanel.repaint()
    }

    /** 测试当前未保存的字段，并将网络请求放到后台以避免设置窗口卡住。 */
    private fun testConnection() {
        val snapshot = editedState()
        val engine = TranslationEngineManager.createEngine(snapshot.activeTranslationEngine, snapshot) ?: return
        testConnectionButton.isEnabled = false
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "测试 ${engine.displayName} 连接", true) {
            /** 仅使用固定测试文本，不发送用户项目中的文案。 */
            override fun run(indicator: ProgressIndicator) {
                indicator.checkCanceled()
                val result = engine.translate(listOf("hello"), "zh", "en")
                if (result.singleOrNull().isNullOrBlank()) throw TranslationException("引擎没有返回单条有效译文")
            }

            /** 后台任务完成后由平台切回界面线程显示结果。 */
            override fun onSuccess() {
                testConnectionButton.isEnabled = true
                if (!project.isDisposed) Messages.showInfoMessage(project, "${engine.displayName} 连接成功", "连接测试")
            }

            /** 失败时保留引擎给出的原因，方便区分地址、鉴权和响应格式问题。 */
            override fun onThrowable(error: Throwable) {
                testConnectionButton.isEnabled = true
                if (!project.isDisposed) Messages.showErrorDialog(project, "连接失败：${error.message}", "连接测试失败")
            }

            /** 取消后恢复按钮，允许用户修改配置后重新测试。 */
            override fun onCancel() {
                testConnectionButton.isEnabled = true
            }
        })
    }

    /** 将旧 ID 和免费版开关转换为界面值，避免打开设置就被误判为已修改。 */
    private fun normalizedState(): I18nSettings.State = state.copy(
        activeTranslationEngine = when (state.activeTranslationEngine) {
            "", "google-free" -> "google"
            else -> state.activeTranslationEngine
        },
        deeplApiUrl = state.deeplApiUrl.ifBlank { if (state.deeplIsFreeApi) DEEPL_FREE_URL else DEEPL_PRO_URL },
    )

    /** 只修改翻译相关字段，保留其余设置及未选中引擎的凭据。 */
    private fun editedState(): I18nSettings.State {
        val deeplUrl = (deeplUrlCombo.editor.item as? String).orEmpty().trim()
            .ifEmpty { if (state.deeplIsFreeApi) DEEPL_FREE_URL else DEEPL_PRO_URL }
        return state.copy(
            activeTranslationEngine = (engineCombo.selectedItem as? EngineOption)?.id ?: "google",
            googleApiKey = String(googleApiKeyField.password).trim(),
            googleApiUrl = googleApiUrlField.text.trim(),
            deeplApiKey = String(deeplApiKeyField.password).trim(),
            deeplApiUrl = deeplUrl,
            deeplIsFreeApi = when (deeplUrl) {
                DEEPL_FREE_URL -> true
                DEEPL_PRO_URL -> false
                else -> state.deeplIsFreeApi
            },
            baiduAppId = baiduAppIdField.text.trim(),
            baiduApiSecret = String(baiduApiSecretField.password).trim(),
            baiduApiUrl = baiduApiUrlField.text.trim(),
            libreTranslateApiUrl = libreApiUrlField.text.trim(),
            libreTranslateApiKey = String(libreApiKeyField.password).trim(),
            openaiApiKey = String(openaiApiKeyField.password).trim(),
            openaiModel = openaiModelField.text.trim().ifEmpty { "gpt-3.5-turbo" },
            openaiBaseUrl = openaiBaseUrlField.text.trim().ifEmpty { "https://api.openai.com/v1" },
            ollamaBaseUrl = ollamaBaseUrlField.text.trim(),
            ollamaModel = ollamaModelField.text.trim(),
        )
    }

    /** 载入所有引擎的配置，切换选项时不重新加载以免丢失未保存输入。 */
    fun reset() {
        val current = normalizedState()
        engineCombo.selectedItem = (0 until engineCombo.itemCount).map { engineCombo.getItemAt(it) }
            .firstOrNull { it.id == current.activeTranslationEngine } ?: engineCombo.getItemAt(0)
        googleApiKeyField.text = current.googleApiKey
        googleApiUrlField.text = current.googleApiUrl
        deeplApiKeyField.text = current.deeplApiKey
        deeplUrlCombo.selectedItem = current.deeplApiUrl
        baiduAppIdField.text = current.baiduAppId
        baiduApiSecretField.text = current.baiduApiSecret
        baiduApiUrlField.text = current.baiduApiUrl
        libreApiUrlField.text = current.libreTranslateApiUrl
        libreApiKeyField.text = current.libreTranslateApiKey
        openaiApiKeyField.text = current.openaiApiKey
        openaiModelField.text = current.openaiModel
        openaiBaseUrlField.text = current.openaiBaseUrl
        ollamaBaseUrlField.text = current.ollamaBaseUrl
        ollamaModelField.text = current.ollamaModel
        updateFieldsVisibility()
    }

    /** 通过现有状态加载入口写回完整快照，保留其他设置页刚刚更新的字段。 */
    fun apply() {
        I18nSettings.getInstance(project).loadState(editedState())
    }

    /** 统一使用同一快照比较，避免新增字段遗漏修改检测。 */
    fun isModified(): Boolean = editedState() != normalizedState()

    private companion object {
        const val DEEPL_FREE_URL = "https://api-free.deepl.com/v2/translate"
        const val DEEPL_PRO_URL = "https://api.deepl.com/v2/translate"
    }
}
