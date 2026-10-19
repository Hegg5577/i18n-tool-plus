package com.up3d.i18n.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 译文写入测试。
 *
 * 写入比读取危险得多 —— 读错了只是显示不对，写错了会**损坏语言包**。
 * 所以这里逐条验证「只改目标、其余原样」，这是最不能出错的地方。
 */
class LocaleWriterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val bs: String = 92.toChar().toString() // \
    private val dq: String = 34.toChar().toString() // "

    // region JSON 写入

    @Test
    fun `JSON 写入已有 key 不影响其它内容`() {
        val original = """
            {
              "common": {
                "cancel": "Cancel",
                "ok": "OK"
              }
            }
        """.trimIndent()

        val writer = JsonLocaleWriter(original)
        assertTrue(writer.set("common.cancel", "取消"))

        val text = writer.render()
        assertTrue(text.contains("\"cancel\": \"取消\""))
        // 同级与其它层级必须原样保留
        assertTrue(text.contains("\"ok\": \"OK\""))
    }

    @Test
    fun `JSON 写入时保留原有顺序`() {
        val original = """
            {
              "z": "1",
              "a": "2",
              "m": "3"
            }
        """.trimIndent()

        val writer = JsonLocaleWriter(original)
        writer.set("new", "x")
        val text = writer.render()

        val z = text.indexOf("\"z\"")
        val a = text.indexOf("\"a\"")
        val m = text.indexOf("\"m\"")
        val n = text.indexOf("\"new\"")
        assertTrue("原顺序应保持 z < a < m", z < a && a < m)
        assertTrue("新 key 应在末尾", m < n)
    }

    @Test
    fun `JSON 写入不转义 HTML 字符`() {
        val writer = JsonLocaleWriter("{}")
        writer.set("html", "Detect Teeth & Continue")
        assertTrue(
            "含 & 的文案不应被转义",
            writer.render().contains("Detect Teeth & Continue"),
        )
    }

    @Test
    fun `JSON 逐级创建缺失的父节点`() {
        val writer = JsonLocaleWriter("""{"a":"1"}""")
        assertTrue(writer.set("deep.nested.key", "v"))

        val read = writer.read()
        assertEquals("1", read["a"])
        assertEquals("v", read["deep.nested.key"])
    }

    // endregion

    // region JS 写入

    @Test
    fun `JS 写入替换已有值`() {
        val original = """
            export default {
                public: {
                    title: "MAKE DENTAL",
                    ok: "OK",
                },
            }
        """.trimIndent()

        val writer = JsLocaleWriter(original)
        assertTrue(writer.set("public.title", "新的标题"))

        val text = writer.render()
        assertTrue(text.contains("新的标题"))
        // 注释与 export 前缀必须保留
        assertTrue(text.contains("export default"))
        assertTrue("同级节点不应被动：$text", text.contains("ok: " + dq + "OK" + dq))
    }

    @Test
    fun `JS 写入保留注释与格式`() {
        val original = """
            /*
             * @Author: haolisha
             */
            export default {
                public: {
                    title: "x",
                },
            }
        """.trimIndent()

        val writer = JsLocaleWriter(original)
        writer.set("public.title", "y")
        val text = writer.render()

        assertTrue("头部注释必须保留", text.contains("@Author: haolisha"))
        assertTrue(text.contains("export default"))
    }

    @Test
    fun `JS 写入的值含双引号时能正确转义并往返`() {
        val writer = JsLocaleWriter("export default { a: " + dq + "x" + dq + " }")
        val value = "He said " + dq + "hi" + dq
        assertTrue(writer.set("a", value))

        val text = writer.render()
        assertTrue("值里的双引号应被转义", text.contains(bs + dq))

        // 写入后必须能被解析回原值
        val reread = JsLocaleParser.parse(
            File.createTempFile("roundtrip", ".js").apply {
                writeText(text); deleteOnExit()
            }
        )
        assertEquals(value, reread["a"])
    }

    @Test
    fun `JS 父对象不存在时拒绝写入而非改坏文件`() {
        val original = "export default { public: { title: " + dq + "x" + dq + " } }"
        val writer = JsLocaleWriter(original)

        // none.xist 的父对象 none 不存在
        assertFalse("父对象不存在时不应写入", writer.set("none.xist", "v"))
        assertEquals("文件内容必须完全不变", original, writer.render())
    }

    @Test
    fun `JS 写入后可被解析回来`() {
        val writer = JsLocaleWriter("export default { public: { title: " + dq + "old" + dq + " } }")
        writer.set("public.title", "new")
        writer.set("public.added", "extra")

        val read = JsLocaleParser.parse(
            File.createTempFile("js-write", ".js").apply {
                writeText(writer.render()); deleteOnExit()
            }
        )

        assertEquals("new", read["public.title"])
        assertEquals("extra", read["public.added"])
    }

    @Test
    fun `JS 顶层 key 也能写入`() {
        // 顶层 key 没有父对象，早期实现因 parts.size < 2 直接返回 false
        val writer = JsLocaleWriter("export default { a: " + dq + "old" + dq + " }")
        assertTrue("顶层 key 应可写入", writer.set("a", "new"))
        assertEquals("new", writer.read()["a"])
    }

    // endregion

    // region 真实项目

    @Test
    fun `真实 JSON 语言包可写入且只改动目标 key`() {
        val source = File("E:/CompanyItem/ai-station-web/src/i18n/locales/zh/index.json")
        assertTrue("真实语言包不存在，跳过：" + source.path, source.isFile)

        val original = source.readText()
        val before = JsonLocaleWriter(original).read()

        val writer = JsonLocaleWriter(original)
        assertTrue(writer.set("common.ok", "测试写入"))

        val after = JsonLocaleWriter(writer.render()).read()

        assertEquals("测试写入", after["common.ok"])
        assertEquals("key 数量不应变化", before.size, after.size)
        // 除目标 key 外，其余值必须完全一致
        for (key in before.keys - setOf("common.ok")) {
            assertEquals("key " + key + " 的值被意外改动", before[key], after[key])
        }
    }

    @Test
    fun `真实 JS 语言包可写入且只改动目标 key`() {
        val source = File("E:/CompanyItem/up3d-software-operation-web/src/lang/zh.js")
        assertTrue("真实语言包不存在，跳过：" + source.path, source.isFile)

        val original = source.readText()
        val before = JsLocaleWriter(original).read()
        assertTrue("真实语言包应能读出 key", before.isNotEmpty())

        val writer = JsLocaleWriter(original)
        assertTrue(writer.set("public.title", "测试标题"))

        val after = JsLocaleWriter(writer.render()).read()

        assertEquals("测试标题", after["public.title"])
        assertEquals("key 总数不应变化", before.size, after.size)
        for (key in before.keys - setOf("public.title")) {
            assertEquals("key " + key + " 的值被意外改动", before[key], after[key])
        }
    }

    // endregion
}