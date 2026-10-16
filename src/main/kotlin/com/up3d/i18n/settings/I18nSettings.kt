package com.up3d.i18n.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * 项目级 i18n 配置。
 *
 * 存语言包路径、源语言、显示语言等，替代原先散落在各处常量的硬编码。
 * 落到项目级 `.idea/i18n-helper.xml`，团队成员共享同一份配置。
 */
@Service(Service.Level.PROJECT)
@State(
    name = "I18nHelperSettings",
    storages = [Storage("i18n-helper.xml")],
)
class I18nSettings : PersistentStateComponent<I18nSettings.State> {

    data class State(
        /** 语言包根目录，相对项目根。例如 `src/i18n/locales`。 */
        var localesPath: String = DEFAULT_LOCALES_DIR,

        /** 源语言代码，作为「缺失」与「过期」判定的基准。 */
        var sourceLocale: String = "en",

        /** 侧边栏默认显示的语言；为空表示显示除源语言外的全部语言。 */
        var displayLocales: MutableList<String> = mutableListOf("zh"),

        /** 行内译文提示是否启用。 */
        var inlineAnnotationEnabled: Boolean = true,

        /** 行内提示的译文语言；为空表示跟随 [displayLocales] 第一项。 */
        var annotationLocale: String = "zh",

        /** 是否把未翻译的 key 显示为源语言原文。 */
        var fallbackToSource: Boolean = true,

        /** 批量翻译时是否覆盖已有译文。默认关，避免误伤人工翻译。 */
        var overrideExisting: Boolean = false,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(s: State) {
        XmlSerializerUtil.copyBean(s, state)
    }

    companion object {
        const val DEFAULT_LOCALES_DIR = "src/i18n/locales"

        fun getInstance(project: Project): I18nSettings =
            project.getService(I18nSettings::class.java)
    }
}