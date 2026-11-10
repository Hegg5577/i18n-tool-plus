package com.hegg5577.i18n.translator

import com.google.gson.JsonObject
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.text.StringUtil

/** Google 无密钥端点与官方 Cloud API 共用语言映射，保留旧 google-free 引擎 ID。 */
class GoogleTranslationEngine(
    private val apiKey: String = "",
    private val apiUrl: String = "",
    private val domestic: Boolean = false,
) : TranslationEngine {
    override val id: String = if (domestic) "google-cn" else "google-free"
    override val displayName: String = if (domestic) "Google 翻译（国内）" else "Google 翻译"

    /** 每条文本独立翻译，避免免费端点丢失多条输入。 */
    override fun translate(texts: List<String>, targetLocale: String, sourceLocale: String): List<String> =
        texts.map { translateOne(it, targetLocale, sourceLocale) }

    /** 保留原有重试入口，取消任务时不继续发起请求。 */
    fun translateOne(text: String, targetLocale: String, sourceLocale: String): String {
        if (text.isBlank()) return text
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return request(text, googleCode(sourceLocale), googleCode(targetLocale))
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) Thread.sleep(600L * (attempt + 1))
            }
        }
        throw TranslationException("Google 翻译失败（共尝试 3 次）：${lastError?.message}", lastError)
    }

    /** 配置密钥时使用 Cloud v2，否则使用免费端点；自定义地址始终优先。 */
    private fun request(text: String, source: String, target: String): String {
        val endpoint = apiUrl.trim().ifBlank {
            when {
                apiKey.isNotBlank() -> "https://translation.googleapis.com/language/translate/v2"
                domestic -> "https://translate.google.cn/translate_a/single"
                else -> "https://translate.googleapis.com/translate_a/single"
            }
        }
        if (apiKey.isNotBlank()) {
            val body = JsonObject().apply {
                addProperty("q", text)
                addProperty("target", cloudCode(target))
                if (source != "auto") addProperty("source", cloudCode(source))
                addProperty("format", "text")
            }
            val response = TranslationHttp.request(
                TranslationHttp.query(endpoint, mapOf("key" to apiKey.trim())), body.toString(),
            )
            val result = runCatching {
                response.asJsonObject.getAsJsonObject("data").getAsJsonArray("translations")[0]
                    .asJsonObject.get("translatedText").asString
            }.getOrNull() ?: throw TranslationException("Google Cloud 响应缺少 translatedText")
            return StringUtil.unescapeXmlEntities(result)
        }
        val response = TranslationHttp.request(TranslationHttp.query(endpoint, mapOf(
            "client" to "gtx", "dt" to "t", "sl" to source, "tl" to target, "q" to text,
        )))
        val result = runCatching {
            response.asJsonArray[0].asJsonArray.joinToString("") {
                val value = it.asJsonArray[0]
                if (value.isJsonNull) "" else value.asString
            }
        }.getOrNull()
        return result?.takeIf { it.isNotBlank() } ?: throw TranslationException("Google 免费端点未返回有效译文")
    }

    /** Cloud API 的非中文语言使用基础代码，中文保留其支持的简繁变体。 */
    private fun cloudCode(locale: String): String = when (locale.lowercase()) {
        "zh-tw", "zh-hk", "zh-hant" -> "zh-TW"
        "zh", "zh-cn", "zh-hans" -> "zh-CN"
        else -> locale.substringBefore('-')
    }
    /** 免费端点对中文使用地区代码，其它代码保持原值。 */
    private fun googleCode(locale: String): String = if (locale.equals("zh", true)) "zh-CN" else locale
}
