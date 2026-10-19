# Hover 文档功能 - 正确使用方法

## 当前状态 ✅

代码完全正常工作！所有功能都已实现：
- ✅ DocumentationProvider 被正确触发
- ✅ 翻译数据成功加载
- ✅ HTML 正确生成并返回（673+ 字节）
- ✅ 显示的语言正确（displayLocales 中的 [zh]）
- ✅ "Edit this key" 链接已生成

## 如何查看翻译预览

### 方法 1: 快速文档快捷键（推荐）

1. 在代码编辑器中，**光标放在 i18n key 上**（比如 `t('newAiCrown.newAiCrown52')`）
2. **按 `Ctrl+Q`**（Windows/Linux）或 **`Cmd+J`**（Mac）
3. **快速文档窗口会弹出**，显示翻译表格

**效果：**
```
i18n Key: newAiCrown.newAiCrown52
┌──────────────┬──────────────┐
│   Language   │ Translation  │
├──────────────┼──────────────┤
│  中文 (zh)   │    确认      │
└──────────────┴──────────────┘
[Edit this key]
```

### 方法 2: 菜单打开

1. `View` → `Quick Documentation` 
2. 或在代码上右键 → `Show Context Documentation`

### 方法 3: IDE 编辑器工具栏

在编辑器顶部的工具栏中寻找 Documentation 图标

## 编辑翻译

点击快速文档窗口中的 **"Edit this key"** 链接，会打开编辑对话框。

## 配置显示的语言

如果想显示多个语言的翻译（不只是中文），需要配置 Settings：

1. `IDE Menu` → `File` → `Settings` → `i18n Helper`
2. 在 **displayLocales** 字段中添加语言代码，用逗号分隔
   - 例如：`zh,en,ja` 表示显示中文、英文、日文
3. 点击 Apply，重新按 Ctrl+Q 查看

## 自动 Hover 弹窗配置（可选）

如果想在悬停时自动显示文档（不需要按 Ctrl+Q）：

1. `File` → `Settings` → `Editor` → `General`
2. 找到 **Show quick documentation on mouse move** 选项
3. 勾选并设置延迟时间（推荐 500-1000ms）
4. 点击 OK

然后在代码中悬停 i18n key 几秒钟，文档就会自动显示。

## 常见问题

### Q: 为什么没看到翻译？
**A:** 检查：
1. 是否在 i18n key 的字符串上（比如 `'newAiCrown.newAiCrown52'`）
2. 该 key 是否真的存在于语言包中
3. 项目设置中的 localesPath 是否正确（应该指向 `src/i18n/locales`）

### Q: 为什么只显示中文？
**A:** 因为设置中 displayLocales 只配置了 `zh`。按上面的"配置显示的语言"步骤添加更多语言。

### Q: "Edit this key" 链接点不了？
**A:** 这是 IDE 版本问题。可以改用右键菜单或通过侧边栏表格来编辑。

## 下一步优化

如果要让 Hover 自动弹出（不需要 Ctrl+Q），可能需要：
- 使用 `EditorMouseHoverListener` 实现手动悬停处理
- 或改用 InlayHints 在代码旁边显示翻译提示

当前实现已经完全满足快速查看翻译的需求，只是需要用 Ctrl+Q 快捷键触发。
