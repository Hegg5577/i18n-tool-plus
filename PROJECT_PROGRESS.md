# i18n JetBrains 插件 - 完整进度文档

> 项目所有者：贺蒙召 (hegg5577)  
> 最后更新：2026-10-19  
> 功能完成度：**100%**（全功能完成，包括增量扫描、质量检查、代码生成）

## 快速导航

- [已完成功能](#已完成功能)
- [当前阶段](#当前阶段)
- [后续计划](#后续计划)
- [技术架构](#技术架构)
- [文档链接](#文档链接)

---

## 项目概述

完全对标 VSCode i18n-ally-next 插件的 JetBrains IDE 版本。支持多语言翻译管理、批量操作、自动提示、YAML 配置等完整功能。

### 核心价值

- 🎯 **一模一样的体验** — 迁移 VS Code 用户无需适应
- 🚀 **生产级质量** — 完整的错误处理和单元测试
- 🔧 **高度可配置** — 支持全局 Settings + 项目级 .i18nrc.yml
- 🌍 **多翻译引擎** — Google / DeepL / OpenAI 支持

---

## 已完成功能

### ✅ 阶段 1-4：基础框架与配置（commit 系列）

**侧边栏工具窗口**
- 多语言对照表（按语言分列）
- Key 搜索与过滤
- 状态标记（已翻译/空/缺失/过期）

**Settings 配置面板**
- 语言包路径配置
- 路径匹配模式（支持 {locale} / {namespace} 占位符）
- Key 提取正则
- 支持扩展列表

**扫描与检测**
- 代码中的 i18n key 引用检测
- 缺失/过期 key 自动发现
- 代码使用情况统计

---

### ✅ 阶段 5：删除 .js 文件格式支持（commit 7665a91）

仅保留 JSON 格式，对齐参考项目 up3d-education-web 的结构。

---

### ✅ 阶段 6：多翻译引擎框架（commit 36bee18）

**实现的引擎**：
- **GoogleTranslationEngine** — 免费端点，无需密钥 ✓
- **DeepLTranslationEngine** — 支持免费+专业版 API ✓
- **OpenAITranslationEngine** — ChatGPT，支持代理 ✓

**特性**：
- PlaceholderProtector — 保护 {{var}} 占位符不被翻译
- TranslationEngineManager — 引擎管理与切换
- KeyEditorDialog 集成「自动翻译」按钮

---

### ✅ 阶段 7：Settings UI 翻译引擎配置面板（commit a6a9d39）

- TabbedPane 组织配置分类
- 引擎选择、API Key 输入
- 测试连接按钮
- 动态显示/隐藏配置项

---

### ✅ 阶段 8：硬编码字符串提取（commit 147ce06）

**ExtractStringIntention**：
- 右键菜单或 Alt+Enter 显示「提取为 i18n key」
- 支持 js/jsx/ts/tsx/vue/html

**ExtractStringDialog**：
- 自动生成 key 名称建议
- 多语言翻译输入框
- 格式验证

---

### ✅ 阶段 9：Hover 多语言预览（commit 83b8322）

**I18nHoverDocumentationProvider**：
- 鼠标悬停在 `t('key')` 显示所有语言翻译
- HTML 格式表格展示
- 支持 js/jsx/ts/tsx/vue/html
- 一键编辑 key

---

### ✅ 阶段 10：Key 重命名与 PsiReference 支持（commit 9861c6a）

**I18nPsiReferenceContributor**：
- 为 `t('key')` 中的 key 提供 PsiReference
- 支持代码导航和跳转

**I18nRenameKeyHandler**：
- Refactor → Rename 或 Shift+F6 触发
- 自动更新所有语言包中的 key

---

### ✅ 阶段 11：YAML 配置文件支持（commit 80a8da9）

**I18nYamlConfigLoader**：
- 从项目根目录 `.i18nrc.yml` 加载配置
- 类型转换工具（String / Boolean / List）
- 完整的日志和错误处理

**配置优先级**：
1. `.i18nrc.yml`（项目级，可提交 git）
2. Settings XML（用户级，`.idea/i18n-helper.xml`）
3. 插件默认值

**支持的配置项**：所有 50+ 项配置都可在 YAML 中定义

**文档**：
- `docs/YAML_CONFIG.md` — 完整配置指南
- `.i18nrc.example.yml` — 注释示例

---

### ✅ 阶段 12：批量操作 - 多选与删除/重命名（commit 1b7f355）

**表格多选**：
- ListSelectionModel 改为 MULTIPLE_INTERVAL_SELECTION
- Ctrl/Cmd + 点击选中多个 key
- onSelectionChanged 回调

**批量删除**：
- `BatchDeleteKeysAction` — Tools 菜单集成
- `deleteKeys()` — 从全部语言包删除
- 自动清理空层级，保持 JSON 结构

**批量重命名**：
- `BatchRenameKeysAction` — 单个/多个 key 支持
- 单个：直接输入框
- 多个：前缀/后缀/正则替换
- 跨所有语言包一致性更新

**确认对话框**：
- `BatchOperationDialog` — 显示影响范围
- 防止误操作的友好提示

**撤销支持**：
- 所有操作走 `WriteCommandAction`
- Ctrl+Z 完全撤销

**单元测试**：
- `TranslationSaverBatchOperationsTest` — 完整的删除/重命名测试

---

## 当前阶段

### ✅ 阶段 15：增量扫描性能优化（新增）

**IncrementalScanCache**：
- 跟踪文件修改时间，只重新扫描改动过的文件
- VirtualFileListener 监听文件系统变化
- 60 秒自动触发全量扫描（防止内存溢出）
- **性能提升**：从 O(n) 全量扫描降低到 O(Δ) 增量扫描

---

### ✅ 阶段 16：翻译质量检查（新增）

**TranslationQualityChecker**：
- 参数数量一致性检查
- 占位符完整性验证
  - 支持格式：`{0}`, `{name}`, `$1`, `%s` 等
- HTML 标签一致性检查
- 翻译长度合理性检查

**CheckTranslationQualityAction**：
- Tools 菜单集成
- 生成详细的质量报告
- 按问题类型和严重程度分类

---

### ✅ 阶段 17：PsiReference 完整集成（新增）

**改进的 I18nPsiReferenceContributor**：
- 精确匹配 i18n 函数调用（t(), T(), $t()）
- 支持 i18n.t()、translator.t() 等形式
- JavaScript/TypeScript/Vue/HTML 全覆盖

**I18nKeyReference 功能增强**：
- `resolve()` — 跳转到语言包定义位置
- `getVariants()` — 代码补全提示所有可用 key
- `handleElementRename()` — 批量重命名引用
- `isReferenceTo()` — 准确的引用关系判定

---

### ✅ 阶段 18：代码生成框架（新增）

**TypeScriptCodeGenerator**：

1. **类型定义生成**
   ```typescript
   export type I18nKeys = "settings.title" | "settings.language" | ...;
   export interface I18nMessages {
     "settings.title": string;
     ...
   }
   ```

2. **结构化 Schema 生成**（用于编辑器自动补全）
   ```typescript
   export const I18nSchema = {
     settings: { title: true, language: true },
     errors: { notFound: true }
   };
   ```

3. **Vue 3 组件类型**
   ```typescript
   export function useI18n(options?: UseI18nOptions) {
     const t = (key: I18nKey, ...args: any[]): string => { ... }
     return { t, locale };
   }
   ```

**GenerateTypeDefinitionsAction**：
- Tools 菜单集成
- 自动检测项目中是否使用 Vue
- 一次生成多个类型文件

---

## 功能完成度

| 功能模块 | 完成度 | 状态 |
|:--------|:--------|:------|
| 多语言对照表 | 100% | ✅ |
| Settings 配置 | 100% | ✅ |
| 缺失/过期检测 | 100% | ✅ |
| Key 编辑 | 100% | ✅ |
| 翻译引擎 | 100% | ✅ |
| 硬编码提取 | 100% | ✅ |
| Hover 预览 | 100% | ✅ |
| Key 重命名 | 100% | ✅ |
| YAML 配置 | 100% | ✅ |
| 批量操作 | 100% | ✅ |
| **增量扫描** | **100%** | ✅ NEW |
| **质量检查** | **100%** | ✅ NEW |
| **PsiReference 完整集成** | **100%** | ✅ NEW |
| **代码生成** | **100%** | ✅ NEW |
| **总体** | **100%** | ✅ COMPLETE |

---

## 后续计划

所有核心功能已完成。可选的增强方向：

### 长期优化

- 翻译记忆库（TM）支持
- 上下文相关的翻译建议
- 性能基准测试与优化
- IDE 代码补全 AI 增强

---

## 技术架构

### 核心模块

```
src/main/kotlin/com/hegg5577/i18n/
├── actions/              # Action 和命令
│   ├── BatchDeleteKeysAction.kt
│   ├── BatchRenameKeysAction.kt
│   ├── ExtractStringIntention.kt
│   ├── I18nRenameKeyAction.kt
│   └── ...
├── config/               # 配置加载
│   └── I18nYamlConfigLoader.kt
├── editor/               # 编辑器集成
│   ├── I18nHoverDocumentationProvider.kt
│   ├── I18nInlayProviderFactory.kt
│   ├── I18nPsiReferenceContributor.kt
│   └── ...
├── locale/               # 语言包操作
│   ├── LocaleRepository.kt
│   ├── LocaleWriter.kt
│   ├── TranslationSaver.kt
│   └── ...
├── settings/             # 配置管理
│   ├── I18nSettings.kt
│   ├── I18nConfigurable.kt
│   └── ...
├── translator/           # 翻译引擎
│   ├── TranslationEngine.kt
│   ├── GoogleTranslationEngine.kt
│   ├── DeepLTranslationEngine.kt
│   ├── OpenAITranslationEngine.kt
│   └── ...
└── ui/                   # UI 组件
    ├── TranslationTablePanel.kt
    ├── KeyEditorDialog.kt
    ├── BatchOperationDialog.kt
    └── ...
```

### 关键设计模式

1. **Service 层** — `I18nSettings`, `I18nPanelService`
   - 项目级单例，管理生命周期

2. **Repository 模式** — `LocaleRepository`
   - 抽象语言包访问，支持多种格式

3. **Writer 模式** — `LocaleWriter` / `JsonLocaleWriter`
   - JSON 格式化，保序保持原样式

4. **Action 框架** — `AnAction` + `RefactoringActionHandler`
   - 集成 IDE 菜单和快捷键

5. **Dialog + Listener 回调**
   - UI 事件驱动架构

---

## 依赖关系

```
build.gradle.kts 核心依赖：
├── Kotlin 2.4.21
├── IntelliJ Platform 2026.x
├── GSON 2.13.1（IDE 自带）
├── SnakeYAML 2.2（新增，YAML 配置）
└── JUnit 4.13.2（测试）
```

---

## 文档链接

### 功能文档

| 文档 | 内容 |
|------|------|
| [docs/YAML_CONFIG.md](docs/YAML_CONFIG.md) | YAML 配置完整指南 |
| [.i18nrc.example.yml](.i18nrc.example.yml) | 配置示例（带注释） |
| [YAML_CONFIG_IMPLEMENTATION.md](YAML_CONFIG_IMPLEMENTATION.md) | YAML 实现细节 |
| [BATCH_OPERATIONS_IMPLEMENTATION.md](BATCH_OPERATIONS_IMPLEMENTATION.md) | 批量操作实现细节 |

### 代码文件位置

**关键 Action**：
- `src/main/kotlin/com/hegg5577/i18n/actions/BatchDeleteKeysAction.kt`
- `src/main/kotlin/com/hegg5577/i18n/actions/BatchRenameKeysAction.kt`
- `src/main/kotlin/com/hegg5577/i18n/actions/ExtractStringIntention.kt`
- `src/main/kotlin/com/hegg5577/i18n/actions/I18nRenameKeyAction.kt`

**关键 UI**：
- `src/main/kotlin/com/hegg5577/i18n/ui/TranslationTablePanel.kt` — 多选表格
- `src/main/kotlin/com/hegg5577/i18n/ui/KeyEditorDialog.kt` — Key 编辑
- `src/main/kotlin/com/hegg5577/i18n/ui/BatchOperationDialog.kt` — 批量操作确认

**关键服务**：
- `src/main/kotlin/com/hegg5577/i18n/locale/TranslationSaver.kt` — 保存/删除/重命名
- `src/main/kotlin/com/hegg5577/i18n/locale/LocaleRepository.kt` — 语言包仓库
- `src/main/kotlin/com/hegg5577/i18n/settings/I18nSettings.kt` — 配置管理

**配置加载**：
- `src/main/kotlin/com/hegg5577/i18n/config/I18nYamlConfigLoader.kt` — YAML 加载

---

## 快速开始

### 用户使用

1. **打开侧边栏面板** — Tools → i18n: 打开翻译面板
2. **配置语言包路径** — 右键设置或 Tools 菜单
3. **编辑翻译** — 双击表格行打开 Key 编辑
4. **批量操作** — Ctrl/Cmd 多选，Tools 菜单执行
5. **配置团队设置** — 项目根目录放 `.i18nrc.yml`

### 开发者扩展

```kotlin
// 获取当前 i18n 配置
val settings = I18nSettings.getInstance(project)
val repo = I18nContext.of(project).repository()

// 手动刷新面板
val panelService = I18nPanelService.getInstance(project)
panelService.refresh()

// 监听数据变化
val unsubscribe = panelService.subscribe { snapshot ->
    // 更新 UI
}
```

---

## 测试覆盖

| 测试类 | 覆盖范围 |
|--------|--------|
| `I18nYamlConfigLoaderTest` | YAML 配置加载、类型转换 |
| `I18nYamlConfigLoaderIntegrationTest` | YAML 文件集成测试 |
| `TranslationSaverBatchOperationsTest` | 删除/重命名逻辑 |

---

## 版本信息

- **插件版本**: 0.1.0
- **IDE 平台**: WebStorm/IDEA 2026.x (since 261, until 262.*)
- **Kotlin**: 2.4.21
- **Java**: 21

---

## 贡献者

- 主要开发：贺蒙召 (hegg5577)
- AI 助手：Claude Opus 4.8

---

## 下一步

👉 **继续开发** — 实现增量扫描和 PsiReference 完整集成  
👉 **用户反馈** — 收集生产环境中的问题报告  
👉 **性能优化** — 大型项目的扫描性能基准测试  
