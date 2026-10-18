package com.up3d.i18n.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JS 语言包解析测试。
 *
 * 覆盖真实项目 `up3d-software-operation-web/src/lang/{locale}.js` 的格式：
 * 头部块注释、`export default {...}`、裸键名、单双引号混用、模板字符串、尾随逗号。
 */
class JsLocaleParserTest {

    // region 规整逻辑

    @Test
    fun `剥掉 export default 与注释`() {
        val source = """
            /*
             * @Author: haolisha
             */
            // 这是行注释
            export default {
                a: "1"
            }
        """.trimIndent()

        val json = JsLocaleParser.toJsonLike(source)

        assertFalse("不应残留注释", json.contains("@Author"))
        assertFalse("不应残留 export", json.contains("export"))
        assertTrue(json.trim().startsWith("{"))
    }

    @Test
    fun `字符串里含注释符时不被误删`() {
        val json = JsLocaleParser.toJsonLike(
            """export default { url: "https://example.com/a//b", note: "/* 不是注释 */" }"""
        )

        assertTrue(json.contains("https://example.com/a//b"))
        assertTrue(json.contains("/* 不是注释 */"))
    }

    @Test
    fun `裸键名补引号`() {
        val json = JsLocaleParser.toJsonLike(
            """export default { public: { title: "x" }, "quoted.key": "y" }"""
        )

        val root = com.google.gson.JsonParser.parseString(json).asJsonObject
        assertTrue("public 应被加上引号", root.has("public"))
        assertTrue(root.has("quoted.key"))
    }

    @Test
    fun `去掉尾随逗号`() {
        val json = JsLocaleParser.toJsonLike(
            """export default { a: "1", b: "2", }"""
        )

        // 能解析即说明尾随逗号已被处理
        val root = com.google.gson.JsonParser.parseString(json).asJsonObject
        assertEquals(2, root.size())
    }

    @Test
    fun `CommonJS 导出写法`() {
        val json = JsLocaleParser.toJsonLike(
            """module.exports = { a: "1" }"""
        )

        assertEquals("1", com.google.gson.JsonParser.parseString(json).asJsonObject.get("a").asString)
    }

    // endregion

    // region 完整解析

    @Test
    fun `解析带注释与混用引号的语言包`() {
        val file = File.createTempFile("locale-en", ".js")
        file.writeText(
            """
            /*
             * @Author: test
             */
            export default {
                public: {
                    title: "MAKE DENTAL DESIGN AN ART",
                    ok: '确定',
                },
                common: {
                    tip: `模板 {name} 字符串`,
                },
            }
            """.trimIndent()
        )

        val values = JsLocaleParser.parse(file)

        assertEquals("MAKE DENTAL DESIGN AN ART", values["public.title"])
        assertEquals("确定", values["public.ok"])
        assertEquals("模板 {name} 字符串", values["common.tip"])

        file.delete()
    }

    @Test
    fun `真实项目的 js 语言包能被解析`() {
        val projectRoot = File("E:/CompanyItem/up3d-software-operation-web")
        assertTrue("真实项目不存在，跳过：$projectRoot", projectRoot.isDirectory)

        val repo = LocaleRepository(
            projectRoot = projectRoot,
            localesDir = "src/lang",
            pathMatcher = "{locale}.js",
        )
        val scanned = repo.scanLocales()

        assertTrue("应扫到多个语言，实际：${scanned.map { it.locale }}", scanned.size >= 8)
        assertTrue("应包含 en", scanned.any { it.locale == "en" })

        val en = repo.valuesOf("en")
        assertTrue("en 应有几十个 key，实际 ${en.size}", en.size > 30)
        assertEquals("MAKE  DENTAL  DESIGN  AN  ART", en["public.title"])
        assertEquals("Username", en["public.username"])
    }

    @Test
    fun `仓库按扩展名自动分派 json 与 js`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "i18n-mixed-${System.nanoTime()}")
        val locales = File(dir, "locales").apply { mkdirs() }

        File(locales, "en").mkdirs()
        File(locales, "en/index.json").writeText("""{"a":"fromJson"}""")
        File(locales, "zh").mkdirs()
        File(locales, "zh/zh.js").writeText("""export default { a: "fromJs" }""")

        // 模式 {locale}/{file}：en 下是 index.json，zh 下是 zh.js
        val repo = LocaleRepository(dir, "locales")
        assertEquals("fromJson", repo.valuesOf("en")["a"])

        val jsRepo = LocaleRepository(dir, "locales", pathMatcher = "{locale}/{locale}.js")
        assertEquals("fromJs", jsRepo.valuesOf("zh")["a"])

        dir.deleteRecursively()
    }

    // endregion
}