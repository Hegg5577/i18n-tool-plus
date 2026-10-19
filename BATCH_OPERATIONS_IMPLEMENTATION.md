# 批量操作功能实现 - 总结

## 功能概述

实现了多选表格 + 批量删除/重命名功能。允许用户在侧边栏表格中选中多个 key，一次性删除或重命名。

## 实现细节

### 1. 表格多选支持

**文件**: `TranslationTablePanel.kt`

**变更**:
- `ListSelectionModel.SINGLE_SELECTION` → `MULTIPLE_INTERVAL_SELECTION`
- 新增 `onSelectionChanged` 回调，多个 key 选中时触发
- 新增 `getSelectedKeys()` 方法，获取所有选中的 key 列表

### 2. 批量操作确认对话框

**文件**: `BatchOperationDialog.kt`

**功能**:
- 显示操作范围（影响的 key 数量、语言数量）
- 列出前 20 个受影响的 key，超出部分显示数量
- 警告提示（可通过 Ctrl+Z 撤销）
- 支持 DELETE 和 RENAME 两种操作类型

### 3. 删除操作支持

**文件**: `TranslationSaver.kt` 新增方法

**核心方法**:
- `deleteKeys(project, repo, keysToDelete)` - 删除指定 key 列表
- `deleteNested(root, dottedKey)` - 从 JSON 中删除嵌套路径的 key
- 自动清理空的中间层级（从底向上）

**特性**:
- 走 `WriteCommandAction`，支持 Undo/Redo
- 删除失败不影响其他 key
- 返回成功/失败结果

### 4. 重命名操作支持

**文件**: `TranslationSaver.kt` 新增方法

**核心方法**:
- `renameKey(project, repo, oldKey, newKey)` - 重命名单个 key
- `renameKeyInJson(sourceText, oldKey, newKey)` - JSON 级重命名
- `getNestedValue(root, dottedKey)` - 读取嵌套 key 的值

**特性**:
- 读取旧 key 的值 → 删除旧 key → 写入新 key
- 保持 JSON 结构完整
- 同样支持 Undo/Redo

### 5. 批量操作 Action

#### `BatchDeleteKeysAction`

**功能**:
- 从 Tools 菜单触发
- 检查是否有选中的 key
- 弹出确认对话框
- 执行删除并刷新表格

#### `BatchRenameKeysAction`

**功能**:
- 单个 key：弹出输入框直接重命名
- 多个 key：提供 4 种批量操作方式：
  1. **前缀替换** - `old_prefix_*` → `new_prefix_*`
  2. **后缀替换** - `*_old_suffix` → `*_new_suffix`
  3. **正则替换** - 自定义正则表达式替换
  4. **取消**

**特性**:
- Action 菜单文本动态显示选中数量
- 只有有选中时才启用
- 所有操作支持撤销

### 6. LocaleRepository 扩展

**文件**: `LocaleRepository.kt`

**新增方法**:
- `localeFiles()` - 返回所有语言及其对应的文件

### 7. 单元测试

**文件**: `TranslationSaverBatchOperationsTest.kt`

**测试覆盖**:
- 删除嵌套 key
- 删除最后一个 key 时自动清理空父层
- 重命名 key 的值迁移
- 删除深层嵌套路径
- 删除不存在的 key 不报错
- 读取嵌套 key 的值

## 使用流程

### 批量删除

1. 侧边栏表格中 Ctrl/Cmd + 点击选中多个 key
2. Tools 菜单 → "i18n: 批量删除 Key"
3. 确认对话框显示影响范围
4. 点击「删除」确认执行
5. 成功后通知栏显示"成功删除 X 个语言的 Key"
6. Ctrl+Z 撤销

### 批量重命名

**单个 key**:
1. 选中一个 key
2. Tools 菜单 → "i18n: 批量重命名 Key"
3. 弹出输入框，输入新 key 名
4. 确认执行

**多个 key**:
1. 选中多个 key
2. Tools 菜单 → "i18n: 批量重命名 Key"
3. 选择批量操作方式（前缀/后缀/正则）
4. 输入旧值和新值
5. 确认对话框
6. 逐个执行重命名

## 架构亮点

1. **保持现有模式** - 删除/重命名都走 `WriteCommandAction`，与现有编辑操作一致
2. **JSON 结构保护** - 自动清理空层级，避免污染文件结构
3. **用户友好** - 多种确认机制，防止误操作
4. **灵活的批量重命名** - 支持多种模式（前缀、后缀、正则）
5. **完整的测试** - 覆盖核心的删除/重命名逻辑

## 已知限制

1. 当前删除/重命名时，代码中的 key 引用不会自动更新（仅更新语言包）
   - 后续可考虑集成 PsiReference 完整重构（需要 IDE refactoring 框架）
2. 大量 key 操作时可能需要进度条（当前无超时保护）
3. 批量重命名时，新 key 与现有 key 冲突不会提前检测

## 后续改进

- [ ] 集成 PsiReference 自动重构代码中的引用
- [ ] 支持进度条和操作超时
- [ ] 冲突检测（新 key 已存在时警告）
- [ ] 批量操作撤销时的合并处理
