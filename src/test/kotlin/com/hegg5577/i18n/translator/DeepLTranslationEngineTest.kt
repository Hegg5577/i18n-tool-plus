package com.hegg5577.i18n.translator

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 验证 DeepL 请求协议，避免字段名、源语言或端点错误导致所有翻译失败。 */
class DeepLTranslationEngineTest {

    /** DeepL 要求 text 数组，且必须保留每条原文及其顺序。 */
    @Test
    fun requestUsesRequiredTextArray() {
        val texts = listOf("Hello {name}", "你好\n\"用户\"")
        val body = requestBody(texts, "zh", "en")

        assertEquals(texts, body.getAsJsonArray("text").map { it.asString })
        assertFalse(body.has("texts"))
        assertEquals("ZH", body.get("target_lang").asString)
        assertEquals("EN", body.get("source_lang").asString)
    }

    /** 英语、葡语及中文的源语言不能沿用目标语言的地区映射。 */
    @Test
    fun sourceLanguageOmitsRegionalVariant() {
        for ((locale, expected) in mapOf("en" to "EN", "en-US" to "EN", "pt-BR" to "PT", "zh-CN" to "ZH")) {
            val body = requestBody(listOf("Hello"), "pt-BR", locale)
            assertEquals(expected, body.get("source_lang").asString)
            assertEquals("PT-BR", body.get("target_lang").asString)
        }
        assertEquals("EN-US", requestBody(listOf("你好"), "en", "zh").get("target_lang").asString)
    }

    /** 免费与专业端点必须使用相同的 v2 协议，避免切换账户类型后失效。 */
    @Test
    fun endpointsUseV2ForBothAccountTypes() {
        val method = DeepLTranslationEngine::class.java.getDeclaredMethod("endpoint").apply { isAccessible = true }

        assertEquals("https://api-free.deepl.com/v2/translate", method.invoke(DeepLTranslationEngine("test-key", true)))
        assertEquals("https://api.deepl.com/v2/translate", method.invoke(DeepLTranslationEngine("test-key", false)))
    }

    /** 检查真实请求构造方法，不发送网络请求，也不读取用户密钥。 */
    private fun requestBody(texts: List<String>, targetLocale: String, sourceLocale: String): JsonObject {
        val method = DeepLTranslationEngine::class.java.getDeclaredMethod(
            "createRequestBody", List::class.java, String::class.java, String::class.java,
        ).apply { isAccessible = true }
        return method.invoke(DeepLTranslationEngine("test-key"), texts, targetLocale, sourceLocale) as JsonObject
    }
}
