package com.up3d.i18n.editor

import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory
import com.intellij.codeInsight.hints.declarative.InlayProviderInfo
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * 行内译文提示的 Provider：把收集器绑到具体文件上。
 */
class I18nInlayHintsProvider : InlayHintsProvider {
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector =
        I18nInlayCollector()
}

/**
 * 声明该 Provider 覆盖哪些语言。
 *
 * 用 [Language.findLanguageByID] 解析，未安装对应插件的语言会被自动过滤掉，
 * 因此不会出现插件加载失败的问题。
 */
class I18nInlayProviderFactory : InlayHintsProviderFactory {

    private val provider = I18nInlayHintsProvider()

    override fun getSupportedLanguages(): Set<Language> =
        LANGUAGE_IDS.mapNotNull { Language.findLanguageByID(it) }.toSet()

    override fun getProvidersForLanguage(language: Language): List<InlayProviderInfo> =
        if (language.id in LANGUAGE_IDS) listOf(newInfo()) else emptyList()

    override fun getProviderInfo(language: Language, providerId: String): InlayProviderInfo? =
        if (providerId == I18nInlayCollector.PROVIDER_ID && language.id in LANGUAGE_IDS) newInfo() else null

    private fun newInfo(): InlayProviderInfo = InlayProviderInfo(
        /* provider = */ provider,
        /* providerId = */ I18nInlayCollector.PROVIDER_ID,
        /* options = */ emptySet(),
        /* isEnabledByDefault = */ true,
        /* providerName = */ PROVIDER_NAME,
    )

    companion object {
        const val PROVIDER_NAME = "i18n translations"

        /**
         * 目标语言的 ID。JSX / TSX 在 IntelliJ 里属于 JavaScript / TypeScript 语言，
         * 因此无需单独列出。
         */
        val LANGUAGE_IDS = setOf("JavaScript", "TypeScript", "HTML", "Vue")

        /** 该语言的 id 是否在我们支持的范围里（供诊断复用）。 */
        fun supports(languageId: String): Boolean = languageId in LANGUAGE_IDS
    }
}
