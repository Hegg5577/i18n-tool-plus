package com.up3d.i18n.ui

/**
 * 一条 key 在各语言下的翻译情况，是对照表的一行。
 *
 * @param key 点号路径形式的 key，如 `common.cancel`
 * @param sourceText 源语言当前原文，作为比对基准
 * @param values locale -> 译文；语言包里没有该 key 时 map 里不会有这个 locale
 * @param usageCount 代码中引用该 key 的文件数，0 表示未统计
 */
data class TranslationRow(
    val key: String,
    val sourceText: String,
    val values: Map<String, String>,
    val usageCount: Int = 0,
) {
    /**
     * 该行在某语言下的展示状态。
     *
     * 源语言列传[sourceLocale] 进来时不做比对 —— 它是基准，
     * 否则整列都会显示"已过期"或"缺失"，等于没有基准。
     */
    fun status(locale: String, sourceLocale: String): TranslationStatus {
        val target = values[locale]
        val source = if (locale == sourceLocale) null else sourceText
        return TranslationStatus.of(target, source)
    }
}