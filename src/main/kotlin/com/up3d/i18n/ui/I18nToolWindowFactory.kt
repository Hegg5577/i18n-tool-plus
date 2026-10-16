package com.up3d.i18n.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.up3d.i18n.locale.LocaleRepository
import com.up3d.i18n.settings.I18nSettings
import java.io.File

/**
 * 侧边栏「i18n」面板的数据服务。
 *
 * 抽成 project 级 Service 是为了让 UI 与数据加载解耦：
 * 刷新动作只管调 [refresh]，UI 订阅回调重绘，符合平台生命周期。
 */
@Service(Service.Level.PROJECT)
class I18nPanelService(private val project: Project) {

    data class Snapshot(
        val rows: List<TranslationRow>,
        val sourceLocale: String,
        val displayLocales: List<String>,
        /** locale -> (已翻译数, 总数)，用于进度概览。 */
        val progress: Map<String, Pair<Int, Int>>,
    )

    private val listeners = mutableListOf<(Snapshot) -> Unit>()
    private var cached: Snapshot? = null

    /** 订阅数据变化，返回的句柄用于取消订阅。 */
    fun subscribe(listener: (Snapshot) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    /** 重新扫描语言包并通知订阅者。 */
    fun refresh() {
        val snapshot = load()
        cached = snapshot
        listeners.toList().forEach { it(snapshot) }
    }

    /** 取当前快照，未加载过则现场构建。 */
    fun snapshot(): Snapshot = cached ?: load().also { cached = it }

    private fun load(): Snapshot {
        val settings = I18nSettings.getInstance(project)
        val state = settings.state
        val root = project.basePath?.let(::File)

        if (root == null || !root.isDirectory) {
            return Snapshot(emptyList(), state.sourceLocale, state.displayLocales, emptyMap())
        }

        val repo = LocaleRepository(root, state.localesPath)
        val all = repo.scanLocales()
        if (all.isEmpty()) {
            return Snapshot(emptyList(), state.sourceLocale, state.displayLocales, emptyMap())
        }

        // 源语言不存在时退化为列表首个，保证总有基准可用
        val sourceLocale = all.firstOrNull { it.locale == state.sourceLocale }?.locale
            ?: all.first().locale
        val displayLocales = all.map { it.locale }.filter { it != sourceLocale }

        // 取所有语言的 key 并集作为行集合：任何语言里出现过的 key 都要列出来，
        // 否则「某语言多出一个 key」这类问题在表里根本看不见。
        val allKeys = LinkedHashSet<String>()
        all.forEach { allKeys += it.keys }

        val sourceKeys = all.firstOrNull { it.locale == sourceLocale }?.keys.orEmpty()
        // 源语言里的 key 排在前面，其余按字母序，避免刷新时行序跳动
        val sortedKeys = allKeys.sortedWith(compareBy({ if (sourceKeys.contains(it)) 0 else 1 }, { it }))

        val valueCache = HashMap<String, Map<String, String>>()
        for (item in all) {
            valueCache[item.locale] = repo.valuesOf(item.locale)
        }

        val rows = sortedKeys.map { key ->
            val values = HashMap<String, String>()
            for (item in all) {
                valueCache[item.locale]?.get(key)?.let { values[item.locale] = it }
            }
            TranslationRow(
                key = key,
                sourceText = values[sourceLocale].orEmpty(),
                values = values,
            )
        }

        val total = allKeys.size.coerceAtLeast(1)
        val progress = all.associate { item ->
            val done = allKeys.count { key -> !valueCache[item.locale]?.get(key).isNullOrBlank() }
            item.locale to (done to total)
        }

        return Snapshot(rows, sourceLocale, displayLocales, progress)
    }
}

/**
 * 在侧边栏注册 i18n 面板。
 *
 * 结构对齐原版：多语言对照表为主体，语言列表随项目实际语言包动态变化。
 */
class I18nToolWindowFactory : ToolWindowFactory {

    companion object {
        const val TOOL_WINDOW_ID = "i18n-helper"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = project.getService(I18nPanelService::class.java)
        val panel = TranslationTablePanel("en", emptyList())

        val content = ContentFactory.getInstance().createContent(panel, "翻译", false)
        toolWindow.contentManager.addContent(content)

        val unsubscribe = service.subscribe { snapshot ->
            // ToolWindow 已关闭时不再重绘，避免对已 dispose 的组件操作
            if (toolWindow.isDisposed) return@subscribe
            panel.setLocales(snapshot.sourceLocale, snapshot.displayLocales)
            panel.setRows(snapshot.rows)
        }

        Disposer.register(toolWindow.disposable) { unsubscribe() }
        service.refresh()
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

/** 便捷入口：主动打开侧边栏面板。 */
fun Project.showI18nToolWindow() {
    ToolWindowManager.getInstance(this)
        .getToolWindow(I18nToolWindowFactory.TOOL_WINDOW_ID)
        ?.activate(null, true)
}