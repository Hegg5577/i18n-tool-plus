package com.up3d.i18n.ui

import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

/**
 * 多语言翻译对照表。
 *
 * 表格结构对齐原版 i18n-ally 的 "manage" 视图：左侧 key，右侧每种语言一列，
 * 单元格内用「状态标记 + 译文」呈现，缺失 / 空 / 过期一眼可辨。
 *
 * 用 [JBTable] 而非手写 Swing，是为了让排序、行高、主题适配、选中态全部走平台样式，
 * 避免又做成一个"看起来像原生但处处不对"的控件。
 */
class TranslationTablePanel(
    private var sourceLocale: String,
    private var displayLocales: List<String>,
) : JPanel(BorderLayout()) {

    private var model = RowTableModel(sourceLocale, displayLocales)
    private val table = JBTable(model)
    private val renderer = TranslationCellRenderer()

    /** 当前聚焦的 key（跟随选中行变化，供外部做跳转 / 翻译）。 */
    var selectedKey: String? = null
        private set

    init {
        table.rowHeight = JBUI.scale(26)
        table.showHorizontalLines = false
        table.showVerticalLines = false
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.tableHeader.reorderingAllowed = false
        table.fillsViewportHeight = true

        table.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                val row = table.selectedRow
                selectedKey = if (row >= 0) model.rowAt(row)?.key else null
            }
        }

        table.setDefaultRenderer(Any::class.java, renderer)

        add(JBScrollPane(table), BorderLayout.CENTER)
        border = JBUI.Borders.empty()
    }

    /** 更新语言列表。语言集合变化时（如新增语言包）调用，会重建列。 */
    fun setLocales(source: String, displays: List<String>) {
        if (source == sourceLocale && displays == displayLocales) return
        sourceLocale = source
        displayLocales = displays
        renderer.sourceLocale = source

        val previous = model
        model = RowTableModel(source, displays)
        table.model = model
        // 换 model 会清空选中，把原来的行数据接回去，避免刷新后表格闪空
        model.update(previous.currentRows())
    }

    /** 更新表格内容。 */
    fun setRows(rows: List<TranslationRow>) {
        model.update(rows)
    }

    /** 按 key 选中一行；不存在则清除选中。 */
    fun selectKey(key: String?) {
        if (key == null) {
            table.clearSelection()
            return
        }
        val idx = model.indexOfKey(key)
        if (idx >= 0) {
            table.selectionModel.setSelectionInterval(idx, idx)
            table.scrollRectToVisible(table.getCellRect(idx, 0, true))
        }
    }

    /** 当前选中的整行数据。 */
    fun selectedRow(): TranslationRow? =
        table.selectedRow.takeIf { it >= 0 }?.let { model.rowAt(it) }

    /** 供外部与测试读取的表格引用。 */
    fun asTable(): JBTable = table

    /**
     * 表格模型。
     *
     * 第 0 列是 key（普通字符串，交给默认渲染器），其余列是 [CellData]，
     * 由 [TranslationCellRenderer] 处理状态色与占位符。
     */
    private class RowTableModel(
        private val sourceLocale: String,
        private val displayLocales: List<String>,
    ) : AbstractTableModel() {

        /** 列顺序：源语言固定在第一列作为基准，其余按传入顺序。 */
        private val localeOrder: List<String> = listOf(sourceLocale) + displayLocales
        private val columns: List<String> = listOf("Key") + displayLocales.map { Flags.displayName(it) }

        private var rows: List<TranslationRow> = emptyList()

        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String =
            if (column == 0) "Key" else "${Flags.emoji(localeOrder[column])} ${columns[column]}"

        /** 第 0 列返回 String，其余返回 CellData 以走自定义渲染器。 */
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == 0) String::class.java else Any::class.java

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            if (columnIndex == 0) return row.key
            return CellData(row, localeOrder[columnIndex])
        }

        fun update(newRows: List<TranslationRow>) {
            rows = newRows
            fireTableStructureChanged()
        }

        fun rowAt(index: Int): TranslationRow? = rows.getOrNull(index)

        fun indexOfKey(key: String): Int = rows.indexOfFirst { it.key == key }

        fun currentRows(): List<TranslationRow> = rows
    }

    companion object {
        fun preferredPanelSize(): Dimension = Dimension(JBUI.scale(860), JBUI.scale(520))
    }
}