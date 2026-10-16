package com.up3d.i18n.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * 翻译对照表的单元格渲染器。
 *
 * 状态标记 + 译文文本，前景色随状态变化：
 * 已翻译与源语言用正常色，空/缺失/过期用灰色，一眼可辨。
 *
 * 刻意只用最基础的 Swing 组件组装，不依赖 `SimpleTextAttributes` /
 * `JBUI.CurrentTheme` 等平台内部 API —— 这些在不同平台版本间签名会变，
 * 踩中就是编译失败。这里用 `JBColor` + `UIUtil` 取颜色和字体，是最稳的组合。
 */
internal class TranslationCellRenderer : TableCellRenderer {

    /** 基准列（源语言）由外层面板注入。 */
    var sourceLocale: String = "en"

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val data = value as? CellData
        val locale = data?.locale.orEmpty()
        val isSource = data != null && locale == sourceLocale
        val status = data?.row?.status(locale, sourceLocale) ?: TranslationStatus.MISSING

        val translated = data?.row?.values?.get(locale).orEmpty()
        val text = when (status) {
            TranslationStatus.MISSING -> "<缺失>"
            TranslationStatus.EMPTY -> "<空>"
            else -> translated
        }
        val prefix = if (isSource) "" else "${status.marker} "

        // 源语言列与已翻译项用正常色；空/缺失/过期用灰色
        val dimmed = !isSource && status != TranslationStatus.TRANSLATED

        val label = JLabel(prefix + text).apply {
            font = UIUtil.getLabelFont()
            foreground = when {
                isSelected && table != null -> table.selectionForeground
                dimmed -> JBColor.GRAY
                else -> JBColor.foreground()
            }
        }

        return JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            border = if (isSource) {
                // 基准列右侧一条竖线作为视觉锚点
                JBUI.Borders.customLineRight(JBColor.border())
            } else {
                JBUI.Borders.empty(2, 4)
            }
            add(label)
        }
    }
}

/** 单元格数据：行 + 该列对应的 locale。 */
internal data class CellData(val row: TranslationRow, val locale: String)