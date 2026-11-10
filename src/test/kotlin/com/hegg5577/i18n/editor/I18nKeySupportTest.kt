package com.hegg5577.i18n.editor

import com.hegg5577.i18n.settings.I18nSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** 识别 Key 时必须限定到真实静态字符串，并遵守项目自定义规则。 */
class I18nKeySupportTest : BasePlatformTestCase() {
    /** 每个用例从独立配置开始，避免自定义规则影响其他场景。 */
    override fun setUp() {
        super.setUp()
        I18nSettings.getInstance(project).loadState(I18nSettings.State())
    }

    /** 注释、示例字符串、普通字符串和第二参数中相同的文本都不能产生引用。 */
    fun testOnlyStaticTranslationArgumentsAreReferences() {
        val file = myFixture.configureByText("calls.js", """
            // t('common.fake')
            const example = "t('common.sample')";
            const ordinary = 'common.plain';
            t('common.ok', { label: 'common.option' });
            i18n.t("common.cancel");
            ${'$'}t('common.vue');
            translate('common.translate');
            t(`common.template`);
            t('common.dynamic' + suffix);
            t(`common.${'$'}{suffix}`);
        """.trimIndent())

        val usages = I18nKeySupport.usages(file)
        assertEquals(listOf("common.ok", "common.cancel", "common.vue", "common.translate", "common.template"),
            usages.map { it.key })
        for (usage in usages) {
            assertEquals(usage.key, file.text.substring(usage.range.startOffset, usage.range.endOffset))
            assertEquals(usage.literal.textRange.startOffset + 1, usage.range.startOffset)
        }
    }

    /** 空 Key 可触发补全，但不会被当成可跳转或可重命名的引用。 */
    fun testEmptyKeyIsOnlyAcceptedForCompletion() {
        val file = myFixture.configureByText("empty.js", "t('<caret>');")
        val offset = myFixture.caretOffset
        assertNull(I18nKeySupport.usageAt(file, offset))
        assertEquals("", I18nKeySupport.usageAt(file, offset, allowEmpty = true)?.key)
    }

    /** 自定义调用规则优先，内置 t 调用不应混入查找结果。 */
    fun testCustomPatternOverridesBuiltinCalls() {
        I18nSettings.getInstance(project).state.usageMatchRegex = mutableListOf("""lookup\('({key})'""")
        val file = myFixture.configureByText("custom.ts", "lookup('custom.key'); t('ignored.key');")
        assertEquals(listOf("custom.key"), I18nKeySupport.usages(file).map { it.key })
    }

    /** 配置发生变化后不能继续使用旧的引用范围缓存。 */
    fun testPatternChangeInvalidatesCachedUsages() {
        val file = myFixture.configureByText("cache.js", "t('builtin.key'); lookup('custom.key');")
        assertEquals(listOf("builtin.key"), I18nKeySupport.usages(file).map { it.key })
        I18nSettings.getInstance(project).state.usageMatchRegex = mutableListOf("""lookup\('({key})'""")
        assertEquals(listOf("custom.key"), I18nKeySupport.usages(file).map { it.key })
    }

    /** 无效自定义规则会安全回退，普通捕获组也不能因缺少命名组而抛异常。 */
    fun testInvalidPatternSafelyFallsBack() {
        I18nSettings.getInstance(project).state.usageMatchRegex = mutableListOf("[")
        val file = myFixture.configureByText("fallback.js", "t('common.ok');")
        assertEquals(listOf("common.ok"), I18nKeySupport.usages(file).map { it.key })
        val match = requireNotNull(Regex("""t\('([\w.]+)'""").find(file.text))
        assertEquals("common.ok", KeyUsageScanner.keyGroup(match)?.value)
    }

    /** 新名称必须能被已有扫描器完整识别，不能接受超过其长度上限的 Key。 */
    fun testRenameKeyValidationMatchesScannerLimit() {
        assertTrue(I18nKeySupport.isValidKey("common.confirm-action"))
        assertFalse(I18nKeySupport.isValidKey("a".repeat(201)))
        assertFalse(I18nKeySupport.isValidKey("common:key"))
    }
    /** 自动弹出候选只对实际 Key 生效，不影响普通字符串输入。 */
    fun testAutoPopupIsLimitedToTranslationStrings() {
        val file = myFixture.configureByText("popup.js", "t('common.ok'); const plain = 'ordinary';")
        val contributor = I18nCompletionContributor()
        assertTrue(contributor.invokeAutoPopup(I18nKeySupport.usages(file).single().literal, '.'))
        val ordinary = requireNotNull(file.findElementAt(file.text.indexOf("ordinary")))
        assertFalse(contributor.invokeAutoPopup(ordinary, 'o'))
    }

}
