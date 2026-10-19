package com.hegg5577.i18n.editor

import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

/**
 * i18n Key 的 PsiReference 支持。
 *
 * 负责在代码中识别 `t('key')` 里的 key 字符串。
 * 为后续的跳转和重命名功能提供基础。
 *
 * 支持的用法：
 * - JavaScript/TypeScript: `t('key')`, `i18n.t('key')`, `$t('key')`
 * - JSX: 属性值中使用
 * - Vue: `{{ $t('key') }}`
 */
class I18nPsiReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // 匹配字符串字面量作为 i18n 函数的第一个参数
        registrar.registerReferenceProvider(
            createI18nStringPattern(),
            I18nKeyReferenceProvider(),
        )
    }

    private fun createI18nStringPattern(): PsiElementPattern.Capture<PsiElement> {
        return PlatformPatterns.psiElement(JSLiteralExpression::class.java)
            .withParent(
                StandardPatterns.or(
                    // t('key') 直接调用
                    PlatformPatterns.psiElement(JSCallExpression::class.java)
                        .withFirstChild(
                            PlatformPatterns.psiElement()
                                .withText(StandardPatterns.string().contains("t"))
                        ),
                )
            )
    }
}

/**
 * i18n Key 的引用提供程序。
 */
class I18nKeyReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> {
        // 检查是否是可能的 i18n key 字符串
        if (!isKeyString(element)) return PsiReference.EMPTY_ARRAY

        // 检查是否在 i18n 函数调用中
        if (!isInI18nCall(element)) return PsiReference.EMPTY_ARRAY

        // 从字符串中提取 key
        val keyText = extractKey(element) ?: return PsiReference.EMPTY_ARRAY

        // 创建引用
        return arrayOf(I18nKeyReference(element, keyText))
    }

    private fun isKeyString(element: PsiElement): Boolean {
        val text = element.text
        // 必须是字符串字面量
        return (text.startsWith("\"") && text.endsWith("\"")) ||
               (text.startsWith("'") && text.endsWith("'")) ||
               (text.startsWith("`") && text.endsWith("`"))
    }

    private fun isInI18nCall(element: PsiElement): Boolean {
        // 检查是否在以下函数调用中：
        // - t(), T(), $t()
        // - i18n.t()
        // - translator.t()
        val parent = element.parent?.parent
        val text = parent?.text ?: return false

        return text.contains(Regex("""\b(t|T|\$t|i18n\.t|translator\.t)\s*\("""))
    }

    private fun extractKey(element: PsiElement): String? {
        val text = element.text
        return when {
            text.length > 2 && text.startsWith("\"") && text.endsWith("\"") ->
                text.substring(1, text.length - 1)
            text.length > 2 && text.startsWith("'") && text.endsWith("'") ->
                text.substring(1, text.length - 1)
            text.length > 2 && text.startsWith("`") && text.endsWith("`") ->
                text.substring(1, text.length - 1)
            else -> null
        }
    }
}

/**
 * i18n Key 的单条引用。
 *
 * 代表代码中 `t('key')` 里对 key 的引用。
 * 支持跳转到语言包定义位置和重命名重构。
 */
class I18nKeyReference(
    element: PsiElement,
    private val keyText: String,
) : com.intellij.psi.PsiReferenceBase<PsiElement>(element, true) {

    override fun resolve(): PsiElement? {
        // 跳转到语言包中 key 的定义位置
        // 通过 TranslationIndex 查找 key
        val project = element.project
        val transIndex = TranslationIndex.getInstance(project)
        return transIndex.findKeyDefinitionElement(keyText)
    }

    override fun getVariants(): Array<Any> {
        // 为代码补全提供所有可用的 key
        val project = element.project
        val transIndex = TranslationIndex.getInstance(project)
        return transIndex.getAllKeys()
            .map { key ->
                com.intellij.codeInsight.completion.PrioritizedLookupElement.withPriority(
                    com.intellij.codeInsight.lookup.LookupElementBuilder.create(key),
                    1.0,
                )
            }
            .toTypedArray()
    }

    override fun handleElementRename(newName: String): PsiElement {
        // 重命名 key 时调用，更新代码中的引用
        val newText = element.text.replace(keyText, newName)
        return element.replace(
            com.intellij.psi.PsiElementFactory.getInstance(element.project)
                .createStringLiteralFromText(newText, element)
        )
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        // 检查该引用是否指向给定的 element
        val project = this.element.project
        val transIndex = TranslationIndex.getInstance(project)
        return transIndex.findKeyDefinitionElement(keyText) == element
    }
}

