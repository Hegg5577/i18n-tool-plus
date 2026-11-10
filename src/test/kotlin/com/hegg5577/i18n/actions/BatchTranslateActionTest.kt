package com.hegg5577.i18n.actions

import com.hegg5577.i18n.translator.TranslationEngine
import com.hegg5577.i18n.translator.TranslationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证批量翻译使用传入引擎，并在保存前保留现有译文。 */
class BatchTranslateActionTest {

    /** 预览与实际翻译必须使用同一引擎，并通过通用接口传递源语言。 */
    @Test
    fun usesSelectedEngineForPreviewAndTranslation() {
        val action = BatchTranslateAction()
        val requests = mutableListOf<Triple<List<String>, String, String>>()
        val engine = object : TranslationEngine {
            override val id = "configured-engine"
            override val displayName = "配置的翻译引擎"

            /** 回显受保护文本，以验证请求参数与占位符还原。 */
            override fun translate(texts: List<String>, targetLocale: String, sourceLocale: String): List<String> {
                requests += Triple(texts, targetLocale, sourceLocale)
                return texts
            }
        }
        val unit = BatchTranslateAction.TranslateUnit("ja", "你好 {name}", listOf("greeting"))

        assertTrue(action.buildPreview(listOf(unit), "zh", engine).contains("翻译引擎：配置的翻译引擎"))
        assertEquals("你好 {name}", action.translateUnit(unit, "zh", engine))
        assertEquals(listOf(Triple(listOf("你好 __P0__"), "ja", "zh")), requests)
    }

    /** 单条请求不能接受多条响应，防止错误地把第一条结果写入语言包。 */
    @Test(expected = TranslationException::class)
    fun rejectsUnexpectedTranslationCount() {
        val engine = object : TranslationEngine {
            override val id = "fake"
            override val displayName = "测试引擎"

            /** 模拟引擎返回与请求数量不符的响应。 */
            override fun translate(texts: List<String>, targetLocale: String, sourceLocale: String): List<String> =
                listOf("第一条", "多余结果")
        }

        BatchTranslateAction().translateUnit(
            BatchTranslateAction.TranslateUnit("zh", "Hello", listOf("greeting")),
            "en",
            engine,
        )
    }

    /** 默认补齐缺失、空串及纯空白，保留人工译文，空白源文不发起请求。 */
    @Test
    fun selectsMissingAndEmptyTranslations() {
        val source = linkedMapOf("missing" to "Hello", "empty" to "Hello", "blank" to "World",
            "manual" to "Keep", "blankSource" to "  ")
        val target = mapOf("zh" to mapOf("empty" to "", "blank" to " \n\t", "manual" to "人工译文"))
        val units = BatchTranslateAction().buildUnits(source, target)

        assertEquals(2, units.size)
        assertEquals(listOf("missing", "empty"), units[0].keys)
        assertEquals("Hello", units[0].sourceText)
        assertEquals(listOf("blank"), units[1].keys)
    }

    /** 开启覆盖后包含已有译文，但目标语言独有的 key 与空源文不应参与翻译。 */
    @Test
    fun overwriteIncludesExistingTranslations() {
        val source = mapOf("manual" to "Hello", "blankSource" to "")
        val target = mapOf("zh" to mapOf("manual" to "人工译文", "extra" to "保留"))
        val units = BatchTranslateAction().buildUnits(source, target, overrideExisting = true)

        assertEquals(1, units.size)
        assertEquals(listOf("manual"), units.single().keys)
        assertTrue(BatchTranslateAction().buildUnits(source, target).isEmpty())
    }

    /** 即使相同原文可合并请求，不同目标语言也必须分别保留。 */
    @Test
    fun keepsTargetLocalesSeparate() {
        val units = BatchTranslateAction().buildUnits(mapOf("hello" to "Hello"),
            mapOf("zh" to emptyMap(), "ja" to emptyMap()))
        assertEquals(listOf("zh", "ja"), units.map { it.locale })
    }

    /** 确认弹窗应明确显示覆盖风险，不得沿用默认的保留译文承诺。 */
    @Test
    fun previewExplainsOverwritePolicy() {
        val engine = object : TranslationEngine {
            override val id = "fake"
            override val displayName = "测试引擎"

            /** 预览无需联网，保持接口可调用。 */
            override fun translate(texts: List<String>, targetLocale: String, sourceLocale: String): List<String> = texts
        }
        val action = BatchTranslateAction()
        val units = listOf(BatchTranslateAction.TranslateUnit("zh", "Hello", listOf("hello")))
        assertTrue(action.buildPreview(units, "en", engine).contains("只补全缺失或空白译文"))
        assertTrue(action.buildPreview(units, "en", engine, true).contains("包括人工译文"))
    }

    /** 空白响应应报告失败，不能把已有内容覆盖成没有译文的状态。 */
    @Test(expected = TranslationException::class)
    fun rejectsBlankTranslation() {
        val engine = object : TranslationEngine {
            override val id = "fake"
            override val displayName = "测试引擎"

            /** 模拟服务返回数量正确但内容为空白的响应。 */
            override fun translate(texts: List<String>, targetLocale: String, sourceLocale: String): List<String> =
                listOf(" \n\t")
        }
        BatchTranslateAction().translateUnit(BatchTranslateAction.TranslateUnit("zh", "Hello", listOf("hello")),
            "en", engine)
    }
}
