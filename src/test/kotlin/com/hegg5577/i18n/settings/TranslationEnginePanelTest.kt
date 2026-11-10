package com.hegg5577.i18n.settings

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBPasswordField

/** 验证引擎设置迁移与切换，避免保存丢失凭据或改动其它配置。 */
class TranslationEnginePanelTest : BasePlatformTestCase() {

    /** 旧 Google 标识和 DeepL 免费版开关加载后，不应无故显示待应用修改。 */
    fun testLegacySettingsAreInitiallyUnmodified() {
        val state = I18nSettings.getInstance(project).state
        state.activeTranslationEngine = "google-free"
        state.deeplApiUrl = ""
        state.deeplIsFreeApi = false
        val panel = TranslationEnginePanel(project)
        panel.createPanel()
        assertFalse(panel.isModified())
    }

    /** 切换引擎不能清空未保存的密钥或自定义地址，应用后保留其它设置的新值。 */
    fun testSwitchKeepsUnsavedCredentialsAndOtherSettings() {
        val state = I18nSettings.getInstance(project).state
        val panel = TranslationEnginePanel(project)
        panel.createPanel()
        val engines = field(panel, "engineCombo") as ComboBox<*>
        val key = field(panel, "deeplApiKeyField") as JBPasswordField
        val urls = field(panel, "deeplUrlCombo") as ComboBox<*>
        engines.selectedIndex = 2
        key.text = "custom-key"
        urls.editor.item = "https://relay.example/v2/translate"
        engines.selectedIndex = 3
        engines.selectedIndex = 2
        assertEquals("custom-key", String(key.password))
        assertEquals("https://relay.example/v2/translate", urls.editor.item)
        assertTrue(panel.isModified())
        state.localesPath = "recently-updated-locales"
        panel.apply()
        assertEquals("recently-updated-locales", state.localesPath)
        assertEquals("deepl", state.activeTranslationEngine)
        assertEquals("custom-key", state.deeplApiKey)
        assertEquals("https://relay.example/v2/translate", state.deeplApiUrl)
        assertFalse(panel.isModified())
    }

    /** 仅在测试中读取已有控件，不为测试扩展生产代码的公开接口。 */
    private fun field(panel: TranslationEnginePanel, name: String): Any =
        panel.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(panel)
}
