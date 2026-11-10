package com.hegg5577.i18n.generator

import com.hegg5577.i18n.locale.KeyStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.File

class TypeScriptCodeGeneratorTest {
    @get:Rule
    val temp = TemporaryFolder()

    /** 空仓库也必须生成合法的联合类型。 */
    @Test
    fun emptyRepositoryGeneratesNever() {
        val types = TypeScriptCodeGenerator.generateTypeDefinitions(temp.root, "locales", "{locale}.json", KeyStyle.AUTO)
        val vue = TypeScriptCodeGenerator.generateVueComponentTypes(temp.root, "locales", "{locale}.json", KeyStyle.AUTO)
        assertTrue(types.content.contains("export type I18nKeys = never;"))
        assertTrue(vue.content.contains("export type I18nKey = never;"))
    }

    /** Key 中的引号和控制字符必须按字面值输出，不能破坏生成文件。 */
    @Test
    fun specialKeysAreEscaped() {
        val key = "say\"hello\\path\nline"
        writeKeys(key)
        val escaped = JsonPrimitive(key).toString()
        val types = TypeScriptCodeGenerator.generateTypeDefinitions(temp.root, "locales", "{locale}.json", KeyStyle.FLAT)
        val vue = TypeScriptCodeGenerator.generateVueComponentTypes(temp.root, "locales", "{locale}.json", KeyStyle.FLAT)
        val schema = TypeScriptCodeGenerator.generateStructuredSchema(temp.root, "locales", "{locale}.json", KeyStyle.FLAT)
        assertTrue(types.content.contains("| $escaped;"))
        assertTrue(types.content.contains("$escaped: string;"))
        assertTrue(vue.content.contains("export type I18nKey = $escaped;"))
        assertTrue(schema.content.contains("$escaped: true"))
    }

    /** 跨语言同时出现父 Key 和子 Key 时，两者都必须保留。 */
    @Test
    fun parentAndChildKeysDoNotCrashSchema() {
        writeKeys("a", "a.b")
        val schema = TypeScriptCodeGenerator.generateStructuredSchema(temp.root, "locales", "{locale}.json", KeyStyle.AUTO)
        assertTrue(schema.content.contains("\"a\": true"))
        assertTrue(schema.content.contains("\"a.b\": true"))
    }

    /** 使用实际语言包验证读取到输出的完整流程。 */
    private fun writeKeys(vararg keys: String) {
        val file = File(temp.root, "locales/en.json")
        file.parentFile.mkdirs()
        file.writeText(JsonObject().apply { keys.forEach { addProperty(it, "value") } }.toString())
    }


    @Test
    fun testGenerateTypeDefinitions() {
        val generated = TypeScriptCodeGenerator.GeneratedCode(
            filePath = "src/types/i18n.ts",
            content = "test content"
        )

        assertTrue(generated.filePath.endsWith(".ts"))
        assertTrue(generated.language == "typescript")
    }

    @Test
    fun testTypeUnionGeneration() {
        // 这里我们测试生成的内容应该包含类型定义
        val generated = TypeScriptCodeGenerator.generateTypeDefinitions(
            projectRoot = java.io.File("."),
            localesDir = "locales",
            pathMatcher = "{locale}/{file}",
            keyStyle = KeyStyle.NESTED
        )

        // 验证生成的文件包含必要的导出
        assertTrue(generated.content.contains("export type I18nKeys"))
        assertTrue(generated.content.contains("export interface I18nMessages"))
    }

    @Test
    fun testStructuredSchemaGeneration() {
        val generated = TypeScriptCodeGenerator.generateStructuredSchema(
            projectRoot = java.io.File("."),
            localesDir = "locales",
            pathMatcher = "{locale}/{file}",
            keyStyle = KeyStyle.NESTED
        )

        assertTrue(generated.content.contains("export const I18nSchema"))
        assertTrue(generated.content.contains("export type I18nSchemaType"))
    }

    @Test
    fun testVueComponentTypesGeneration() {
        val generated = TypeScriptCodeGenerator.generateVueComponentTypes(
            projectRoot = java.io.File("."),
            localesDir = "locales",
            pathMatcher = "{locale}/{file}",
            keyStyle = KeyStyle.NESTED
        )

        assertTrue(generated.content.contains("import { computed, ref, Ref } from 'vue'"))
        assertTrue(generated.content.contains("export function useI18n"))
    }
}
