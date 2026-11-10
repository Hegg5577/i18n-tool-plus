package com.hegg5577.i18n.actions

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.RenameHandler
import com.hegg5577.i18n.editor.I18nKeySupport
import com.intellij.refactoring.actions.BaseRefactoringAction
import com.hegg5577.i18n.editor.KeyUsageScanner
import com.hegg5577.i18n.locale.TranslationSaver
import com.hegg5577.i18n.settings.I18nContext

/**
 * i18n Key 重命名 Action。
 *
 * 通过 Refactor → Rename 或 Shift+F6 触发，支持重命名代码中和语言包中的 key。
 */
class I18nRenameKeyAction : BaseRefactoringAction() {

    override fun isAvailableInEditorOnly(): Boolean = false

    override fun isEnabledOnElements(elements: Array<PsiElement>): Boolean {
        return elements.size == 1 && isI18nKeyString(elements[0])
    }

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler {
        return I18nRenameKeyHandler()
    }

    /** 只接管实际翻译调用或语言包定义，普通字符串仍由原语言插件处理。 */
    private fun isI18nKeyString(element: PsiElement): Boolean = I18nKeySupport.keyAt(element) != null
}

/**
 * i18n Key 重命名的重构处理程序。
 *
 * 源码引用先完成准备，再与语言包一起提交，保证一次撤销可恢复全部修改。
 */
class I18nRenameKeyHandler : RenameHandler {

    /** 将 Shift+F6 限定在实际 Key 上，避免抢占变量和普通 JSON 属性的重命名。 */
    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        val file = CommonDataKeys.PSI_FILE.getData(dataContext)
        val editor = CommonDataKeys.EDITOR.getData(dataContext)
        if (file != null && editor != null) {
            val offset = editor.caretModel.offset
            if (I18nKeySupport.usageAt(file, offset) != null) return true
            return file.findElementAt(offset)?.let { I18nKeySupport.definitionAt(it) } != null
        }
        return CommonDataKeys.PSI_ELEMENT.getData(dataContext)?.let { I18nKeySupport.keyAt(it) } != null
    }

    /** 与可用性共用判定，让平台在 Key 上优先使用完整的多文件重命名。 */
    override fun isRenaming(dataContext: DataContext): Boolean = isAvailableOnDataContext(dataContext)

    /** 从源码字符串或 JSON 叶子属性取得完整 Key，再执行统一重命名。 */
    override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext?) {
        if (elements.isEmpty()) return

        val element = elements[0]
        val oldKey = I18nKeySupport.keyAt(element) ?: return

        // 弹出重命名对话框
        val newKey = showRenameDialog(project, oldKey) ?: return
        if (newKey == oldKey) return

        // 执行重命名
        performRename(project, oldKey, newKey, element)
    }

    /** 光标落在字符串内容或引号上时都能找到同一个 Key。 */
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
        if (editor == null || file == null) return

        val element = file.findElementAt(editor.caretModel.offset) ?: return
        invoke(project, arrayOf(element), dataContext)
    }

    /** 只允许扫描器支持的静态 Key，防止改名后无法再识别引用。 */
    private fun showRenameDialog(project: Project, oldKey: String): String? {
        val validator = object : com.intellij.openapi.ui.InputValidator {
            /** 与编辑器引用的 Key 字符规则保持一致。 */
            override fun checkInput(input: String): Boolean = I18nKeySupport.isValidKey(input, I18nContext.of(project).keyPattern)

            /** 校验不通过时保留输入，避免提交无法扫描的新名称。 */
            override fun canClose(input: String): Boolean {
                return checkInput(input)
            }
        }

        return Messages.showInputDialog(
            project,
            "输入完整的新 Key（例如 common.confirm）：",
            "重命名 i18n Key",
            Messages.getQuestionIcon(),
            oldKey,
            validator,
        )
    }

    /** 先准备源码修改，由统一入口检查冲突、删除旧 key 并提交同一撤销命令。 */
    private fun performRename(project: Project, oldKey: String, newKey: String, origin: PsiElement) {
        try {
            val result = renameKey(project, oldKey, newKey, origin)
            if (!result.isSuccess) {
                val detail = result.failed.entries.joinToString("；") { "${it.key}: ${it.value}" }
                Messages.showErrorDialog(project, "保存失败：\n$detail", "重命名失败")
            } else {
                Messages.showInfoMessage(project, "已将 $oldKey 重命名为 $newKey，可按 Ctrl+Z 撤销", "重命名完成")

                // 刷新 UI
                project.getService(com.hegg5577.i18n.ui.I18nPanelService::class.java).refresh()
            }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "重命名失败：${e.message}", "重命名失败")
        }
    }

    /** 与标准快捷键共用同一事务，源文件不在扫描范围时拒绝删除其仍在使用的旧 Key。 */
    internal fun renameKey(project: Project, oldKey: String, newKey: String, origin: PsiElement? = null): TranslationSaver.Result {
        require(I18nKeySupport.isValidKey(newKey, I18nContext.of(project).keyPattern)) { "新 Key 含有不支持的字符或超过 200 个字符" }
        val context = I18nContext.of(project)
        val repo = checkNotNull(context.repository()) { "无法定位语言包" }
        val definitions = I18nKeySupport.definitions(context, project).filter { it.key == oldKey }
        check(definitions.groupBy { it.locale }.none { it.value.size > 1 }) { "同一语言有多个同名 Key，请先明确文件归属" }
        val sourceUpdates = prepareSourceUpdates(project, context, oldKey, newKey)
        if (origin != null && I18nKeySupport.definitionAt(origin) == null) {
            val document = PsiDocumentManager.getInstance(project).getDocument(origin.containingFile)
            check(document != null && document in sourceUpdates) { "当前引用不在配置的源码扫描范围内，未修改任何文件" }
        }
        return TranslationSaver.renameKey(project, repo, oldKey, newKey, sourceUpdates)
    }

    /** 按配置扫描最新文档，任一文件无法安全准备时直接失败，不产生部分写入。 */
    internal fun prepareSourceUpdates(
        project: Project,
        context: I18nContext,
        oldKey: String,
        newKey: String,
    ): Map<Document, String> {
        requireNotNull(context.projectRoot) { "无法确定项目源码目录" }
        val updates = LinkedHashMap<Document, String>()
        val documents = FileDocumentManager.getInstance()
        val psiDocuments = PsiDocumentManager.getInstance(project)
        I18nKeySupport.processSourceFiles(context) { file ->
            val document = checkNotNull(documents.getDocument(file)) { "无法获取源码文档：${file.path}" }
            psiDocuments.commitDocument(document)
            val psiFile = checkNotNull(psiDocuments.getPsiFile(document)) { "无法解析源码：${file.path}" }
            val text = document.text
            val updated = renameSourceText(psiFile, text, oldKey, newKey, context.usageMatchRegex, context.keyPattern)
            if (updated != text) {
                check(file.isWritable && document.isWritable) { "源码文件只读：${file.path}" }
                updates[document] = updated
            }
            true
        }
        return updates
    }

    /** 使用扫描结果及捕获组双重校验位置，只倒序替换 key，保留调用参数和其他文本。 */
    internal fun renameSourceText(
        file: PsiFile,
        text: String,
        oldKey: String,
        newKey: String,
        customPatterns: List<String> = emptyList(),
        keyPattern: String = KeyUsageScanner.DEFAULT_KEY_PATTERN,
    ): String {
        // 提示扫描允许截断，重命名必须完整，否则删除旧 key 后会留下失效引用。
        check(!KeyUsageScanner.isTooLarge(text.length)) { "源码超过安全扫描上限：${file.name}" }
        val rules = KeyUsageScanner.compileRules(customPatterns, keyPattern = keyPattern)
        val usages = KeyUsageScanner.scanWith(text, rules).toList()
        check(usages.size < KeyUsageScanner.MAX_HINTS_PER_FILE) { "源码引用达到扫描上限：${file.name}" }
        val targets = usages.filter { it.key == oldKey }
        if (targets.isEmpty()) return text

        val candidates = HashMap<Int, MutableSet<IntRange>>()
        for (rule in rules) {
            for (match in rule.regex.findAll(text)) {
                val group = KeyUsageScanner.keyGroup(match) ?: continue
                if (group.value.trim().trim('"', '\'') != oldKey) continue
                val start = group.range.first + group.value.indexOf(oldKey)
                val offset = KeyUsageScanner.hintOffset(text, match.range.last + 1)
                candidates.getOrPut(offset) { LinkedHashSet() }.add(start until start + oldKey.length)
            }
        }

        val ranges = targets.map { usage ->
            val range = candidates[usage.offset]?.singleOrNull()
            checkNotNull(range) { "无法唯一定位源码 key：${file.name}:${usage.offset}" }
            check(text.substring(range) == usage.key) { "源码 key 位置已变化：${file.name}" }
            range
        }.filter { I18nKeySupport.isStaticReference(file, it) }.distinct().sortedByDescending { it.first }
        val updated = StringBuilder(text)
        var previousStart = text.length
        for (range in ranges) {
            check(range.last < previousStart) { "源码引用范围重叠：${file.name}" }
            updated.replace(range.first, range.last + 1, newKey)
            previousStart = range.first
        }
        return updated.toString()
    }
}
