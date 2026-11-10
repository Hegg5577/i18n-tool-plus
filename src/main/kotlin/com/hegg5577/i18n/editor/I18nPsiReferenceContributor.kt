package com.hegg5577.i18n.editor

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.hegg5577.i18n.settings.I18nContext
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.IncorrectOperationException
import com.intellij.util.ProcessingContext

/** 将翻译调用中的字符串接到平台引用体系，供跳转和查找引用使用。 */
class I18nPsiReferenceContributor : PsiReferenceContributor() {
    /** 共用范围校验，避免 JavaScript、TypeScript 和模板中的识别规则分叉。 */
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(), I18nKeyReferenceProvider())
    }
}

/** 仅在 Key 所在的完整字符串节点创建引用，避免父子节点重复注册。 */
class I18nKeyReferenceProvider : PsiReferenceProvider() {
    /** 复用扫描与 PSI 校验，普通字符串、注释和参数中的相似文本不会产生引用。 */
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val text = element.text
        if (text.length < 2 || text.first() !in "\"'`" || text.last() != text.first()) return PsiReference.EMPTY_ARRAY
        val file = element.containingFile ?: return PsiReference.EMPTY_ARRAY
        val usage = I18nKeySupport.usages(file).firstOrNull { it.literal == element && it.key.isNotEmpty() }
            ?: return PsiReference.EMPTY_ARRAY
        return arrayOf(I18nKeyReference(element, usage.key,
            usage.range.shiftLeft(element.textRange.startOffset)))
    }
}

/** 引用范围只包含 Key 内容，重命名不会改变引号、参数或相似 Key。 */
class I18nKeyReference(
    element: PsiElement,
    private val keyText: String,
    range: TextRange = TextRange(1, element.textLength - 1),
) : PsiReferenceBase<PsiElement>(element, range, true) {

    /** 源语言优先；遇到多文件同名 Key 的歧义时返回空值。 */
    override fun resolve(): PsiElement? = I18nKeySupport.resolve(element, keyText)

    /** 查找引用时使用完整路径，避免嵌套 Key 被当成末级名称。 */
    override fun getCanonicalText(): String = keyText

    /** 补全显示首选语言的译文，缺少该语言时回退到源语言。 */
    override fun getVariants(): Array<Any> = variants(element).toTypedArray()

    /** 平台直接调用引用改名时也只修改字符串内容，保留调用语法。 */
    override fun handleElementRename(newName: String): PsiElement {
        if (!I18nKeySupport.isValidKey(newName, I18nContext.of(element.project).keyPattern)) throw IncorrectOperationException("Key 含有不支持的字符或超过 200 个字符，请使用已配置的命名空间分隔符")
        return ElementManipulators.handleContentChange(element, rangeInElement, newName)
    }

    /** 各语言定义均代表同一个逻辑 Key，可从任意语言包查找源码引用。 */
    override fun isReferenceTo(target: PsiElement): Boolean {
        val definition = I18nKeySupport.definitionAt(target) ?: return false
        return definition.key == keyText && resolve() != null
    }

    companion object {
        /** 同一 Key 只显示一次，并排除尚无明确归属的多文件冲突。 */
        internal fun variants(element: PsiElement): List<LookupElementBuilder> {
            val context = I18nContext.of(element.project)
            return I18nKeySupport.definitions(context, element.project).groupBy { it.key }.mapNotNull { (key, values) ->
                if (values.groupBy { it.locale }.any { it.value.size > 1 } || !I18nKeySupport.isValidKey(key, context.keyPattern)) return@mapNotNull null
                val preferred = values.firstOrNull { it.locale == context.effectiveAnnotationLocale }
                    ?: values.firstOrNull { it.locale == context.sourceLocale } ?: values.first()
                LookupElementBuilder.create(key).withTypeText("${preferred.locale}: ${preferred.value.replace('\n', ' ').take(100)}", true)
            }
        }
    }
}
