package com.hegg5577.i18n.translator

import org.junit.Assert.assertEquals
import org.junit.Test

/** 结构化译文必须完整有效，否则不能写入项目。 */
class LlmTranslationTest {
    /** 接受模型常见的代码围栏，但保留译文内部换行。 */
    @Test
    fun acceptsJsonCodeFence() {
        assertEquals(listOf("第一行\n第二行"), LlmTranslation.parse("```json\n{\"translations\":[\"第一行\\n第二行\"]}\n```", 1))
    }

    /** 少返回一条不能导致后续文本错位。 */
    @Test(expected = TranslationException::class)
    fun rejectsWrongCount() {
        LlmTranslation.parse("""{"translations":["你好"]}""", 2)
    }

    /** JSON 数字不能被 Gson 自动转成字符串后冒充译文。 */
    @Test(expected = TranslationException::class)
    fun rejectsNonStringTranslation() {
        LlmTranslation.parse("""{"translations":[123]}""", 1)
    }

    /** 模型未按协议作答时应显示明确错误。 */
    @Test(expected = TranslationException::class)
    fun rejectsPlainTextResponse() {
        LlmTranslation.parse("你好", 1)
    }

    /** 完整端点和尾部斜线不重复拼接，查询参数在拼接路径后仍保持有效。 */
    @Test
    fun endpointPreservesQueryParameters() {
        assertEquals("https://example.com/v1/chat/completions?key=value", TranslationHttp.endpoint(
            "https://example.com/v1/?key=value", "/chat/completions",
        ))
        assertEquals("https://example.com/api/chat?key=value", TranslationHttp.endpoint(
            "https://example.com/api/chat/?key=value", "/api/chat",
        ))
    }
    /** 地址配置不能允许带凭据或本地文件协议。 */
    @Test(expected = TranslationException::class)
    fun rejectsNonHttpUrl() {
        TranslationHttp.validateUrl("file:///tmp/translation.json")
    }
}
