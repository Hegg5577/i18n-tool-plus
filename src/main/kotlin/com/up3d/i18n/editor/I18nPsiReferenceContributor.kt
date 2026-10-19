package com.up3d.i18n.editor

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext

/**
 * i18n Key 的 PsiReference 支持。
 *
 * 负责在代码中识别 `t('key')` 里的 key 字符串。
 * 为后续的跳转和重命名功能提供基础。
 */
class I18nPsiReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: com.intellij.psi.PsiReferenceRegistrar) {
        // 为所有 PsiElement 注册引用提供程序
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            I18nKeyReferenceProvider(),
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
) : PsiReferenceBase<PsiElement>(element, true) {

    override fun resolve(): PsiElement? {
        // 跳转到语言包中 key 的定义位置
        // 这需要 TranslationIndex 来查找 key 的位置
        return null
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun handleElementRename(newName: String): PsiElement {
        // 重命名 key 时调用
        val newText = "\"$newName\""
        return element
    }
}

