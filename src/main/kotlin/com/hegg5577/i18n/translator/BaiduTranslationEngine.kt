package com.hegg5577.i18n.translator

import java.security.MessageDigest
import java.util.UUID

/** 百度通用翻译 API，使用 App ID 与密钥对原文签名。 */
class BaiduTranslationEngine(
    private val appId: String,
    private val apiSecret: String,
    private val apiUrl: String = "https://fanyi-api.baidu.com/api/trans/vip/translate",
) : TranslationEngine {
    override val id = "baidu"
    override val displayName = "百度翻译"

    /** 逐条请求以保持原文和译文边界，签名必须使用编码前的原文。 */
    override fun translate(texts: List<String>, targetLocale: String, sourceLocale: String): List<String> {
        if (texts.isEmpty()) return emptyList()
        if (appId.isBlank() || apiSecret.isBlank()) throw TranslationException("百度翻译需要配置 App ID 和密钥")
        return texts.map { text ->
            val salt = UUID.randomUUID().toString()
            val form = TranslationHttp.form(mapOf(
                "q" to text, "from" to languageCode(sourceLocale), "to" to languageCode(targetLocale),
                "appid" to appId.trim(), "salt" to salt, "sign" to sign(text, salt),
            ))
            val response = TranslationHttp.request(apiUrl, form, "application/x-www-form-urlencoded").asJsonObject
            val error = response.get("error_code")?.asString
            if (error != null && error != "52000") {
                throw TranslationException("百度翻译失败（错误码 $error），请检查凭据、额度、语言和调用频率")
            }
            val results = runCatching { response.getAsJsonArray("trans_result")?.map { it.asJsonObject.get("dst").asString } }
                .getOrNull() ?: throw TranslationException("百度翻译响应缺少有效的 trans_result")
            if (results.isEmpty() || results.any { it.isBlank() } && text.isNotBlank()) {
                throw TranslationException("百度翻译返回了空译文")
            }
            results.joinToString("\n")
        }
    }

    /** 百度协议指定 MD5；这里只用于请求签名，不用于存储或加密密钥。 */
    private fun sign(text: String, salt: String): String = MessageDigest.getInstance("MD5")
        .digest((appId.trim() + text + salt + apiSecret.trim()).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** 百度使用自己的语言简称，不能直接截取 ja、ko、fr 等标准代码。 */
    private fun languageCode(locale: String): String {
        val code = locale.replace('_', '-').lowercase()
        if (code in listOf("zh-tw", "zh-hk", "zh-hant")) return "cht"
        return when (val language = code.substringBefore('-')) {
            "ja" -> "jp"
            "ko" -> "kor"
            "fr" -> "fra"
            "es" -> "spa"
            "ar" -> "ara"
            "vi" -> "vie"
            "sl" -> "slo"
            "bg" -> "bul"
            "et" -> "est"
            "da" -> "dan"
            "fi" -> "fin"
            "ro" -> "rom"
            "sv" -> "swe"
            else -> language
        }
    }
}
