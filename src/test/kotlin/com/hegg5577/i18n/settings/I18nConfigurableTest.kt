package com.hegg5577.i18n.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBTextField

/** 验证设置页的修改检测、保存及 YAML 优先级。 */
class I18nConfigurableTest : BasePlatformTestCase() {

    /** 单独修改源码目录也必须触发应用，保存后状态与界面保持一致。 */
    fun testSourceDirsChangeCanBeApplied() {
        val configurable = I18nConfigurable(project)
        configurable.createComponent()
        try {
            assertFalse(configurable.isModified())
            val field = configurable.javaClass.getDeclaredField("sourceDirsField").apply { isAccessible = true }
                .get(configurable) as JBTextField
            field.text = "src， packages/web"
            assertTrue(configurable.isModified())
            configurable.apply()
            assertEquals(listOf("src", "packages/web"), I18nSettings.getInstance(project).state.sourceDirs)
            assertFalse(configurable.isModified())
        } finally {
            configurable.disposeUIResources()
        }
    }

    /** 点击应用也应遵守 YAML 优先级，界面回显的值必须与实际配置一致。 */
    fun testApplyRetainsYamlOverrides() {
        val settings = I18nSettings.getInstance(project)
        val yamlField = settings.javaClass.getDeclaredField("yamlConfig").apply { isAccessible = true }
        val configurable = I18nConfigurable(project)
        configurable.createComponent()
        try {
            yamlField.set(settings, mapOf("overrideExisting" to true, "sourceDirs" to listOf("app")))
            configurable.apply()
            assertTrue(settings.state.overrideExisting)
            assertEquals(listOf("app"), settings.state.sourceDirs)
            assertFalse(configurable.isModified())
        } finally {
            yamlField.set(settings, emptyMap<String, Any>())
            configurable.disposeUIResources()
        }
    }

    /** 多目录只输入一次即可保存，旧单目录保留作为清空后的回退值。 */
    fun testLocalePathsCanBeAppliedAndDeduplicated() {
        val configurable = I18nConfigurable(project)
        configurable.createComponent()
        try {
            val area = configurable.javaClass.getDeclaredField("localesPathsArea").apply { isAccessible = true }
                .get(configurable) as com.intellij.ui.components.JBTextArea
            area.text = "packages/a/locales\npackages/b/locales\npackages/a/locales"
            assertTrue(configurable.isModified())
            configurable.apply()
            assertEquals(listOf("packages/a/locales", "packages/b/locales"),
                I18nContext.of(project).localesPaths)
            assertFalse(configurable.isModified())
        } finally {
            configurable.disposeUIResources()
        }
    }

    /** 旧 YAML 单目录仍高于 XML 多目录，新 YAML 多目录高于旧目录。 */
    fun testYamlLocalePathsPriority() {
        val settings = I18nSettings.getInstance(project)
        val yamlField = settings.javaClass.getDeclaredField("yamlConfig").apply { isAccessible = true }
        try {
            settings.state.localesPaths = mutableListOf("old/a", "old/b")
            yamlField.set(settings, mapOf("localesPath" to "legacy"))
            settings.applyYamlOverrides()
            assertTrue(settings.state.localesPaths.isEmpty())
            assertEquals("legacy", I18nContext.of(project).localesPath)
            yamlField.set(settings, mapOf("localesPath" to "legacy", "localesPaths" to listOf("new/a", "new/b")))
            settings.applyYamlOverrides()
            assertEquals(listOf("new/a", "new/b"), I18nContext.of(project).localesPaths)
        } finally {
            yamlField.set(settings, emptyMap<String, Any>())
        }
    }

}
