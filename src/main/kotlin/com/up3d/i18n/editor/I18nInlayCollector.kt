package com.up3d.i18n.editor

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayPayload
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.OwnBypassCollector
import com.intellij.psi.PsiFile
import com.up3d.i18n.locale.TranslationIndex

/**
 * 在 `t('key')` 之后内联显示对应译文。
 *
 * 只负责把 [KeyUsageScanner] 的扫描结果翻译成 IDE 的 inlay 渲染；
 * 命中判定统一放在 [KeyUsageScanner]，与诊断动作共用，避免两套逻辑不一致。
 */
class I18nInlayCollector : OwnBypassCollector {

    override fun collectHintsForFile(file: PsiFile, sink: InlayTreeSink) {
        if (!TranslationIndex.isSupported(file.name)) return

        val projectRoot = file.project.basePath ?: return
        val values = TranslationIndex.values(projectRoot)
        if (values.isEmpty()) return

        val text = file.text
        if (KeyUsageScanner.isTooLarge(text.length)) return

        for (usage in KeyUsageScanner.scan(text)) {
            // 只给语言包里确实存在的 key 显示，天然过滤掉同名但非 i18n 的调用
            val translated = values[usage.key] ?: continue

            sink.addPresentation(
                position = InlineInlayPosition(usage.offset, true, 0),
                payloads = emptyList<InlayPayload>(),
                tooltip = null,
                hintFormat = HINT_FORMAT,
            ) {
                text(" $translated")
            }
        }
    }

    companion object {
        const val PROVIDER_ID = "i18n.translation.hints"

        /** 默认格式（带背景色），与平台参数提示观感一致，便于一眼看到。 */
        private val HINT_FORMAT = HintFormat.Companion.default
    }
}
