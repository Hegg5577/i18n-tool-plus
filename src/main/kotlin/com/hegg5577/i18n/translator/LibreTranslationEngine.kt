package com.hegg5577.i18n.translator

import com.google.gson.JsonObject

/** 连接自托管 LibreTranslate，兼容启用密钥的实例。 */
class LibreTranslationEngine(
    private val apiUrl: String = "http://localhost:5000/translate",
    private val apiKey: String = "",
) : TranslationEngine {
    override val id = "libretranslate"
    override val displayName = "LibreTranslate（自托管）"

    /** 逐条请求，避免依赖不同自托管版本对数组输入的支持。 */
    override fun translate(texts: List<String>, targetLocale: String, sourceLocale: String): List<String> = texts.map { text ->
        val body = JsonObject().apply {
            addProperty("q", text)
            addProperty("source", sourceLocale.substringBefore('-').lowercase())
            addProperty("target", targetLocale.substringBefore('-').lowercase())
            addProperty("format", "text")
            if (apiKey.isNotBlank()) addProperty("api_key", apiKey.trim())
        }
        val response = TranslationHttp.request(apiUrl, body.toString())
        val translated = runCatching { response.asJsonObject.get("translatedText").asString }.getOrNull()
        translated?.takeIf { it.isNotBlank() || text.isBlank() }
            ?: throw TranslationException("LibreTranslate 未返回有效的 translatedText，请检查服务支持的语言")
    }
}
