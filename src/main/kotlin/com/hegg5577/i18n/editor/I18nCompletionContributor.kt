package com.hegg5577.i18n.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiElement

/** 在空字符串和部分 Key 中补全，使用完整 Key 前缀而不是只匹配最后一段。 */
class I18nCompletionContributor : CompletionContributor() {
    /** 在翻译字符串内输入时自动弹出候选，普通字符串保持原编辑器行为。 */
    override fun invokeAutoPopup(position: PsiElement, typeChar: Char): Boolean {
        val file = position.containingFile ?: return false
        val context = com.hegg5577.i18n.settings.I18nContext.of(file.project)
        if (!typeChar.isLetterOrDigit() && typeChar !in "\"'`._-" &&
            !(context.namespaceEnabled && typeChar in context.namespaceDelimiter)) return false
        return I18nKeySupport.usages(file, context).any { it.literal.textRange.contains(position.textRange) }
    }

    /** 从字符串中间补全时也替换完整 Key，避免残留旧后缀。 */
    override fun beforeCompletion(context: CompletionInitializationContext) {
        if (context.completionType != CompletionType.BASIC) return
        val usage = I18nKeySupport.usageAt(context.file, context.startOffset, allowEmpty = true) ?: return
        context.replacementOffset = usage.range.endOffset
    }

    /** 原文件不含平台补全占位符，可准确识别空 Key 并保留引号及调用参数。 */
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return
        val usage = I18nKeySupport.usageAt(parameters.originalFile, parameters.offset, allowEmpty = true) ?: return
        if (parameters.offset < usage.range.startOffset) return
        val prefix = parameters.originalFile.text.substring(usage.range.startOffset, parameters.offset)
        result.withPrefixMatcher(prefix).addAllElements(I18nKeyReference.variants(parameters.originalFile).map { candidate ->
            candidate.withInsertHandler { context, _ ->
                // Enter 默认保留旧后缀，显式删除到已记录的字符串末尾，行为与 Tab 一致。
                val end = context.offsetMap.getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET)
                if (end > context.tailOffset) context.document.deleteString(context.tailOffset, end)
            }
        })
        result.stopHere()
    }
}
