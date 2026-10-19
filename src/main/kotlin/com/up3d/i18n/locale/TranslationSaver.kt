package com.up3d.i18n.locale

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
}
