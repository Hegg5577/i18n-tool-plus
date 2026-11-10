package com.hegg5577.i18n.translator

import com.hegg5577.i18n.settings.I18nSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** 验证引擎管理器的所有读取入口都使用当前配置。 */
class TranslationEngineManagerTest : BasePlatformTestCase() {

    /** 切换引擎后应立即生效，未知或空配置继续回退到默认引擎。 */
    fun testActiveEngineFollowsConfiguration() {
        val state = I18nSettings.getInstance(project).state
        val manager = TranslationEngineManager.getInstance(project)
        state.activeTranslationEngine = "deepl"
        assertEquals("deepl", manager.getActiveEngine().id)

        state.activeTranslationEngine = "openai"
        assertEquals("openai", manager.getActiveEngine().id)

        state.activeTranslationEngine = "unknown"
        assertEquals("google-free", manager.getActiveEngine().id)

        state.activeTranslationEngine = ""
        assertEquals("google-free", manager.getActiveEngine().id)
    }

    /** 初次读取后修改 DeepL 密钥与端点类型，按 ID 读取也必须得到最新配置。 */
    fun testDeepLConfigurationIsNotCached() {
        val state = I18nSettings.getInstance(project).state
        val manager = TranslationEngineManager.getInstance(project)
        state.deeplApiKey = "old-key"
        state.deeplIsFreeApi = true
        val before = manager.getEngine("deepl")!!

        state.deeplApiKey = "new-key"
        state.deeplIsFreeApi = false
        val after = manager.getEngine("deepl")!!

        assertNotSame(before, after)
        assertEquals("new-key", configurationValue(after, "apiKey"))
        assertEquals(false, configurationValue(after, "isFreeApi"))
    }

    /** 修改 OpenAI 密钥、模型和地址后，活跃引擎与引擎列表均应更新。 */
    fun testOpenAIConfigurationIsNotCached() {
        val state = I18nSettings.getInstance(project).state
        val manager = TranslationEngineManager.getInstance(project)
        state.activeTranslationEngine = "openai"
        manager.listEngines()
        manager.getActiveEngine()

        state.openaiApiKey = "new-key"
        state.openaiModel = "configured-model"
        state.openaiBaseUrl = "http://localhost:12345/v1"

        for (engine in listOf(manager.getActiveEngine(), manager.listEngines().first { it.id == "openai" })) {
            assertEquals("new-key", configurationValue(engine, "apiKey"))
            assertEquals("configured-model", configurationValue(engine, "model"))
            assertEquals("http://localhost:12345/v1", configurationValue(engine, "baseUrl"))
        }
    }

    /** 新引擎必须可选，旧 google-free 与新 google 配置都保持可用。 */
    fun testAllSupportedEnginesAreRegistered() {
        val manager = TranslationEngineManager.getInstance(project)
        assertEquals(7, manager.listEngines().size)
        for (id in listOf("google", "google-free", "google-cn", "deepl", "baidu", "libretranslate", "openai", "ollama")) {
            assertNotNull(id, manager.getEngine(id))
        }
        assertNull(manager.getEngine("editor-llm"))
    }

    /** DeepL 自定义地址修改后，测试与正式翻译工厂都必须使用新值。 */
    fun testDeepLCustomEndpointIsNotCached() {
        val state = I18nSettings.getInstance(project).state
        val manager = TranslationEngineManager.getInstance(project)
        state.deeplApiUrl = "https://old.example/v2/translate"
        manager.getEngine("deepl")
        state.deeplApiUrl = "https://new.example/v2/translate"
        assertEquals(state.deeplApiUrl, configurationValue(manager.getEngine("deepl")!!, "apiUrl"))
        assertEquals(state.deeplApiUrl, configurationValue(TranslationEngineManager.createEngine("deepl", state)!!, "apiUrl"))
    }
    /** 直接检查构造参数，避免回归测试发起真实网络请求或暴露配置读取接口。 */
    private fun configurationValue(engine: TranslationEngine, name: String): Any? =
        engine.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(engine)
}
