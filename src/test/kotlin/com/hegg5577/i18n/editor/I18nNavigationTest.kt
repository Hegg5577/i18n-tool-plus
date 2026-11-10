package com.hegg5577.i18n.editor

import com.hegg5577.i18n.actions.I18nRenameKeyHandler
import com.hegg5577.i18n.locale.KeyStyle
import com.hegg5577.i18n.settings.I18nContext
import com.hegg5577.i18n.settings.I18nSettings
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import java.io.File

/** 使用真实磁盘语言包验证平台注册、定义定位、补全、引用搜索及重命名事务。 */
class I18nNavigationTest : BasePlatformTestCase() {
    /** 路径匹配器需要真实目录，使用物理项目和文件夹而非内存 VFS。 */
    override fun createMyFixture(): CodeInsightTestFixture {
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val fixture = factory.createFixtureBuilder(getTestName(false)).fixture
        return factory.createCodeInsightFixture(fixture, factory.createTempDirTestFixture())
    }

    /** 每个用例共享相同语言包结构，源码扫描只覆盖本用例的 src 目录。 */
    override fun setUp() {
        super.setUp()
        val projectRoot = File(requireNotNull(project.basePath)).toPath()
        val root = File(myFixture.tempDirPath)
        WriteCommandAction.runWriteCommandAction(project) {
            val module = com.intellij.openapi.module.ModuleManager.getInstance(project)
                .newModule(File(root, "test.iml").path, "EMPTY_MODULE")
            com.intellij.testFramework.PsiTestUtil.addContentRoot(module,
                requireNotNull(com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root)))
        }
        I18nSettings.getInstance(project).loadState(I18nSettings.State(
            localesPath = projectRoot.relativize(root.resolve("locales").toPath()).toString(),
            pathMatcher = "{locale}.json",
            sourceDirs = mutableListOf(root.resolve("src").absolutePath),
            displayLocales = mutableListOf("zh"),
        ))
        myFixture.addFileToProject("locales/en.json", """{"common":{"ok":"OK","cancel":"Cancel"}}""")
        myFixture.addFileToProject("locales/zh.json", """{"common":{"ok":"确定","cancel":"取消"}}""")
    }

    /** 注册的引用应指向源语言实际属性，同时与另一语言的同一 Key 关联。 */
    fun testRegisteredReferenceResolvesToSourceDefinition() {
        val file = myFixture.configureByText("calls.js", "t('common.<caret>ok');")
        val reference = file.findReferenceAt(myFixture.caretOffset)
        assertTrue(reference is I18nKeyReference)
        val definition = I18nKeySupport.definitionAt(requireNotNull(reference?.resolve()))
        assertEquals("common.ok", definition?.key)
        assertEquals("en", definition?.locale)
        val zh = I18nKeySupport.definitions(I18nContext.of(project), project)
            .single { it.key == "common.ok" && it.locale == "zh" }
        assertTrue(requireNotNull(reference).isReferenceTo(zh.property))
    }

    /** 首选语言的译文应展示在候选旁边，选择 Key 后保留完整调用语法。 */
    fun testCompletionIncludesTranslationAndKeepsQuotes() {
        val file = myFixture.configureByText("complete.ts", "t('common.<caret>');")
        val variants = I18nKeyReference.variants(file)
        val candidate = variants.single { it.lookupString == "common.ok" }
        val presentation = LookupElementPresentation()
        candidate.renderElement(presentation)
        assertEquals("zh: 确定", presentation.typeText)
        val lookup = myFixture.completeBasic()
        assertNotNull(lookup)
        requireNotNull(myFixture.lookup).currentItem = requireNotNull(lookup).single { it.lookupString == "common.ok" }
        myFixture.finishLookup('\n')
        assertEquals("t('common.ok');", myFixture.editor.document.text)
    }

    /** 空字符串里也应出现候选，在 Key 中间选择候选时不能保留旧后缀。 */
    fun testCompletionInEmptyStringAndMiddleOfKey() {
        myFixture.configureByText("empty.js", "t('<caret>');")
        assertTrue(requireNotNull(myFixture.completeBasic()).any { it.lookupString == "common.ok" })
        requireNotNull(myFixture.lookup).hideLookup(true)
        myFixture.configureByText("middle.js", "t('common.o<caret>ldSuffix', { count: 1 });")
        val variants = myFixture.completeBasic()
        if (myFixture.lookup != null) {
            myFixture.lookup!!.currentItem = requireNotNull(variants).single { it.lookupString == "common.ok" }
            myFixture.finishLookup('\n')
        }
        assertEquals("t('common.ok', { count: 1 });", myFixture.editor.document.text)
    }

    /** 全局查找只返回配置目录中的翻译调用，并遵守调用者指定的文件范围。 */
    fun testReferencesSearchHonorsScopeAndSkipsExamples() {
        val first = myFixture.addFileToProject("src/a.js",
            "t('common.ok'); const plain = 'common.ok'; // t('common.ok')")
        val second = myFixture.addFileToProject("src/b.ts", "translate('common.ok');")
        myFixture.addFileToProject("outside/ignored.js", "t('common.ok');")
        myFixture.addFileToProject("src/node_modules/ignored.js", "t('common.ok');")
        val target = requireNotNull(I18nKeySupport.resolve(first, "common.ok"))

        // 原生平台也会搜索已注册的引用，调用者必须明确传入希望搜索的目录范围。
        val sourceScope = com.intellij.psi.search.GlobalSearchScopesCore.directoryScope(project, first.virtualFile.parent, true)
        val all = ReferencesSearch.search(target, sourceScope, true).findAll()
            .filterIsInstance<I18nKeyReference>()
        assertEquals(setOf(first.virtualFile, second.virtualFile), all.map { it.element.containingFile.virtualFile }.toSet())
        assertEquals(2, all.size)
        val local = ReferencesSearch.search(target, GlobalSearchScope.fileScope(first), true).findAll()
            .filterIsInstance<I18nKeyReference>()
        assertEquals(1, local.size)
    }

    /** 项目启动扩展必须实际注册，并从项目文件读取 YAML 覆盖设置。 */
    fun testYamlStartupIsRegisteredAndLoadsConfiguration() {
        val activity = com.intellij.openapi.extensions.ExtensionPointName
            .create<com.intellij.openapi.startup.ProjectActivity>("com.intellij.postStartupActivity")
            .findExtensionOrFail(com.hegg5577.i18n.startup.I18nProjectOpenListener::class.java)
        val config = File(requireNotNull(project.basePath), ".i18nrc.yml")
        val previous = config.takeIf { it.exists() }?.readText()
        config.parentFile.mkdirs()
        try {
            config.writeText("sourceLocale: ja\nnamespaceEnabled: true\nnamespaceDelimiter: '##'\n")
            kotlinx.coroutines.runBlocking { activity.execute(project) }
            assertEquals("ja", I18nContext.of(project).sourceLocale)
            assertEquals("##", I18nContext.of(project).namespaceDelimiter)
        } finally {
            if (previous == null) config.delete() else config.writeText(previous)
        }
    }

    /** 原生重命名只接管 Key，普通字符串仍交由原语言插件处理。 */
    fun testRenameHandlerAvailabilityIsLimitedToKeys() {
        val file = myFixture.configureByText("rename.js", "t('common.<caret>ok'); const label = 'common.ok';")
        val context = SimpleDataContext.builder().add(CommonDataKeys.PSI_FILE, file)
            .add(CommonDataKeys.EDITOR, myFixture.editor).build()
        val handler = I18nRenameKeyHandler()
        assertTrue(handler.isAvailableOnDataContext(context))
        myFixture.editor.caretModel.moveToOffset(file.text.lastIndexOf("common.ok") + 2)
        assertFalse(handler.isAvailableOnDataContext(context))
    }

    /** 原生入口应同步更新所有语言与源码引用，普通字符串保持不变。 */
    fun testRenameUpdatesLocalesAndSourceTogether() {
        val source = myFixture.addFileToProject("src/calls.js", "t('common.ok'); const label = 'common.ok';")
        val result = I18nRenameKeyHandler().renameKey(project, "common.ok", "actions.confirm")
        assertTrue(result.isSuccess)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals("t('actions.confirm'); const label = 'common.ok';", source.text)
        val repo = requireNotNull(I18nContext.of(project).repository())
        assertEquals("OK", repo.valuesOf("en")["actions.confirm"])
        assertEquals("确定", repo.valuesOf("zh")["actions.confirm"])
        assertFalse(repo.valuesOf("en").containsKey("common.ok"))
        assertFalse(repo.valuesOf("zh").containsKey("common.ok"))
    }

    /** 目标 Key 已存在时语言包和源码都不能发生部分修改。 */
    fun testRenameConflictPreservesEveryFile() {
        val source = myFixture.addFileToProject("src/calls.js", "t('common.ok');")
        val repo = requireNotNull(I18nContext.of(project).repository())
        val before = repo.valuesOf("en")
        val result = I18nRenameKeyHandler().renameKey(project, "common.ok", "common.cancel")
        assertFalse(result.isSuccess)
        assertEquals(before, repo.valuesOf("en"))
        assertEquals("t('common.ok');", source.text)
    }

    /** 平铺与 AUTO 应定位真实字段，未保存的译文修改也能立即用于候选展示。 */
    fun testFlatAndAutoDefinitionsUseLatestPsi() {
        val file = requireNotNull(psiManager.findFile(requireNotNull(myFixture.tempDirFixture.getFile("locales/en.json"))))
        val document = requireNotNull(PsiDocumentManager.getInstance(project).getDocument(file))
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText("""{"common.ok":"Flat","common":{"ok":"Nested"}}""")
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        I18nSettings.getInstance(project).state.keyStyle = "auto"
        val target = requireNotNull(I18nKeySupport.resolve(file, "common.ok"))
        assertEquals("common.ok", target.name)
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText("""{"common.ok":"Unsaved"}""")
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        val context = I18nContext.of(project).copy(keyStyle = KeyStyle.FLAT)
        assertEquals("Unsaved", I18nKeySupport.definitions(context, project)
            .single { it.locale == "en" && it.key == "common.ok" }.value)
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
    }

    /** 同一语言的多个文件有同名 Key 时，不提供可能跳错或改错的目标。 */
    fun testAmbiguousDefinitionsAreNotGuessed() {
        I18nSettings.getInstance(project).state.pathMatcher = "{locale}/{namespace}.json"
        myFixture.addFileToProject("locales/en/common.json", """{"title":"Common"}""")
        myFixture.addFileToProject("locales/en/page.json", """{"title":"Page"}""")
        val file = myFixture.configureByText("ambiguous.js", "t('title');")
        assertNull(I18nKeySupport.resolve(file, "title"))
        assertFalse(I18nKeyReference.variants(file).any { it.lookupString == "title" })
    }
    /** 从扫描范围之外发起改名时必须保留语言包，避免留下当前调用的旧 Key。 */
    fun testRenameOutsideConfiguredSourceScopeIsRejected() {
        val source = myFixture.addFileToProject("outside/calls.js", "t('common.ok');")
        val origin = I18nKeySupport.usages(source).single().literal
        val repo = requireNotNull(I18nContext.of(project).repository())
        val before = repo.valuesOf("en")
        try {
            I18nRenameKeyHandler().renameKey(project, "common.ok", "actions.confirm", origin)
            fail("当前调用不在扫描范围时应拒绝重命名")
        } catch (exception: IllegalStateException) {
            assertTrue(exception.message.orEmpty().contains("扫描范围"))
        }
        assertEquals(before, repo.valuesOf("en"))
        assertEquals("t('common.ok');", source.text)
    }

    /** 平台调用单条引用改名时也必须只替换 Key 内容，不能改动参数中的相同文本。 */
    fun testReferenceRenameOnlyChangesStringContent() {
        val file = myFixture.configureByText("direct.js", "t('common.<caret>ok', { value: 'common.ok' });")
        val reference = requireNotNull(file.findReferenceAt(myFixture.caretOffset))
        WriteCommandAction.runWriteCommandAction(project) {
            reference.handleElementRename("actions.confirm")
        }
        assertEquals("t('actions.confirm', { value: 'common.ok' });", myFixture.editor.document.text)
    }


    /** 自定义分隔符贯通补全、跳转和源码改名，不影响另一命名空间的同名字段。 */
    fun testNamespaceCompletionNavigationAndRename() {
        val state = I18nSettings.getInstance(project).state
        state.pathMatcher = "{locale}/{namespace}.json"
        state.namespaceEnabled = true
        state.namespaceDelimiter = "##"
        myFixture.addFileToProject("locales/en/common.json", """{"title":"Common"}""")
        myFixture.addFileToProject("locales/zh/common.json", """{"title":"通用"}""")
        myFixture.addFileToProject("locales/en/page.json", """{"title":"Page"}""")
        myFixture.addFileToProject("locales/zh/page.json", """{"title":"页面"}""")
        val file = myFixture.configureByText("namespaced.js", "t('common##<caret>');")
        val usage = I18nKeySupport.usageAt(file, myFixture.caretOffset, allowEmpty = true)
        assertEquals("common##", usage?.key)
        assertTrue(I18nKeyReference.variants(file).any { it.lookupString == "common##title" })
        val source = myFixture.addFileToProject("src/namespace.js", "t('common##title'); t('page##title');")
        val target = I18nKeySupport.resolve(source, "common##title")
        assertNotNull(target)
        assertEquals("common.json", requireNotNull(target).containingFile.name)
        val result = I18nRenameKeyHandler().renameKey(project, "common##title", "common##heading")
        assertTrue(result.isSuccess)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals("t('common##heading'); t('page##title');", source.text)
        val repo = requireNotNull(I18nContext.of(project).repository())
        assertEquals("Common", repo.valuesOf("en")["common##heading"])
        assertEquals("Page", repo.valuesOf("en")["page##title"])
    }

}
