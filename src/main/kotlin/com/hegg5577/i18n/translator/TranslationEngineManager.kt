package com.hegg5577.i18n.translator

import com.hegg5577.i18n.settings.I18nSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/** 根据最新配置创建翻译引擎，测试连接与实际翻译共用同一工厂。 */
@Service(Service.Level.PROJECT)
class TranslationEngineManager(private val project: Project) {

    /** 不缓存凭据，保证修改设置后立即使用新配置。 */
    fun listEngines(): List<TranslationEngine> = listOf(
        "google", "google-cn", "deepl", "baidu", "libretranslate", "openai", "ollama",
    ).mapNotNull { getEngine(it) }

    /** 保留旧 google-free 配置及未知 ID 返回空值的行为。 */
    fun getEngine(id: String): TranslationEngine? = createEngine(id, I18nSettings.getInstance(project).state)

    /** 未知引擎继续回退到 Google，兼容已有项目配置。 */
    fun getActiveEngine(): TranslationEngine =
        getEngine(I18nSettings.getInstance(project).state.activeTranslationEngine) ?: GoogleTranslationEngine()

    /** 保持原有公共翻译入口，单条编辑与批量操作都使用当前引擎。 */
    fun translate(texts: List<String>, targetLocale: String, sourceLocale: String = "en"): List<String> =
        getActiveEngine().translate(texts, targetLocale, sourceLocale)

    companion object {
        /** 根据配置快照创建引擎，允许测试尚未点击“应用”的设置。 */
        internal fun createEngine(id: String, state: I18nSettings.State): TranslationEngine? = when (id) {
            "google", "google-free", "google-cn" -> GoogleTranslationEngine(
                state.googleApiKey, state.googleApiUrl, id == "google-cn",
            )
            "deepl" -> DeepLTranslationEngine(state.deeplApiKey, state.deeplIsFreeApi, state.deeplApiUrl)
            "baidu" -> BaiduTranslationEngine(state.baiduAppId, state.baiduApiSecret, state.baiduApiUrl)
            "libretranslate" -> LibreTranslationEngine(state.libreTranslateApiUrl, state.libreTranslateApiKey)
            "openai" -> OpenAITranslationEngine(state.openaiApiKey, state.openaiModel, state.openaiBaseUrl)
            "ollama" -> OllamaTranslationEngine(state.ollamaModel, state.ollamaBaseUrl)
            else -> null
        }

        /** 获取项目引擎管理器。 */
        fun getInstance(project: Project): TranslationEngineManager = project.getService(TranslationEngineManager::class.java)
    }
}
