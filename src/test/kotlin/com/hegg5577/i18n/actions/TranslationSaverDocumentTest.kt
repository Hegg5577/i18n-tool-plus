package com.hegg5577.i18n.actions

import com.hegg5577.i18n.locale.KeyStyle
import com.hegg5577.i18n.locale.LocaleRepository
import com.hegg5577.i18n.locale.TranslationSaver
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files

/** 验证实际文档写入保留人工内容，并在准备失败时不产生部分修改。 */
class TranslationSaverDocumentTest : BasePlatformTestCase() {
    private lateinit var root: File

    /** 每个用例使用独立的物理目录，保证 VFS 与磁盘读取指向同一文件。 */
    override fun setUp() {
        super.setUp()
        root = Files.createTempDirectory("i18n-saver").toFile()
    }

    /** 测试数据用后清理，避免影响其它项目的语言包扫描。 */
    override fun tearDown() {
        try {
            root.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    /** 任一语言出现路径冲突时，先前准备成功的语言也不能被写入。 */
    fun testConflictPreventsAllWrites() {
        val en = write("en.json", "{}")
        val zh = write("zh.json", """{"common":"通用"}""")
        val repo = LocaleRepository(root, "locales", "{locale}.json")

        val result = TranslationSaver.save(project, repo, "en", linkedMapOf(
            "en" to mapOf("common.ok" to "OK"),
            "zh" to mapOf("common.ok" to "确定"),
        ))

        assertFalse(result.isSuccess)
        assertTrue(result.failed.containsKey("zh"))
        assertEquals("{}", en.readText())
        assertEquals("""{"common":"通用"}""", zh.readText())
    }

    /** 保存前必须检查最新 Document，不能覆盖尚未保存的人工译文。 */
    fun testOnlyMissingPreservesUnsavedTranslation() {
        val file = write("zh.json", "{}")
        val document = document(file)
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText("""{"manual":"人工译文"}""")
        }
        val repo = LocaleRepository(root, "locales", "{locale}.json")

        val result = TranslationSaver.save(project, repo, "en", mapOf(
            "zh" to mapOf("manual" to "机器译文", "missing" to "新译文"),
        ), onlyMissing = true)

        assertTrue(result.isSuccess)
        assertEquals(mapOf("manual" to "人工译文", "missing" to "新译文"), repo.valuesOf("zh"))
    }

    /** 默认批量翻译读取未保存文档，补齐空白但保留等待期间填写的人工译文。 */
    fun testBatchFillsEmptyAndPreservesUnsavedTranslation() {
        val file = write("zh.json", "{}")
        val target = document(file)
        WriteCommandAction.runWriteCommandAction(project) {
            target.setText("""{"manual":"人工译文","empty":"","blank":"  "}""")
        }
        val repo = LocaleRepository(root, "locales", "{locale}.json")
        val edits = mapOf("zh" to mapOf("manual" to "机器译文", "empty" to "填充", "blank" to "补全", "missing" to "新增"))

        val result = BatchTranslateAction().writeBack(project, repo, "en", edits, overrideExisting = false)

        assertTrue(result.isSuccess)
        assertEquals(mapOf("manual" to "人工译文", "empty" to "填充", "blank" to "补全", "missing" to "新增"),
            repo.valuesOf("zh"))
    }

    /** 明确开启覆盖后允许替换已有译文，同时保留本次任务之外的目标 key。 */
    fun testBatchOverwriteReplacesExistingTranslation() {
        write("zh.json", """{"manual":"人工译文","extra":"保留"}""")
        val repo = LocaleRepository(root, "locales", "{locale}.json")

        val result = BatchTranslateAction().writeBack(project, repo, "en",
            mapOf("zh" to mapOf("manual" to "机器译文")), overrideExisting = true)

        assertTrue(result.isSuccess)
        assertEquals(mapOf("manual" to "机器译文", "extra" to "保留"), repo.valuesOf("zh"))
    }

    /** 兼容原有仅补缺失的保存接口，不显式开启填空时保留已有空值。 */
    fun testOnlyMissingStillPreservesExistingEmptyValue() {
        write("zh.json", """{"empty":""}""")
        val repo = LocaleRepository(root, "locales", "{locale}.json")
        val result = TranslationSaver.save(project, repo, "en",
            mapOf("zh" to mapOf("empty" to "填充", "missing" to "新增")), onlyMissing = true)

        assertTrue(result.isSuccess)
        assertEquals(mapOf("empty" to "", "missing" to "新增"), repo.valuesOf("zh"))
    }

    /** 目标 key 冲突时，语言包和已准备的源码更新都必须保持原样。 */
    fun testRenameConflictDoesNotWriteSource() {
        val localeFile = write("en.json", """{"old":"Old","new":"New"}""")
        val sourceFile = File(root, "source.js").apply { writeText("t('old')") }
        val source = document(sourceFile)
        val repo = LocaleRepository(root, "locales", "{locale}.json")

        val result = TranslationSaver.renameKey(project, repo, "old", "new", mapOf(source to "t('new')"))

        assertFalse(result.isSuccess)
        assertEquals("t('old')", source.text)
        assertEquals("t('old')", sourceFile.readText())
        assertEquals("""{"old":"Old","new":"New"}""", localeFile.readText())
    }

    /** 成功重命名时语言包必须移除旧 key，并提交对应源码更新。 */
    fun testRenameMovesTranslationAndUpdatesSource() {
        write("en.json", """{"old":"Hello"}""")
        write("zh.json", """{"old":"你好"}""")
        val sourceFile = File(root, "source.js").apply { writeText("t('old')") }
        val source = document(sourceFile)
        val repo = LocaleRepository(root, "locales", "{locale}.json")

        val result = TranslationSaver.renameKey(project, repo, "old", "new", mapOf(source to "t('new')"))

        assertTrue(result.isSuccess)
        assertEquals(mapOf("new" to "Hello"), repo.valuesOf("en"))
        assertEquals(mapOf("new" to "你好"), repo.valuesOf("zh"))
        assertEquals("t('new')", source.text)
        assertEquals("t('new')", sourceFile.readText())
    }

    /** 多文件布局新增译文应跟随源语言的 namespace，已有译文在原文件更新。 */
    fun testSaveUsesKeyOwnershipAcrossFiles() {
        write("en/common.json", """{"title":"Title"}""")
        val common = write("zh/common.json", "{}")
        val page = write("zh/page.json", """{"page":"旧值"}""")
        val repo = LocaleRepository(root, "locales", "{locale}/{namespace}.json")

        val result = TranslationSaver.save(project, repo, "en", mapOf(
            "zh" to mapOf("title" to "标题", "page" to "新值"),
        ))

        assertTrue(result.isSuccess)
        assertEquals(mapOf("title" to "标题"), repo.valuesOfFile(common))
        assertPersisted(repo, page, mapOf("page" to "新值"))
    }

    /** AUTO 的同名平铺和嵌套定义不能只迁移一个，遇到歧义时源码与语言包均保留。 */
    fun testRenameRejectsShadowedAutoKey() {
        val file = write("en.json", """{"old.key":"Flat","old":{"key":"Nested"}}""")
        val sourceFile = File(root, "source.js").apply { writeText("t('old.key')") }
        val source = document(sourceFile)
        val repo = LocaleRepository(root, "locales", "{locale}.json", KeyStyle.AUTO)
        val before = file.readText()
        val result = TranslationSaver.renameKey(project, repo, "old.key", "new.key",
            mapOf(source to "t('new.key')"))

        assertFalse(result.isSuccess)
        assertEquals(before, file.readText())
        assertEquals("t('old.key')", source.text)
    }

    /** 构造真实文件，使所有保存调用都经过正式的语言包定位入口。 */
    private fun write(name: String, text: String): File = File(root, "locales/$name").apply {
        parentFile.mkdirs()
        writeText(text)
    }

    /** 获取最新编辑器文档，模拟磁盘值与未保存内容不同的情况。 */
    private fun document(file: File): Document {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)!!
        return FileDocumentManager.getInstance().getDocument(virtualFile)!!
    }

    /** 保存逻辑 Key 时剥离命名空间，其他文件同名字段不能被覆盖。 */
    fun testNamespaceSaveRenameAndDeleteStayInTheirFile() {
        write("en/common.json", """{"title":"Common"}""")
        write("en/page.json", """{"title":"Page"}""")
        val common = write("zh/common.json", """{"title":"通用"}""")
        write("zh/page.json", "{}")
        val repo = LocaleRepository(root, "locales", "{locale}/{namespace}.json", namespaceEnabled = true)
        assertTrue(TranslationSaver.save(project, repo, "en", mapOf("zh" to mapOf("page:title" to "页面"))).isSuccess)
        assertEquals("""{"title":"通用"}""", common.readText())
        assertEquals("页面", repo.valuesOf("zh")["page:title"])
        assertFalse(repo.valuesOfFile(File(root, "locales/zh/page.json")).containsKey("page:title"))
        assertTrue(TranslationSaver.renameKey(project, repo, "page:title", "page:heading").isSuccess)
        assertEquals("Page", repo.valuesOf("en")["page:heading"])
        assertEquals("Common", repo.valuesOf("en")["common:title"])
        assertTrue(TranslationSaver.deleteKeys(project, repo, listOf("page:heading")).isSuccess)
        assertFalse(repo.valuesOf("en").containsKey("page:heading"))
        assertEquals("通用", repo.valuesOf("zh")["common:title"])
    }

    /** 跨命名空间改名应迁移到同目录的对应文件，并保留其他 Key。 */
    fun testNamespaceMoveUsesDestinationFile() {
        write("en/common.json", """{"title":"Title"}""")
        write("en/page.json", """{"keep":"Keep"}""")
        val repo = LocaleRepository(root, "locales", "{locale}/{namespace}.json", namespaceEnabled = true)
        val result = TranslationSaver.renameKey(project, repo, "common:title", "page:heading")
        assertTrue(result.isSuccess)
        assertPersisted(repo, File(root, "locales/en/common.json"), emptyMap())
        assertEquals(mapOf("keep" to "Keep", "heading" to "Title"),
            repo.valuesOfFile(File(root, "locales/en/page.json")))
    }

    /** 任一语言缺少目标命名空间文件时，整个迁移不能写入。 */
    fun testNamespaceMoveFailurePreservesAllFiles() {
        val en = write("en/common.json", """{"title":"Title"}""")
        val page = write("en/page.json", "{}")
        val zh = write("zh/common.json", """{"title":"标题"}""")
        val repo = LocaleRepository(root, "locales", "{locale}/{namespace}.json", namespaceEnabled = true)
        val result = TranslationSaver.renameKey(project, repo, "common:title", "page:title")
        assertFalse(result.isSuccess)
        assertEquals("{}", page.readText())
        assertEquals("""{"title":"Title"}""", en.readText())
        assertEquals("""{"title":"标题"}""", zh.readText())
    }

    /** 未保存的同名字段由命名空间隔离，补缺不能覆盖已有人工译文。 */
    fun testNamespaceOnlyMissingUsesUnsavedValues() {
        write("en/common.json", """{"title":"Title"}""")
        write("en/page.json", """{"title":"Page"}""")
        val common = write("zh/common.json", "{}")
        write("zh/page.json", "{}")
        WriteCommandAction.runWriteCommandAction(project) {
            document(common).setText("""{"title":"人工译文"}""")
        }
        val repo = LocaleRepository(root, "locales", "{locale}/{namespace}.json", namespaceEnabled = true)
        val result = TranslationSaver.save(project, repo, "en", mapOf("zh" to mapOf(
            "common:title" to "机器译文", "page:title" to "页面")), onlyMissing = true, fillEmpty = true)
        assertTrue(result.isSuccess)
        assertEquals("人工译文", com.google.gson.JsonParser.parseString(document(common).text)
            .asJsonObject.get("title").asString)
        assertEquals("页面", repo.valuesOf("zh")["page:title"])
    }

    /** 多目录下已存在的译文归属不匹配时，保持所有文件原样。 */
    fun testMultiRootSaveRejectsWrongTargetOwner() {
        val first = File(root, "a/en/common.json").apply { parentFile.mkdirs(); writeText("""{"title":"Title"}""") }
        val second = File(root, "b/zh/common.json").apply { parentFile.mkdirs(); writeText("""{"title":"人工译文"}""") }
        val repo = LocaleRepository(root, pathMatcher = "{locale}/{namespace}.json", localesDirs = listOf("a", "b"),
            namespaceEnabled = true)
        val result = TranslationSaver.save(project, repo, "en", mapOf("zh" to mapOf("common:title" to "机器译文")))
        assertFalse(result.isSuccess)
        assertEquals("""{"title":"Title"}""", first.readText())
        assertEquals("""{"title":"人工译文"}""", second.readText())
    }

    /** 多目录同命名空间的不同 Key 应分别保存，不能按文件顺序选目标。 */
    fun testMultiRootSaveFollowsEachSourceFile() {
        write("a/en/common.json", """{"first":"First"}""")
        val first = write("a/zh/common.json", "{}")
        write("b/en/common.json", """{"second":"Second"}""")
        val second = write("b/zh/common.json", "{}")
        val repo = LocaleRepository(root, pathMatcher = "{locale}/{namespace}.json",
            localesDirs = listOf("locales/a", "locales/b"), namespaceEnabled = true)
        val result = TranslationSaver.save(project, repo, "en", mapOf("zh" to mapOf(
            "common:first" to "第一", "common:second" to "第二")))
        assertTrue(result.isSuccess)
        assertEquals(mapOf("first" to "第一"), repo.valuesOfFile(first))
        assertPersisted(repo, second, mapOf("second" to "第二"))
    }

    /** 连续修改多文件必须立即落盘，不能只更新文档后误报成功。 */
    fun testRepeatedMultiFileSavesReachDisk() {
        repeat(200) { index ->
            write("round$index/en/common.json", """{"one":"One"}""")
            write("round$index/en/page.json", """{"two":"Two"}""")
            val first = write("round$index/zh/common.json", "{}")
            val second = write("round$index/zh/page.json", "{}")
            val repo = LocaleRepository(root, "locales/round$index", "{locale}/{namespace}.json", namespaceEnabled = true)
            val result = TranslationSaver.save(project, repo, "en", mapOf("zh" to mapOf("common:one" to "First", "page:two" to "Second")))
            assertTrue(result.toString(), result.isSuccess)
            for ((file, expected) in listOf(first to mapOf("one" to "First"), second to mapOf("two" to "Second"))) {
                val manager = FileDocumentManager.getInstance()
                val virtual = LocalFileSystem.getInstance().findFileByIoFile(file)!!
                val doc = manager.getCachedDocument(virtual)!!
                val async = virtual.fileSystem as? com.intellij.openapi.vfs.newvfs.AsyncableFileSystem
                assertFalse("成功返回时不能仍有待写入任务：$file", async?.hasUnfinishedTasksFor(virtual) == true)
                assertEquals("round=$index, document=${doc.text}, unsaved=${manager.isDocumentUnsaved(doc)}, " +
                    "docStamp=${doc.modificationStamp}, fileStamp=${virtual.modificationStamp}", expected, repo.valuesOfFile(file))
            }
        }
    }

    /** 失败时保留文档与文件版本信息，避免刷新文件改变现场。 */
    private fun assertPersisted(repo: LocaleRepository, file: File, expected: Map<String, String>) {
        val virtual = LocalFileSystem.getInstance().findFileByIoFile(file)!!
        val manager = FileDocumentManager.getInstance()
        val doc = manager.getCachedDocument(virtual)!!
        assertEquals("file=$file; doc=${doc.text}; unsaved=${manager.isDocumentUnsaved(doc)}; " +
            "docStamp=${doc.modificationStamp}; fileStamp=${virtual.modificationStamp}", expected, repo.valuesOfFile(file))
    }

    /** 保存被平台拒绝时不能误报成功，未写入的译文仍须保留供用户重试。 */
    fun testSaveVetoReturnsFailureAndKeepsEditedDocument() {
        val file = write("zh.json", "{}")
        val target = document(file)
        val vetoer = object : com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer() {
            /** 仅拒绝本用例的文件，模拟平台或其他扩展阻止保存。 */
            override fun maySaveDocument(document: Document, isSaveExplicit: Boolean): Boolean = document !== target
        }
        val disposable = com.intellij.openapi.util.Disposer.newDisposable()
        com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer.EP_NAME.point.registerExtension(vetoer, disposable)
        try {
            val repo = LocaleRepository(root, "locales", "{locale}.json")
            val result = TranslationSaver.save(project, repo, "en", mapOf("zh" to mapOf("title" to "Title")))
            assertFalse(result.isSuccess)
            assertTrue(result.failed.keys.any { it.endsWith("zh.json") })
            assertEquals("{}", file.readText())
            assertTrue(target.text.contains("Title"))
            assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(target))
        } finally {
            com.intellij.openapi.util.Disposer.dispose(disposable)
            FileDocumentManager.getInstance().reloadFromDisk(target)
        }
    }

    /** 提取字符串等入口可能已持有写锁，等待异步写盘也必须正常完成。 */
    fun testSaveInsideWriteCommandReachesDisk() {
        val file = write("zh.json", "{}")
        val repo = LocaleRepository(root, "locales", "{locale}.json")
        WriteCommandAction.runWriteCommandAction(project) {
            val result = TranslationSaver.save(project, repo, "en", mapOf("zh" to mapOf("title" to "Title")))
            assertTrue(result.toString(), result.isSuccess)
            assertPersisted(repo, file, mapOf("title" to "Title"))
        }
    }

    /** 不同模块各有部分语言时，删除也不能把碰巧同名的 Key 当作同一组。 */
    fun testDeleteRejectsKeysAcrossRoots() {
        val first = write("a/en/common.json", """{"title":"Title"}""")
        val second = write("b/zh/common.json", """{"title":"标题"}""")
        val repo = LocaleRepository(root, pathMatcher = "{locale}/{namespace}.json",
            localesDirs = listOf("locales/a", "locales/b"), namespaceEnabled = true)
        val result = TranslationSaver.deleteKeys(project, repo, listOf("common:title"))
        assertFalse(result.isSuccess)
        assertEquals("""{"title":"Title"}""", first.readText())
        assertEquals("""{"title":"标题"}""", second.readText())
    }

}
