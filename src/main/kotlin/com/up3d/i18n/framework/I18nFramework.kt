package com.up3d.i18n.framework

/**
 * i18n 框架描述。
 *
 * 刻意做成**声明式配置**而非解析器：对前端框架而言，key 的提取差异本质上只有
 * 「调用写法」和「语言包结构」两点，用「正则 + 目录规则」即可覆盖，绝大多数框架
 * 不需要真正的AST 解析。这样新增一个框架的成本约等于写一条配置。
 *
 * 参考 i18n-ally-next 的 `src/frameworks/base.ts` 设计，但按 JetBrains 场景简化：
 * 去掉了 Laravel/Rails/Flutter 等后端专属能力，只保留 JS/TS 生态需要的字段。
 */
data class I18nFramework(
    /** 稳定标识，如 `i18next`。 */
    val id: String,

    /** 展示名。 */
    val display: String,

    /**
     * key 提取正则，必须含一个名为 `key` 的捕获组。
     *
     * 覆盖该框架的几种常见调用写法，如 `t('a')` 与 `i18n.t("a")`。
     * 允许在 `${key}` 之外使用其它捕获组，但只有 `key` 会被取用。
     */
    val usagePatterns: List<Regex>,

    /** 该框架在代码里绑定到哪种语言上，用于 Inlay 提示的语言过滤。 */
    val supportedLanguages: Set<String>,

    /**
     * 语言包目录的匹配规则。
     *
     * 对 i18next / vue-i18n 这类「每语言一个目录」的结构，
     * 规则为 `dir/locale/file`；对单文件结构则退化为 `file`。
     */
    val localeLayout: LocaleLayout,

    /** key 中的命名空间分隔符。 */
    val namespaceDelimiters: List<String> = listOf(":"),

    /**
     * 复数派生规则。当源语言出现 `{key}_other` 时，衍生出对应的 key 也算已翻译。
     * 命名沿用 i18next 的后缀约定。
     */
    val derivedKeySuffixes: List<String> = emptyList(),

    /**
     * 依赖检测：在 `package.json` 的 dependencies 里命中这些名字即可认为项目用了该框架。
     * 空列表表示不做依赖检测（用户手动指定框架时用）。
     */
    val packageDetection: List<String> = emptyList(),

    /** 互斥依赖：若命中则禁用本框架。例如有 react-i18next 就不该再按裸 i18next 处理。 */
    val packageExclusions: List<String> = emptyList(),
)

/** 语言包在磁盘上的组织形式。 */
sealed interface LocaleLayout {
    /** 每语言一个目录：`locales/{locale}/{fileName}`。i18next / vue-i18n 默认结构。 */
    data class PerLocaleDir(val dirName: String, val fileName: String) : LocaleLayout

    /** 单文件多语言：`{fileName}` 内按 locale 分组。 */
    data class SingleFile(val fileName: String) : LocaleLayout
}

/**
 * 内置框架注册表。
 *
 * 当前只覆盖 JS/TS 前端生态的四个常用方案。扩展新框架只需往[ALL] 里加一条配置，
 * 无需改动任何调用方代码。
 */
object Frameworks {

    /** JS/TS 全套语言 ID，凡是这些文件都允许挂译文提示。 */
    private val JS_LANGUAGES = setOf(
        "JavaScript", "TypeScript", "JSX Harmony", "TSX", "ECMAScript 6", "Vue.js", "HTML",
    )

    /**
     * 构造 key 提取正则。
     *
     * 形态为 `<receiver>\.<method>\s*\(\s*(引号)(?<key>...)\1`，
     * `receiver` 为空表示裸函数调用（`t('a')`），否则形如 `i18next.t('a')`。
     * `method` 覆盖该框架所有可能的调用名，`key` 是必须存在的命名捕获组。
     *
     * 所有片段都是纯正则文本，不含 `$` 模板语法之外的 Kotlin 插值 ——
     * Vue 的 `$t` / `$tc` 里那个 `$` 在正则中需转义为 `\$`。
     */
    private fun patterns(receivers: List<String>, methods: String): List<Regex> =
        receivers.flatMap { receiver ->
            val head = if (receiver.isEmpty()) "" else "$receiver\\s*\\.\\s*"
            // 两种引号风格各一条：'a' 与 "a"
            listOf("'", "\"").map { quote ->
                Regex("""$head(?:$methods)\s*\(\s*$quote(?<key>[^'"]+)$quote""")
            }
        }

    val I18NEXT = I18nFramework(
        id = "i18next",
        display = "i18next",
        usagePatterns = patterns(
            receivers = listOf("", "i18next", "i18n"),
            methods = "t|gettext|transl|translate",
        ),
        supportedLanguages = JS_LANGUAGES,
        localeLayout = LocaleLayout.PerLocaleDir("locales", "index.json"),
        namespaceDelimiters = listOf(":", "/"),
        derivedKeySuffixes = listOf(
            "_plural", "_zero", "_one", "_two", "_few", "_many", "_other",
        ),
        packageDetection = listOf("i18next"),
        packageExclusions = listOf("react-i18next", "vue-i18n"),
    )

    val VUE_I18N = I18nFramework(
        id = "vue-i18n",
        display = "vue-i18n",
        usagePatterns = patterns(
            // Vue 组件内 `$t('a')` / `$tc('a', 2)` 是最常见写法，故单独列出 receiver
            receivers = listOf("", "\\\$t", "\\\$tc", "i18n"),
            methods = "t|gettext|transl|translate",
        ),
        supportedLanguages = JS_LANGUAGES,
        localeLayout = LocaleLayout.PerLocaleDir("locales", "index.json"),
        namespaceDelimiters = listOf(":"),
        derivedKeySuffixes = listOf("_plural", "_zero", "_one", "_two", "_few", "_many", "_other"),
        packageDetection = listOf("vue-i18n"),
    )

    val REACT_I18NEXT = I18nFramework(
        id = "react-i18next",
        display = "react-i18next",
        usagePatterns = patterns(
            receivers = listOf("", "i18next", "t"),
            methods = "t|gettext|transl|translate",
        ),
        supportedLanguages = JS_LANGUAGES,
        localeLayout = LocaleLayout.PerLocaleDir("locales", "translation.json"),
        namespaceDelimiters = listOf(":"),
        derivedKeySuffixes = listOf("_one", "_other", "_zero", "_two", "_few", "_many"),
        packageDetection = listOf("react-i18next"),
    )

    val I18N_JS = I18nFramework(
        id = "i18n-js",
        display = "i18n-js",
        // i18n-js 的典型写法是 `I18n.t('a')` 与 `i18n.translate('a')`
        usagePatterns = patterns(
            receivers = listOf("", "I18n", "i18n"),
            methods = "t|translate|transl|gettext",
        ),
        supportedLanguages = JS_LANGUAGES,
        localeLayout = LocaleLayout.SingleFile("locales.json"),
        namespaceDelimiters = listOf("."),
        packageDetection = listOf("i18n-js"),
    )

    /** 全部内置框架，按注册顺序。 */
    val ALL: List<I18nFramework> = listOf(I18NEXT, VUE_I18N, REACT_I18NEXT, I18N_JS)

    private val byId = ALL.associateBy { it.id }

    fun byId(id: String): I18nFramework? = byId[id]

    /**
     * 读`package.json` 依赖名，自动推断项目用的框架。
     *
     * 依赖顺序很重要：[ALL] 里靠前的优先级更高。命中互斥依赖的框架会被跳过，
     * 这样「装了 react-i18next 就不会同时按裸 i18next 处理」。
     */
    fun detect(dependencies: Set<String>): List<I18nFramework> =
        ALL.filter { framework ->
            val detected = framework.packageDetection.any { it in dependencies }
            val excluded = framework.packageExclusions.any { it in dependencies }
            detected && !excluded
        }

    /** 从 package.json 里提取 dependencies / devDependencies 的包名。 */
    fun readPackageNames(packageJsonText: String): Set<String> =
        runCatching {
            val root = com.google.gson.JsonParser.parseString(packageJsonText).asJsonObject
            buildSet {
                for (field in listOf("dependencies", "devDependencies")) {
                    val obj = root.getAsJsonObject(field) ?: continue
                    for (name in obj.keySet()) add(name)
                }
            }
        }.getOrDefault(emptySet())
}