package com.hegg5577.i18n.locale

import com.hegg5577.i18n.editor.KeyUsageScanner
import com.hegg5577.i18n.generator.TypeScriptCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 验证逻辑 Key 在多个目录中的命名空间隔离和文件归属。 */
class NamespaceLocaleTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** 使用真实目录覆盖路径解析，避免只验证手工拼出的结果。 */
    private fun write(path: String, text: String): File = File(temp.root, path).apply {
        parentFile.mkdirs()
        writeText(text)
    }

    /** 新配置作为可选尾参数，旧的单目录构造方式继续可用。 */
    private fun repository(dirs: List<String> = listOf("locales"), delimiter: String = ":") =
        LocaleRepository(temp.root, "locales", "{locale}/{namespace}.json",
            localesDirs = dirs, namespaceEnabled = true, namespaceDelimiter = delimiter)

    /** 同名字段在不同命名空间中必须保留各自译文。 */
    @Test
    fun `相同字段由命名空间隔离`() {
        write("locales/en/common.json", """{"title":"Common"}""")
        write("locales/en/page.json", """{"title":"Page"}""")
        val repo = repository()
        assertEquals(mapOf("common:title" to "Common", "page:title" to "Page"), repo.valuesOf("en"))
        assertEquals(setOf("common:title", "page:title"), repo.scanLocales().single().keys)
        val page = repo.resolvedFiles().single { it.namespace == "page" }
        assertEquals("title", repo.rawKey(page, "page:title"))
        assertNull(repo.rawKey(page, "common:title"))
    }

    /** 不同模块的源文件不能把缺失译文写入另一个模块。 */
    @Test
    fun `缺失译文沿源目录定位`() {
        write("a/en/common.json", """{"one":"One"}""")
        val target = write("a/zh/common.json", "{}")
        write("b/en/common.json", """{"two":"Two"}""")
        write("b/zh/common.json", "{}")
        val repo = repository(listOf("a", "b"))
        assertEquals(setOf("common:one", "common:two"), repo.valuesOf("en").keys)
        assertEquals(target.canonicalFile, repo.localeFile("zh", "common:one", "en"))
    }

    /** 已存在的同名译文也不能让保存绕过根目录归属。 */
    @Test
    fun `目标在错误目录时拒绝写入`() {
        write("a/en/common.json", """{"title":"Title"}""")
        write("b/zh/common.json", """{"title":"标题"}""")
        try {
            repository(listOf("a", "b")).localeFile("zh", "common:title", "en")
            fail("必须拒绝跨目录写入")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("不同目录"))
        }
    }

    /** 重复逻辑 Key 显示所有冲突文件，避免静默覆盖其中一个。 */
    @Test
    fun `重复逻辑Key报告具体文件`() {
        val first = write("a/en/common.json", """{"title":"First"}""")
        val second = write("b/en/common.json", """{"title":"Second"}""")
        try {
            repository(listOf("a", "b")).valuesOf("en")
            fail("必须报告重复 Key")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("common:title"))
            assertTrue(e.message.orEmpty().contains(first.canonicalPath))
            assertTrue(e.message.orEmpty().contains(second.canonicalPath))
        }
    }

    /** 相对和绝对路径重复配置时只收集一次文件。 */
    @Test
    fun `目录别名去重且兼容绝对路径`() {
        write("locales/en/common.json", """{"title":"Title"}""")
        val repo = repository(listOf("locales", File(temp.root, "locales").absolutePath))
        assertEquals(1, repo.resolvedFiles().size)
        assertEquals("Title", repo.valuesOf("en")["common:title"])
    }

    /** 自定义分隔符中的正则字符必须按字面量匹配。 */
    @Test
    fun `自定义分隔符贯通内置和自定义扫描规则`() {
        val pattern = KeyUsageScanner.keyPattern("|+")
        val text = "t('common|+title'); translate(`page|+title`); t('commonXXtitle');"
        assertEquals(listOf("common|+title", "commonXXtitle", "page|+title"),
            KeyUsageScanner.scan(text, keyPattern = pattern).map { it.key }.toList())
        assertEquals(listOf("page|+title"),
            KeyUsageScanner.scan("tr('page|+title')", listOf("tr\\(['\"]({key})['\"]"),
                keyPattern = pattern).map { it.key }.toList())
    }

    /** 切换目录、命名空间开关与分隔符后不能复用旧索引。 */
    @Test
    fun `索引区分目录和命名空间配置`() {
        write("a/en/common.json", """{"title":"First"}""")
        write("b/en/common.json", """{"title":"Second"}""")
        try {
            assertEquals("First", TranslationIndex.values(temp.root.path, "en", "",
                repository = repository(listOf("a")))["common:title"])
            assertEquals("Second", TranslationIndex.values(temp.root.path, "en", "",
                repository = repository(listOf("b"), "##"))["common##title"])
            val legacy = LocaleRepository(temp.root, "a", "{locale}/{namespace}.json")
            assertEquals("First", TranslationIndex.values(temp.root.path, "en", "", repository = legacy)["title"])
        } finally {
            TranslationIndex.invalidate()
        }
    }

    /** 新 Key 必须能够唯一定位命名空间，不根据文件排序决定归属。 */
    @Test
    fun `新Key按明确命名空间定位`() {
        val source = write("locales/en/common.json", "{}")
        write("locales/en/page.json", "{}")
        assertEquals(source.canonicalFile, repository().localeFile("en", "common:new", "en"))
    }

    /** 分隔符与命名空间前缀重叠时不能猜测目标文件，否则改名会写错位置。 */
    @Test
    fun `拒绝有歧义的命名空间前缀`() {
        write("locales/en/common.json", "{}")
        write("locales/en/common.admin.json", "{}")
        try {
            repository(delimiter = ".").resolvedFiles()
            fail("必须拒绝有歧义的文件归属")
        } catch (e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("命名空间"))
        }
    }

    /** 类型生成必须包含命名空间和所有目录的 Key。 */
    @Test
    fun `类型生成使用完整仓库配置`() {
        write("a/en/common.json", """{"title":"Title"}""")
        write("b/en/page.json", """{"title":"Page"}""")
        val repo = repository(listOf("a", "b"))
        val types = TypeScriptCodeGenerator.generateTypeDefinitions(temp.root, "a", "{locale}/{namespace}.json",
            KeyStyle.NESTED, repository = repo)
        assertTrue(types.content.contains("\"common:title\""))
        assertTrue(types.content.contains("\"page:title\""))
        val schema = TypeScriptCodeGenerator.generateStructuredSchema(temp.root, "a", "{locale}/{namespace}.json",
            KeyStyle.NESTED, repository = repo)
        assertTrue(schema.content.contains("\"common:title\": true"))
    }
}
