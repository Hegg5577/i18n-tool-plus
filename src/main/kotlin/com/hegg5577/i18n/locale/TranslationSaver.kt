package com.hegg5577.i18n.locale

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.ManagingFS
import java.io.File

/** 统一准备语言包修改，全部校验通过后才在一个撤销命令中提交。 */
object TranslationSaver {
    data class Result(
        val succeeded: List<String> = emptyList(),
        val failed: Map<String, String> = emptyMap(),
    ) {
        val isSuccess: Boolean get() = failed.isEmpty() && succeeded.isNotEmpty()
    }

    /**
     * 按 key 所属文件保存，缺失译文沿用源语言的文件归属。
     * onlyMissing 默认保留所有已有 key；配合 fillEmpty 时允许填充空白值，检查使用最新 Document。
     */
    fun save(
        project: Project,
        repo: LocaleRepository,
        sourceLocale: String,
        edits: Map<String, Map<String, String>>,
        onlyMissing: Boolean = false,
        fillEmpty: Boolean = false,
    ): Result = onEventThread {
        val changes = linkedMapOf<Document, String>()
        val locales = linkedSetOf<String>()
        val failed = linkedMapOf<String, String>()
        val resolved = repo.resolvedFiles()
        val current = try {
            resolved.associate { file -> file.file to repo.logicalValues(file,
                JsonLocaleWriter(document(file.file).text, repo.keyStyle).read()) }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            return@onEventThread Result(failed = mapOf("语言包" to (e.message ?: "读取失败")))
        }
        for ((locale, values) in edits) {
            if (values.isEmpty()) continue
            try {
                val existing = current.filterKeys { file -> resolved.any { it.locale == locale && it.file == file } }
                repo.validateUnique(locale, existing)
                val destinations = values.keys.associateWith { key ->
                    repo.localeFile(locale, key, sourceLocale, current)
                        ?: error("未找到 $locale 对应的语言包文件：$key")
                }
                // 缺失检查与写入同处事件线程，保留等待翻译期间新增的未保存译文。
                val pending = if (onlyMissing) {
                    values.filterKeys { key ->
                        existing.values.none { entries ->
                            key in entries && (!fillEmpty || !entries[key].isNullOrBlank())
                        }
                    }
                } else values
                if (pending.isEmpty()) continue
                val grouped = pending.entries.groupBy { (key, _) -> destinations.getValue(key) }
                for ((file, entries) in grouped) {
                    val target = document(file)
                    val owner = resolved.single { it.file == file }
                    changes[target] = applyChanges(target.text, entries.associate {
                        checkNotNull(repo.rawKey(owner, it.key)) { "Key 与文件命名空间不匹配：${it.key}" } to it.value
                    }, repo.keyStyle)
                }
                locales += locale
            } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                failed[locale] = e.message ?: "保存失败"
            }
        }
        commit(project, "i18n: 保存翻译", changes, locales, failed)
    }

    /** 用实际 key 风格生成新文本，冲突时保留原文并向调用者返回错误。 */
    internal fun applyChanges(
        sourceText: String,
        changes: Map<String, String>,
        keyStyle: KeyStyle = KeyStyle.NESTED,
    ): String {
        val writer = JsonLocaleWriter(sourceText, keyStyle)
        for ((key, value) in changes) writer.setValue(key, value)
        return writer.render()
    }

    /** 从真实译文值判断缺失，空字符串也需要补全。 */
    fun missingFor(repo: LocaleRepository, sourceLocale: String, targetLocales: List<String>): Map<String, List<String>> {
        val source = repo.valuesOf(sourceLocale)
        return targetLocales.associateWith { locale ->
            val values = repo.valuesOf(locale)
            source.keys.filter { values[it].isNullOrBlank() }
        }
    }

    /** 遍历全部语言文件删除 key，准备失败时不提交部分结果。 */
    fun deleteKeys(project: Project, repo: LocaleRepository, keysToDelete: List<String>): Result = onEventThread {
        val changes = linkedMapOf<Document, String>()
        val locales = linkedSetOf<String>()
        val failed = linkedMapOf<String, String>()
        val files = try {
            validatedFiles(repo, keysToDelete)
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            return@onEventThread Result(failed = mapOf("语言包" to (e.message ?: "归属不明确")))
        }
        for (resolved in files) {
            try {
                val target = document(resolved.file)
                val writer = JsonLocaleWriter(target.text, repo.keyStyle)
                val rawKeys = keysToDelete.mapNotNull { repo.rawKey(resolved, it) }
                if (rawKeys.none { it in writer.read() }) continue
                rawKeys.forEach { writer.remove(it) }
                check(rawKeys.none { it in writer.read() }) { "Key 存在多个平铺或嵌套定义，未提交删除" }
                changes[target] = writer.render()
                locales += resolved.locale
            } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                failed[resolved.file.path] = e.message ?: "删除失败"
            }
        }
        commit(project, "i18n: 删除 key", changes, locales, failed)
    }

    /** 同时重命名所有语言文件和源码引用，目标冲突时整个操作不写入。 */
    fun renameKey(
        project: Project,
        repo: LocaleRepository,
        oldKey: String,
        newKey: String,
        sourceUpdates: Map<Document, String> = emptyMap(),
    ): Result = onEventThread {
        if (oldKey == newKey) return@onEventThread Result()
        val changes = linkedMapOf<Document, String>()
        val locales = linkedSetOf<String>()
        val failed = linkedMapOf<String, String>()
        val files = try {
            validatedFiles(repo, listOf(oldKey))
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            return@onEventThread Result(failed = mapOf("语言包" to (e.message ?: "归属不明确")))
        }
        for (resolved in files) {
            try {
                val target = document(resolved.file)
                val writer = JsonLocaleWriter(target.text, repo.keyStyle)
                val values = writer.read()
                val rawOld = repo.rawKey(resolved, oldKey)
                val rawNew = repo.rawKey(resolved, newKey)
                require(rawNew == null || rawNew !in values) { "目标 key 已存在：$newKey" }
                if (rawOld == null || rawOld !in values) continue
                if (rawNew != null) {
                    writer.rename(rawOld, rawNew)
                } else {
                    val destinations = files.filter { it.locale == resolved.locale && it.root == resolved.root &&
                        repo.rawKey(it, newKey) != null }
                    val destination = destinations.singleOrNull()
                        ?: error("目标命名空间在当前目录中不存在或不唯一：$newKey")
                    val destDocument = document(destination.file)
                    val destWriter = JsonLocaleWriter(changes[destDocument] ?: destDocument.text, repo.keyStyle)
                    val destKey = checkNotNull(repo.rawKey(destination, newKey))
                    require(destKey !in destWriter.read()) { "目标 key 已存在：$newKey" }
                    destWriter.setValue(destKey, values.getValue(rawOld))
                    changes[destDocument] = destWriter.render()
                    writer.remove(rawOld)
                }
                // AUTO 可能同时包含同名平铺字段与嵌套路径，不能只移走其中一个定义。
                check(rawOld !in writer.read()) { "原 Key 存在多个平铺或嵌套定义，未提交重命名：$oldKey" }
                changes[target] = writer.render()
                locales += resolved.locale
            } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                failed[resolved.file.path] = e.message ?: "重命名失败"
            }
        }
        if (changes.isEmpty() && failed.isEmpty()) failed[oldKey] = "未找到原 key"
        changes.putAll(sourceUpdates)
        commit(project, "i18n: 重命名 key", changes, locales, failed)
    }

    /** 删除和重命名前校验最新文档归属，阻止跨模块修改碰巧同名的 Key。 */
    private fun validatedFiles(repo: LocaleRepository, keys: List<String>): List<ResolvedLocale> {
        val files = repo.resolvedFiles()
        val values = files.associate { file -> file.file to repo.logicalValues(file,
            JsonLocaleWriter(document(file.file).text, repo.keyStyle).read()) }
        for ((locale, members) in files.groupBy { it.locale }) {
            repo.validateUnique(locale, members.associate { it.file to values.getValue(it.file) })
        }
        for (key in keys) {
            val owners = files.filter { key in values.getValue(it.file) }
            check(owners.map { it.root }.distinct().size <= 1) {
                "Key「$key」分属不同语言包目录，未修改任何文件：${owners.joinToString { it.file.path }}"
            }
        }
        return files
    }

    /** 使用 IDE 最新的未保存文本，避免磁盘旧值覆盖编辑器改动。 */
    private fun document(file: File): Document {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
            ?: error("找不到文件：${file.path}")
        return FileDocumentManager.getInstance().getDocument(virtualFile)
            ?: error("无法读取文档：${file.path}")
    }

    /** 文档修改支持统一撤销，保存须等待异步写盘完成，不能仅凭文档状态报告成功。 */
    private fun commit(
        project: Project,
        title: String,
        changes: Map<Document, String>,
        locales: Set<String>,
        failures: Map<String, String>,
    ): Result {
        if (failures.isNotEmpty()) return Result(failed = failures)
        if (changes.isEmpty()) return Result()
        if (changes.keys.any { !it.isWritable || FileDocumentManager.getInstance().getFile(it)?.isWritable == false }) return Result(failed = mapOf(title to "存在只读文档，未保存任何修改"))
        val originals = changes.keys.associateWith { it.text }
        var failure: String? = null
        WriteCommandAction.runWriteCommandAction(project, title, null, Runnable {
            try {
                for ((document, text) in changes) document.setText(text)
            } catch (e: Exception) {
                originals.forEach { (document, text) -> document.setText(text) }
                if (e is com.intellij.openapi.progress.ProcessCanceledException) throw e
                failure = e.message ?: "写入失败，已恢复原文"
            }
        })
        failure?.let { return Result(failed = mapOf(title to it)) }
        val manager = FileDocumentManager.getInstance()
        val saveFailures = linkedMapOf<String, String>()
        for (document in changes.keys) {
            val file = manager.getFile(document)
            try {
                checkNotNull(file) { "文档未关联文件" }
                manager.saveDocument(document)
                // IDE 可能先清除未保存标记再异步写盘，只等待本次修改的文件，避免误保存其他文档。
                ManagingFS.getInstance().flushPendingUpdates(file)
                check(!manager.isDocumentUnsaved(document)) { "IDE 未完成保存，修改仍保留在编辑器中" }
            } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                saveFailures[file?.path ?: title] = e.message ?: "文件写入失败"
            }
        }
        return if (saveFailures.isEmpty()) Result(succeeded = locales.toList()) else Result(failed = saveFailures)
    }

    /** 文档读取和修改都在事件线程完成，避免后台任务与用户编辑互相覆盖。 */
    private fun onEventThread(action: () -> Result): Result {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) return action()
        var result = Result()
        application.invokeAndWait { result = action() }
        return result
    }
}
