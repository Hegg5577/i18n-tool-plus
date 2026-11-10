package com.hegg5577.i18n.translator

import com.google.gson.JsonObject

/** 直接调用 Ollama 原生聊天接口，本地服务不要求 API Key。 */
class OllamaTranslationEngine(
    private val model: String,
    private val baseUrl: String = "http://localhost:11434",
) : TranslationEngine {
    override val id = "ollama"
    override val displayName = "Ollama（本地模型）"

    /** 关闭流式输出并要求 JSON，保留模型首次加载所需的等待时间。 */
    override fun translate(texts: List<String>, targetLocale: String, sourceLocale: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        if (model.isBlank()) throw TranslationException("请配置 Ollama 中已下载的模型名称")
        val body = JsonObject().apply {
            addProperty("model", model.trim())
            addProperty("stream", false)
            addProperty("format", "json")
            add("messages", LlmTranslation.messages(texts, sourceLocale, targetLocale))
        }
        val response = TranslationHttp.request(
            TranslationHttp.endpoint(baseUrl, "/api/chat"), body.toString(), readTimeout = 120_000,
        )
        val content = runCatching { response.asJsonObject.getAsJsonObject("message").get("content").asString }.getOrNull()
            ?: throw TranslationException("Ollama 响应缺少 message.content")
        return LlmTranslation.parse(content, texts.size)
    }
}
