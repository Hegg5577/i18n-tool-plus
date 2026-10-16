package com.up3d.i18n.ui

/**
 * 语言代码到国旗 emoji 的映射。
 *
 * 刻意用 emoji 而非图片资源：省掉十几个 flag png 的打包与 DPI适配，
 * 且现代 JetBrains 平台在 Linux/macOS/Windows 上都能渲染。
 * 平台把 emoji 渲染成彩色字形时会自动跟随主题，不需要额外处理。
 */
object Flags {

    /** 常见语言代码 -> ISO 3166 国家码。 */
    private val REGION: Map<String, String> = mapOf(
        // 中文
        "zh" to "CN", "zh-CN" to "CN", "zh-Hans" to "CN", "zh-TW" to "TW",
        "zh-Hant" to "TW", "zh-HK" to "HK", "zh-MO" to "MO", "zh-SG" to "SG",
        // 日韩
        "ja" to "JP", "ko" to "KR",
        // 东南亚
        "th" to "TH", "vi" to "VN", "id" to "ID", "ms" to "MY", "fil" to "PH", "km" to "KH",
        // 南亚
        "hi" to "IN", "bn" to "BD", "ta" to "IN", "te" to "IN", "mr" to "IN", "gu" to "IN", "ur" to "PK", "ne" to "NP", "si" to "LK",
        // 西亚
        "ar" to "SA", "he" to "IL", "fa" to "IR", "ur-PK" to "PK", "tr" to "TR", "ku" to "IQ",
        // 欧洲
        "en" to "US", "en-GB" to "GB", "en-US" to "US", "en-AU" to "AU", "en-CA" to "CA",
        "en-IN" to "IN", "en-NZ" to "NZ", "en-ZA" to "ZA",
        "de" to "DE", "fr" to "FR", "es" to "ES", "pt" to "PT", "pt-BR" to "BR", "pt-PT" to "PT",
        "it" to "IT", "nl" to "NL", "ru" to "RU", "uk" to "UA", "pl" to "PL", "cs" to "CZ",
        "sk" to "SK", "sv" to "SE", "da" to "DK", "fi" to "FI", "nb" to "NO", "no" to "NO",
        "el" to "GR", "hu" to "HU", "ro" to "RO", "bg" to "BG", "hr" to "HR", "sr" to "RS",
        "sl" to "SI", "et" to "EE", "lv" to "LV", "lt" to "LT", "is" to "IS", "ga" to "IE",
        "sq" to "AL", "bs" to "BA", "mk" to "MK", "ca" to "ES", "gl" to "ES", "eu" to "ES",
        // 美洲
        "es-MX" to "MX", "es-AR" to "AR", "es-CL" to "CL", "es-CO" to "CO", "es-US" to "US",
        "fr-CA" to "CA", "fr-BE" to "BE", "fr-CH" to "CH",
        // 其他
        "sw" to "KE", "af" to "ZA", "zu" to "ZA",
    )

    /**
     * 取语言代码对应的 emoji 旗帜；查不到时返回地球仪，避免出现空白方块。
     *
     * 处理了两类输入：带地区后缀的（`pt-BR`）先整查，再退化为按主语言查（`pt`）。
     */
    fun emoji(locale: String): String {
        if (locale.isBlank()) return "🌐"
        val normalized = locale.replace('_', '-')
        REGION[normalized]?.let { return flagEmoji(it) }
        normalized.substringBefore('-')
            .takeIf { it != normalized }
            ?.let { sub -> REGION[sub]?.let { return flagEmoji(it) } }
        return "🌐"
    }

    /** 两字节区域码转 regional indicator 符号。 */
    private fun flagEmoji(regionCode: String): String {
        if (regionCode.length != 2) return "🌐"
        val base = 0x1F1E6
        val sb = StringBuilder(4)
        for (c in regionCode.uppercase()) {
            sb.append((base + (c.code - 'A'.code)).toChar())
        }
        return sb.toString()
    }

    /** 语言代码的中文显示名，用于面板表头。 */
    fun displayName(locale: String): String {
        val normalized = locale.replace('_', '-')
        val javaLocale = runCatching {
            java.util.Locale.forLanguageTag(normalized)
        }.getOrNull()

        val name = javaLocale?.getDisplayLanguage(java.util.Locale.CHINA)
        // getDisplayLanguage 在未知语言上会回显输入本身，此时退化为"原始文本"标签
        return if (!name.isNullOrBlank() && name != normalized && name != locale) {
            "$name ($locale)"
        } else {
            locale
        }
    }
}

/**
 * 单条翻译的状态，对齐原版三态。
 *
 * - [TRANSLATED] 已翻译且非空
 * - [EMPTY] key 存在但值为空串或纯空白
 * - [MISSING] 语言包里没有这个 key
 * - [STALE] 已有译文，但与源语言当前原文不一致（源文本改过）
 */
enum class TranslationStatus(val label: String) {
    TRANSLATED("已翻译"),
    EMPTY("待填写"),
    MISSING("缺失"),
    STALE("已过期");

    companion object {
        /**
         * 判定一条译文的展示状态。
         *
         * @param target 目标语言的译文，`null` 表示 key 不存在
         * @param sourceText 源语言当前原文，用于过期检测；`null` 则跳过该判断
         */
        fun of(target: String?, sourceText: String? = null): TranslationStatus {
            if (target == null) return MISSING
            if (target.isBlank()) return EMPTY
            if (sourceText != null && target != sourceText && looksStale(target, sourceText)) {
                return STALE
            }
            return TRANSLATED
        }

        /**
         * 过期判定只做精确比对。
         *
         * 刻意**不做**"源文本变了就算过期"的宽松判断：占位符顺序调整、
         * 标点差异都会导致大量误报，噪音会让用户失去对提示的信任。
         */
        private fun looksStale(target: String, sourceText: String): Boolean =
            target != sourceText && placeholdersMatch(target, sourceText)

        /** 两段文本的占位符集合一致时，认为译文源自同一句原文、只是措辞变了。 */
        private fun placeholdersMatch(a: String, b: String): Boolean =
                PLACEHOLDER.findAll(a).map { it.value }.toSet() ==
                        PLACEHOLDER.findAll(b).map { it.value }.toSet()

        private val PLACEHOLDER = Regex("""\{[^{}]*\}|\$\{[^{}]*\}|%[sd]""")
    }
}

/**
 * 状态对应的行内标记符号。
 *
 * 用 Unicode 符号而非 [javax.swing.Icon]：本表格单元格宽度有限，符号能随文本
 * 一起被复制粘贴，且不依赖平台图标资源在不同主题下的存在性。
 */
val TranslationStatus.marker: String
    get() = when (this) {
        TranslationStatus.TRANSLATED -> "✓"
        TranslationStatus.EMPTY -> "○"
        TranslationStatus.MISSING -> "✗"
        TranslationStatus.STALE -> "⚠"
    }