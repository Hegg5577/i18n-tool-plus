package com.up3d.i18n.locale

import com.google.gson.JsonParser
import org.junit.Test
import org.junit.Assert.*

class TranslationSaverBatchOperationsTest {

    @Test
    fun testDeleteKeyFromNestedJson() {
        val json = """
{
  "common": {
    "ok": "OK",
    "cancel": "Cancel"
  },
  "form": {
    "submit": "Submit"
  }
}
        """.trimIndent()

        val root = JsonParser.parseString(json).asJsonObject
        val writer = JsonLocaleWriter(json)

        // 删除 common.cancel
        deleteNested(root, "common.cancel")

        val result = writer.render()
        assertTrue(result.contains("\"ok\""))
        assertFalse(result.contains("\"cancel\""))
        assertTrue(result.contains("\"submit\""))
    }

    @Test
    fun testDeleteLastKeyRemovesParent() {
        val json = """
{
  "temp": {
    "value": "will be deleted"
  },
  "keep": "this"
}
        """.trimIndent()

        val root = JsonParser.parseString(json).asJsonObject

        deleteNested(root, "temp.value")

        // 如果 temp 下没有其他 key，temp 也会被删除
        val result = JsonLocaleWriter(json).render()
        assertFalse(result.contains("\"temp\""))
        assertTrue(result.contains("\"keep\""))
    }

    @Test
    fun testRenameKeyMovesValue() {
        val json = """
{
  "old_key": "Old Value",
  "other": "Other"
}
        """.trimIndent()

        val root = JsonParser.parseString(json).asJsonObject
        val writer = JsonLocaleWriter(json)

        // 获取旧值
        val value = getNestedValue(root, "old_key")
        assertEquals("Old Value", value)

        // 删除旧 key，写入新 key
        deleteNested(root, "old_key")
        LocaleFileWriter.setNested(root, "new_key", value ?: "")

        val result = JsonLocaleWriter(json).render()
        assertFalse(result.contains("\"old_key\""))
        assertTrue(result.contains("\"new_key\""))
        assertTrue(result.contains("\"Old Value\""))
    }

    @Test
    fun testDeleteNestedPath() {
        val json = """
{
  "a": {
    "b": {
      "c": {
        "d": "value"
      }
    }
  }
}
        """.trimIndent()

        val root = JsonParser.parseString(json).asJsonObject
        deleteNested(root, "a.b.c.d")

        val result = JsonLocaleWriter(json).render()
        assertFalse(result.contains("\"d\""))
        assertFalse(result.contains("\"value\""))
    }

    @Test
    fun testDeleteNonExistentKeyDoesNothing() {
        val json = """
{
  "exists": "yes"
}
        """.trimIndent()

        val root = JsonParser.parseString(json).asJsonObject
        deleteNested(root, "does.not.exist")

        val result = JsonLocaleWriter(json).render()
        assertTrue(result.contains("\"exists\""))
    }

    @Test
    fun testGetNestedValue() {
        val json = """
{
  "level1": {
    "level2": {
      "key": "value"
    }
  }
}
        """.trimIndent()

        val root = JsonParser.parseString(json).asJsonObject
        assertEquals("value", getNestedValue(root, "level1.level2.key"))
        assertNull(getNestedValue(root, "level1.level2.nonexist"))
        assertNull(getNestedValue(root, "nonexist"))
    }

    // 辅助方法（从 TranslationSaver 复制用于测试）
    private fun deleteNested(root: com.google.gson.JsonObject, dottedKey: String) {
        val parts = dottedKey.split('.')
        if (parts.isEmpty()) return

        val keys = mutableListOf<Pair<com.google.gson.JsonObject, String>>()
        var current = root

        for (i in 0 until parts.lastIndex) {
            val part = parts[i]
            val existing = current[part]
            keys += current to part

            current = if (existing != null && existing.isJsonObject) {
                existing.asJsonObject
            } else {
                return
            }
        }

        current.remove(parts[parts.lastIndex])

        for ((obj, key) in keys.asReversed()) {
            val child = obj[key]
            if (child != null && child.isJsonObject && child.asJsonObject.size() == 0) {
                obj.remove(key)
            } else {
                break
            }
        }
    }

    private fun getNestedValue(root: com.google.gson.JsonObject, dottedKey: String): String? {
        val parts = dottedKey.split('.')
        var current: com.google.gson.JsonElement = root

        for (part in parts) {
            if (!current.isJsonObject) return null
            current = current.asJsonObject[part] ?: return null
        }

        return if (current.isJsonPrimitive) current.asString else null
    }
}
