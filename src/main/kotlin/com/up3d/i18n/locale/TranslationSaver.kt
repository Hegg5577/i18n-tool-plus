package com.up3d.i18n.locale

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * 译文保存服务：把 key 的译文写回语言包文件。
 *
 * 走 VFS Document 写入而不是 [File.writeText]，这样整批改动能像普通编辑一样
 * 被 Ctrl+Z 撤销 —— 语言包是业务资产，误改必须能一键回退。
 */
object TranslationSaver {

    /** 一次保存的结果。 */
    data class Result(
        /** 成功写入的语言。 */
        val succeeded: List<String> = emptyList(),
        /** 失败项：语言 -> 失败原因。 */
        val failed: Map<String, String> = emptyMap(),
    ) {
        val isSuccess: Boolean get() = failed.isEmpty() && succeeded.isNotEmpty()
    }

    /**
     * 写入一批译文。
     *
     * @param project 项目
     * @param repo 语言包仓库，用于定位各语言的实际文件路径
     * @param sourceLocale 源语言；它的值会作为「填充其它语言」的默认值来源
     * @param edits 目标语言 -> (key -> 新译文)
     */
    fun save(
        project: Project,
        repo: LocaleRepository,
        sourceLocale: String,
        edits: Map<String, Map<String, String>>,
    ): Result {
        if (edits.isEmpty()) return Result()

        val succeeded = ArrayList<String>()
        val failed = LinkedHashMap<String, String>()

        // 先解析所有目标文件，写操作统一放在一个命令里，避免产生多次撤销记录
        val prepared = LinkedHashMap<String, Pair<VirtualFile, String>>()

        for ((locale, changes) in edits) {
            if (changes.isEmpty()) continue
            val ioFile: File = repo.localeFile(locale) ?: run {
                failed[locale] = "未找到该语言的语言包文件"
                continue
            }
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)
            if (virtualFile == null) {
                failed[locale] = "无法定位文件：${ioFile.name}"
                continue
            }
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            if (document == null) {
                failed[locale] = "无法获取文档"
                continue
            }

            val updated = runCatching { applyChanges(document.text, ioFile, changes) }
            if (updated.isFailure) {
                failed[locale] = updated.exceptionOrNull()?.message ?: "写入失败"
                continue
            }
            prepared[locale] = virtualFile to updated.getOrThrow()
        }

        if (prepared.isEmpty()) return Result(succeeded, failed)

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project, "i18n: 保存译文", null, {
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

    /**
     * 把一组 key 的译文应用到文件文本。
     *
     * @param sourceText 原文
     * @param ioFile 目标文件，用于决定用 JSON 还是 JS 写入器
     */
    internal fun applyChanges(
        sourceText: String,
        ioFile: File,
        changes: Map<String, String>,
    ): String {
        val writer: LocaleWriter = if (JsLocaleParser.supports(ioFile)) {
            JsLocaleWriter(sourceText)
        } else {
            JsonLocaleWriter(sourceText)
        }

        // 任一条失败即整体放弃：避免只写入一半、留下不一致的语言包
        for ((key, value) in changes) {
            if (!writer.set(key, value)) {
                throw IllegalStateException("无法定位 key「$key」")
            }
        }
        return writer.render()
    }

    /**
     * 以源语言为模板，为其它语言生成待填写的 key 集合。
     *
     * 用于「一键补齐」：找出所有语言包里都还没有的 key，让用户集中处理。
     *
     * @return 目标语言 -> 缺失的 key 列表
     */
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