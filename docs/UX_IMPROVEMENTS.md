# UX 改进说明

> 2026-10-19 - 翻译对照表与项目配置的优化

## 改进内容

### 1. 表头可拖动重排序

**改进前**：语言列的顺序固定，由配置决定，无法动态调整。

**改进后**：
- ✅ 表头支持拖动重排序
- ✅ 用户可以实时调整语言列的显示顺序
- ✅ 排序顺序只影响当前 IDE 会话（关闭 IDE 后恢复配置顺序）

**使用方式**：
```
在翻译对照表的表头（zh, en, ja 等列）拖动鼠标，即可重排列的顺序。
```

**代码位置**：`ui/TranslationTablePanel.kt:67`

---

### 2. 去掉状态标记（√ 符号），缺失直接空着

**改进前**：
```
| key         | zh        | en        |
|-------------|-----------|-----------|
| common.ok   | 好 (√)   | OK        |
| common.no   | <缺失>   | NO        |
| settings    | 设置 (√)  | Settings  |
```

**改进后**：
```
| key         | zh        | en        |
|-------------|-----------|-----------|
| common.ok   | 好        | OK        |
| common.no   |           | NO        |（直接空着，灰色显示）
| settings    | 设置      | Settings  |
```

**好处**：
- ✅ 界面更干净，减少视觉噪音
- ✅ 缺失/空值一眼可辨（灰色显示）
- ✅ 对齐 VS Code i18n-ally 的设计

**代码位置**：`ui/TranslationCellRenderer.kt:58-61`

---

### 3. 每个项目独立配置

**系统设计**：

```
┌─────────────────────────────────────┐
│  全局 IDE Settings                   │
│  (Preferences > Tools > i18n Helper) │
│  • 默认翻译引擎 API Key              │
│  • 默认扫描目录                      │
└─────────────────────────────────────┘
              ↑ 所有项目默认值
              │
     ┌────────┴────────┐
     ↓                  ↓
┌──────────────┐  ┌──────────────┐
│ Project A    │  │ Project B    │
│ .i18nrc.yml  │  │ .i18nrc.yml  │
│              │  │              │
│ localesPath: │  │ localesPath: │
│   locales/   │  │   src/i18n/  │
│ displayLoc:  │  │ displayLoc:  │
│   [zh,en,ja] │  │   [en,fr,de] │
│ sourceLocale:│  │ sourceLocale:│
│   en         │  │   fr         │
└──────────────┘  └──────────────┘
```

**优先级**（高 → 低）：
1. 项目 `.i18nrc.yml` 配置
2. IDE Settings
3. 插件内置默认值

**优点**：
- ✅ 每个项目完全独立配置
- ✅ `.i18nrc.yml` 可提交到 Git，团队共享
- ✅ 支持多个项目同时打开，互不影响
- ✅ 新成员 clone 项目后自动使用正确的配置

**文档**：`docs/YAML_CONFIG.md`

---

## 配置示例

### 项目 A - 中文优先

```yaml
# project-a/.i18nrc.yml
localesPath: locales
sourceLocale: en
displayLocales:
  - zh
  - en
  - ja
```

### 项目 B - 英文优先

```yaml
# project-b/.i18nrc.yml
localesPath: src/i18n
sourceLocale: fr
displayLocales:
  - en
  - fr
  - de
```

打开各项目时，会自动使用各自的配置。

---

## 实施细节

### TranslationTablePanel 的改动

```kotlin
// 启用表头拖动
table.tableHeader.reorderingAllowed = true  // 原来是 false
table.tableHeader.resizingAllowed = true
```

### TranslationCellRenderer 的改动

```kotlin
// 改进前：显示 <缺失> / <空> + 状态标记 (√)
val text = when (status) {
    TranslationStatus.MISSING -> "<缺失>"
    TranslationStatus.EMPTY -> "<空>"
    else -> value.row.values[value.locale].orEmpty()
}
val prefix = if (isSource) "" else "${status.marker} "

// 改进后：直接显示值，缺失/空就是空字符串，灰色显示
val text = value.row.values[value.locale].orEmpty()
val dimmed = !isSource && status != TranslationStatus.TRANSLATED
// 灰色帮助用户判断是否缺失
```

---

## 使用建议

### 推荐做法

1. **在项目根目录创建 `.i18nrc.yml`**
   ```bash
   touch .i18nrc.yml
   git add .i18nrc.yml
   ```

2. **配置翻译文件位置**
   ```yaml
   localesPath: locales
   pathMatcher: "{locale}/common.json"
   ```

3. **配置显示语言顺序**
   ```yaml
   displayLocales:
     - zh      # 中文放第一位，优先展示
     - en
     - ja
   ```

4. **如需调整顺序，在表格表头拖动即可**
   - 临时调整不会改变 `.i18nrc.yml`
   - 关闭 IDE 后恢复配置顺序

---

## 对标 VS Code i18n-ally-next

| 功能 | i18n-ally-next | JetBrains 版本 |
|:-----|:----------------|:----------------|
| 表头拖动排序 | ✅ | ✅ 新增 |
| 缺失值直接空着 | ✅ | ✅ 新增 |
| 状态标记 | ✅ 有 | ❌ 移除 |
| 项目独立配置 | ✅ | ✅ |
| 多项目支持 | ✅ | ✅ |

---

**完全对齐 VS Code 用户体验** 🎉
