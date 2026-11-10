package com.hegg5577.i18n.editor

import com.hegg5577.i18n.settings.I18nContext
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor

/** 复合点号 Key 不能依赖平台按单词索引的搜索，按已配置源码范围精确查找。 */
class I18nReferencesSearch : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    /** 遵守平台传入的搜索范围与提前停止信号，只返回经 PSI 校验的翻译调用。 */
    override fun execute(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>): Boolean =
        ReadAction.compute<Boolean, RuntimeException> {
            val target = parameters.elementToSearch
            val key = I18nKeySupport.definitionAt(target)?.key ?: return@compute true
            if (I18nKeySupport.resolve(target, key) == null) return@compute true
            val context = I18nContext.of(target.project)
            val scope = parameters.effectiveSearchScope
            val manager = PsiManager.getInstance(target.project)
            I18nKeySupport.processSourceFiles(context) { virtualFile ->
                if (!scope.contains(virtualFile)) return@processSourceFiles true
                val file = manager.findFile(virtualFile) ?: return@processSourceFiles true
                for (usage in I18nKeySupport.usages(file, context)) {
                    if (usage.key != key) continue
                    if (scope is LocalSearchScope && !scope.containsRange(file, usage.range)) continue
                    val reference = I18nKeyReference(usage.literal, key,
                        usage.range.shiftLeft(usage.literal.textRange.startOffset))
                    if (!consumer.process(reference)) return@processSourceFiles false
                }
                true
            }
        }
}

/** 从源码调用或任意语言的定义启动标准“查找用法”窗口。 */
class I18nFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    /** 仅接管已配置语言包的叶子 Key，普通 JSON 属性仍交给原语言插件。 */
    override fun canFindUsages(element: PsiElement): Boolean = target(element) != null

    /** 平台窗口负责展示结果，实际引用由统一搜索器提供。 */
    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? =
        target(element)?.let { object : FindUsagesHandler(it) {} }

    /** 源码引用统一解析到源语言定义，以完整 Key 作为查找入口。 */
    private fun target(element: PsiElement): PsiElement? {
        val key = I18nKeySupport.keyAt(element) ?: return null
        return I18nKeySupport.resolve(element, key)
    }
}
