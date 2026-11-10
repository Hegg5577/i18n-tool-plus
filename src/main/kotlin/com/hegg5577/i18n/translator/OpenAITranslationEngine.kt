package com.hegg5577.i18n.translator

import com.google.gson.JsonObject
import com.intellij.openapi.progress.ProcessCanceledException

/** OpenAI 及兼容 Chat Completions 的中转服务，使用 JSON 结果保留多条译文边界。 */
class OpenAITranslationEngine(
    private val apiKey: String,
    private val model: String = "gpt-3.5-turbo",
    private val baseUrl: String = "https://api.openai.com/v1",
) : TranslationEngine {
    override val id = "openai"
    override val displayName = "OpenAI（兼容 API）"

    /** 沿用原有重试行为，模型和密钥缺失时直接提示配置错误。 */
    override fun translate(texts: List<String>, targetLocale: String, sourceLocale: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        if (apiKey.isBlank()) throw TranslationException("OpenAI API Key 未配置")
        if (model.isBlank()) throw TranslationException("OpenAI 模型未配置")
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return requestBatch(texts, targetLocale, sourceLocale)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) Thread.sleep(600L * (attempt + 1))
            }
        }
        throw TranslationException("OpenAI 翻译失败（共尝试 3 次）：${lastError?.message}", lastError)
    }

    /** 不强制 response_format 扩展，以兼容只实现基础聊天协议的第三方接口。 */
    private fun requestBatch(texts: List<String>, targetLocale: String, sourceLocale: String): List<String> {
        val body = JsonObject().apply {
            addProperty("model", model.trim())
            add("messages", LlmTranslation.messages(texts, sourceLocale, targetLocale))
        }
        val response = TranslationHttp.request(
            TranslationHttp.endpoint(baseUrl, "/chat/completions"), body.toString(),
            headers = mapOf("Authorization" to "Bearer ${apiKey.trim()}"),
        )
        val content = runCatching {
            response.asJsonObject.getAsJsonArray("choices")[0].asJsonObject
                .getAsJsonObject("message").get("content").asString
        }.getOrNull() ?: throw TranslationException("OpenAI 响应缺少 choices[0].message.content")
        return LlmTranslation.parse(content, texts.size)
    }
}
