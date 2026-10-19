package com.up3d.i18n.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.up3d.i18n.locale.LocaleKeys
import com.up3d.i18n.settings.I18nContext

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
        /** 代码里用到但语言包没定义的 key。 */
        val missingKeys: List<MissingKey> = emptyList(),
        /** 语言包定义了但代码里没用到。 */
        val unusedKeys: List<String> = emptyList(),
        /** 语言包定义的 key 总数 / 代码引用到的 key 总数。 */
        val definedCount: Int = 0,
        val usedCount: Int = 0,
        /** 配置或路径有问题时的提示信息，为空表示一切正常。 */
        val warning: String = "",
    )

    /** 一个缺失的 key 及其引用位置。 */
    data class MissingKey(
        val key: String,
        /** 引用该 key 的文件（相对路径）。 */
        val files: List<String>,
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

    /**
     * 挑选基准语言。
     *
     * 优先级：配置指定 → key 数量最多的语言 → 列表首个。
     * 刻意**不简单取字母序首个** —— 目录名排序把 `ar`（阿拉伯语）排在最前，
     * 一旦配置的源语言写错，整张表会以阿拉伯语为原文显示，
     * 看起来"全是译文但 key 对不上"。用 key 数量兜底更稳。
     */
    private fun pickSource(configured: String, locales: List<LocaleKeys>): String? {
        locales.firstOrNull { it.locale == configured }?.let { return it.locale }
        return locales.maxByOrNull { it.keys.size }?.locale ?: locales.firstOrNull()?.locale
    }

    private fun load(): Snapshot {
        val context = I18nContext.of(project)
        if (!context.isValid) {
            return Snapshot(
                emptyList(), context.sourceLocale, context.displayLocales, emptyMap(),
                warning = "无法定位项目根目录",
            )
        }

        val root = context.projectRoot!!
        val repo = context.repository() ?: return Snapshot(
            emptyList(), context.sourceLocale, context.displayLocales, emptyMap(),
            warning = "无法定位项目根目录",
        )

        val all = repo.scanLocales()
        if (all.isEmpty()) {
            val warning = "未找到语言包。当前配置：路径 ${context.localesPath} + 模式 ${context.pathMatcher}"
            return Snapshot(
                emptyList(), context.sourceLocale, context.displayLocales, emptyMap(),
                warning = warning,
            )
        }

        val sourceLocale = pickSource(context.sourceLocale, all) ?: all.first().locale

        // 配置里指定的语言排前面，其余按字母序，减少横向滚动
        val preferred = context.displayLocales.filter { it != sourceLocale && all.any { l -> l.locale == it } }
        val rest = all.map { it.locale }
            .filter { it != sourceLocale && !preferred.contains(it) }
            .sorted()
        val displayLocales = preferred + rest

        // 取所有语言的 key 并集作为行集合：任何语言里出现过的 key 都要列出来，
        // 否则「某语言多出一个 key」这类问题在表里根本看不见
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

        // 扫描代码里实际用到的 key，与语言包比对找出「缺失」与「多余」
        val usage = runCatching {
            com.up3d.i18n.locale.UsageScanner.analyze(
                projectRoot = root,
                localesDir = context.localesPath,
                pathMatcher = context.pathMatcher,
                keyStyle = context.keyStyle,
                sourceDirs = context.sourceDirs.ifEmpty { com.up3d.i18n.locale.DEFAULT_SOURCE_DIRS },
                extensions = context.supportedExtensions,
                customPatterns = context.usageMatchRegex,
            )
        }.getOrNull()

        // 源语言没配对时给出提示，避免用户对着"整表都是原文"的界面一头雾水
        val warning = if (all.none { it.locale == context.sourceLocale }) {
            "配置指定的源语言「${context.sourceLocale}」不存在，当前改用「${Flags.displayName(sourceLocale)}」作为基准。"
        } else {
            ""
        }

        return Snapshot(
            rows = rows,
            sourceLocale = sourceLocale,
            displayLocales = displayLocales,
            progress = progress,
            missingKeys = usage?.missing?.map { key ->
                MissingKey(key, usage.usages[key]?.files.orEmpty())
            }.orEmpty(),
            unusedKeys = usage?.unused.orEmpty(),
            definedCount = usage?.definedCount ?: 0,
            usedCount = usage?.usedCount ?: 0,
            warning = warning,
        )
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

        // 双击某行 -> 打开 key 编辑弹窗
        panel.onEditKey = { key -> openKeyEditor(project, key, service) }

        val manager = ContentFactory.getInstance()
        val content = manager.createContent(panel, "翻译对照", false)
        toolWindow.contentManager.addContent(content)

        val missingPanel = MissingKeysPanel()
        val missingContent = manager.createContent(missingPanel, "缺失检测", false)
        toolWindow.contentManager.addContent(missingContent)

        val unsubscribe = service.subscribe { snapshot ->
            // ToolWindow 已关闭时不再重绘，避免对已 dispose 的组件操作
            if (toolWindow.isDisposed) return@subscribe
            panel.setLocales(snapshot.sourceLocale, snapshot.displayLocales)
            panel.setRows(snapshot.rows)
            panel.setStatus(snapshot.warning)
            missingPanel.setData(
                missing = snapshot.missingKeys.map { it.key to it.files },
                unused = snapshot.unusedKeys,
                definedCount = snapshot.definedCount,
                usedCount = snapshot.usedCount,
            )
            // 缺失列表双击同样能编辑：既然检测出来了，就该能就地补齐
            missingPanel.onEditKey = { key -> openKeyEditor(project, key, service) }
        }

        Disposer.register(toolWindow.disposable) { unsubscribe() }
        service.refresh()
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

/**
 * 打开 key 编辑弹窗。
 *
 * 从面板服务读当前数据而不是重新扫盘：用户刚刷新过，数据是最新的，
 * 也避免每次双击都重新遍历项目。
 */
private fun openKeyEditor(project: Project, key: String, service: I18nPanelService) {
    val context = I18nContext.of(project)
    val repo = context.repository() ?: return
    val snapshot = service.snapshot()

    val locales = snapshot.displayLocales
    // 缺失的 key 用 null 表示「语言包里根本没有」，弹窗据此提示"保存后创建"
    val values: Map<String, String?> = locales.associateWith { repo.valuesOf(it)[key] }

    KeyEditorDialog(
        project = project,
        key = key,
        sourceLocale = snapshot.sourceLocale,
        values = values,
        locales = locales,
        localeFiles = locales.mapNotNull { l -> repo.localeFile(l)?.let { l to it } }.toMap(),
        onSaved = {
            // 保存后立即刷新，让用户看到状态变化
            service.refresh()
        },
    ).show()
}

/** 便捷入口：主动打开侧边栏面板。 */
fun Project.showI18nToolWindow() {
    ToolWindowManager.getInstance(this)
        .getToolWindow(I18nToolWindowFactory.TOOL_WINDOW_ID)
        ?.activate(null, true)
}