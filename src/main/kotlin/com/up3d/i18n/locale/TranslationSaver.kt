package com.hegg5577.i18n.locale

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object TranslationSaver {

    data class Result(
        val succeeded: List<String> = emptyList(),
        val failed: Map<String, String> = emptyMap(),
    ) {
        val isSuccess: Boolean get() = failed.isEmpty() && succeeded.isNotEmpty()
    }

    fun save(
        project: Project,
        repo: LocaleRepository,
        sourceLocale: String,
        edits: Map<String, Map<String, String>>,
    ): Result {
        if (edits.isEmpty()) return Result()
        val succeeded = ArrayList<String>()
        val failed = LinkedHashMap<String, String>()
        val prepared = LinkedHashMap<String, Pair<VirtualFile, String>>()

        for ((locale, changes) in edits) {
            if (changes.isEmpty()) continue
            val ioFile: File = repo.localeFile(locale) ?: run {
                failed[locale] = "未找到对应的语言包文件"
                continue
            }
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)
            if (virtualFile == null) {
                failed[locale] = "VFS 找不到文件: ${ioFile.name}"
                continue
            }
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            if (document == null) {
                failed[locale] = "无法获取 Document"
                continue
            }
            val updated = runCatching { applyChanges(document.text, changes) }
            if (updated.isFailure) {
                failed[locale] = updated.exceptionOrNull()?.message ?: "写入失败"
                continue
            }
            prepared[locale] = virtualFile to updated.getOrThrow()
        }

        if (prepared.isEmpty()) return Result(succeeded, failed)

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project, "i18n: 保存翻译", null, {
                for ((locale, pair) in prepared) {
                    val (virtualFile, newText) = pair
                    val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: continue
                    document.setText(newText)
                    FileDocumentManager.getInstance().saveDocument(document)
                    succeeded += locale
                }
            })
        }
        return Result(succeeded, failed)
    }

    internal fun applyChanges(
        sourceText: String,
        changes: Map<String, String>,
    ): String {
        val writer = JsonLocaleWriter(sourceText)
        for ((key, value) in changes) {
            if (!writer.set(key, value)) {
                throw IllegalStateException("无法写入 key: $key")
            }
        }
        return writer.render()
    }

    fun missingFor(
        repo: LocaleRepository,
        sourceLocale: String,
        targetLocales: List<String>,
    ): Map<String, List<String>> {
        val source = repo.valuesOf(sourceLocale)
        return targetLocales.associateWith { locale ->
            val values = repo.valuesOf(locale)
            source.keys.filter { key -> values[key].isNullOrBlank() }
        }
    }

    /**
     * 从全部语言包中删除指定 key。
     *
     * @param repo 语言库实例
     * @param keysToDelete 要删除的 key 列表
     * @return 操作结果（成功/失败信息）
     */
    fun deleteKeys(
        project: Project,
        repo: LocaleRepository,
        keysToDelete: List<String>,
    ): Result {
        if (keysToDelete.isEmpty()) return Result()

        val succeeded = ArrayList<String>()
        val failed = LinkedHashMap<String, String>()
        val prepared = LinkedHashMap<String, Pair<VirtualFile, String>>()

        // 对每个语言包进行删除
        for ((locale, localeFile) in repo.localeFiles()) {
            if (localeFile == null) continue

            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localeFile)
            if (virtualFile == null) {
                failed[locale] = "VFS 找不到文件: ${localeFile.name}"
                continue
            }

            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            if (document == null) {
                failed[locale] = "无法获取 Document"
                continue
            }

            val updated = runCatching { deleteKeysFromJson(document.text, keysToDelete) }
            if (updated.isFailure) {
                failed[locale] = updated.exceptionOrNull()?.message ?: "删除失败"
                continue
            }

            prepared[locale] = virtualFile to updated.getOrThrow()
        }

        if (prepared.isEmpty()) return Result(succeeded, failed)

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project, "i18n: 删除 key", null, {
                for ((locale, pair) in prepared) {
                    val (virtualFile, newText) = pair
                    val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return@runWriteCommandAction
                    document.setText(newText)
                    succeeded += locale
                }
            })
        }

        return Result(succeeded, failed)
    }

    /**
     * 从 JSON 文本中删除指定 key。
     * 支持嵌套路径，如 `common.ok` 删除 `{"common": {"ok": "..."}}`。
     */
    private fun deleteKeysFromJson(sourceText: String, keysToDelete: List<String>): String {
        val root = com.google.gson.JsonParser.parseString(sourceText).asJsonObject

        for (key in keysToDelete) {
            deleteNested(root, key)
        }

        return LocaleFileWriter.render(root)
    }

    /**
     * 从根对象中按点号路径删除 key。
     * 如果中间层级被清空则一并删除。
     */
    private fun deleteNested(root: com.google.gson.JsonObject, dottedKey: String) {
        val parts = dottedKey.split('.')
        if (parts.isEmpty()) return

        val keys = mutableListOf<Pair<com.google.gson.JsonObject, String>>()
        var current = root

        // 定位到倒数第二层
        for (i in 0 until parts.lastIndex) {
            val part = parts[i]
            val existing = current[part]
            keys += current to part

            current = if (existing != null && existing.isJsonObject) {
                existing.asJsonObject
            } else {
                // 中间路径不存在，无需删除
                return
            }
        }

        // 删除最后一个 key
        current.remove(parts[parts.lastIndex])

        // 清理空的中间层级（从底向上）
        for ((obj, key) in keys.asReversed()) {
            val child = obj[key]
            if (child != null && child.isJsonObject && child.asJsonObject.size() == 0) {
                obj.remove(key)
            } else {
                break
            }
        }
    }

    /**
     * 将指定 key 在所有语言包中重命名。
     *
     * @param repo 语言库实例
     * @param oldKey 旧 key
     * @param newKey 新 key
     * @return 操作结果
     */
    fun renameKey(
        project: Project,
        repo: LocaleRepository,
        oldKey: String,
        newKey: String,
    ): Result {
        if (oldKey == newKey) return Result()

        val succeeded = ArrayList<String>()
        val failed = LinkedHashMap<String, String>()
        val prepared = LinkedHashMap<String, Pair<VirtualFile, String>>()

        // 对每个语言包进行重命名
        for ((locale, localeFile) in repo.localeFiles()) {
            if (localeFile == null) continue

            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localeFile)
            if (virtualFile == null) {
                failed[locale] = "VFS 找不到文件: ${localeFile.name}"
                continue
            }

            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            if (document == null) {
                failed[locale] = "无法获取 Document"
                continue
            }

            val updated = runCatching { renameKeyInJson(document.text, oldKey, newKey) }
            if (updated.isFailure) {
                failed[locale] = updated.exceptionOrNull()?.message ?: "重命名失败"
                continue
            }

            prepared[locale] = virtualFile to updated.getOrThrow()
        }

        if (prepared.isEmpty()) return Result(succeeded, failed)

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project, "i18n: 重命名 key", null, {
                for ((locale, pair) in prepared) {
                    val (virtualFile, newText) = pair
                    val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return@runWriteCommandAction
                    document.setText(newText)
                    succeeded += locale
                }
            })
        }

        return Result(succeeded, failed)
    }

    /**
     * 在 JSON 文本中重命名 key。
     * 如果旧 key 存在，读取其值，删除旧 key，写入新 key。
     */
    private fun renameKeyInJson(sourceText: String, oldKey: String, newKey: String): String {
        val root = com.google.gson.JsonParser.parseString(sourceText).asJsonObject
        val writer = JsonLocaleWriter(sourceText)

        // 读取旧 key 的值
        val value = getNestedValue(root, oldKey)
        if (value != null) {
            // 删除旧 key
            deleteNested(root, oldKey)
            // 写入新 key
            LocaleFileWriter.setNested(root, newKey, value)
        }

        return LocaleFileWriter.render(root)
    }

    /**
     * 从根对象按点号路径读取值。
     */
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
