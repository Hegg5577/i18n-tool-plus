# i18n JetBrains 插件 - 对话查阅索引

> 快速查找当前开发进度和相关信息

## 📍 当前状态

- **项目名称**: i18n Helper for JetBrains
- **包名**: com.hegg5577.i18n（已迁移）
- **功能完成度**: 90%
- **最后更新**: 2026-10-19 
- **分支**: master

---

## 🗺️ 对话历史导航

### 这一轮开发新增的功能

| 序号 | 功能 | 提交 | 文档 |
|------|------|------|------|
| 1 | YAML 配置文件支持 | commit 80a8da9 | [docs/YAML_CONFIG.md](docs/YAML_CONFIG.md) |
| 2 | 批量操作（删除/重命名 Key） | commit 1b7f355 | [BATCH_OPERATIONS_IMPLEMENTATION.md](BATCH_OPERATIONS_IMPLEMENTATION.md) |
| 3 | 包名迁移到 hegg5577 | commit 754a2d5 | 无 |
| 4 | 完整项目文档 | 无提交 | [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md) |

### 前期已完成的功能（来自 git history）

| 功能 | 状态 | 相关代码 |
|------|------|--------|
| 多语言对照表 | ✅ | `src/main/kotlin/com/hegg5577/i18n/ui/TranslationTablePanel.kt` |
| 缺失/过期检测 | ✅ | `src/main/kotlin/com/hegg5577/i18n/locale/LocaleRepository.kt` |
| Key 编辑与保存 | ✅ | `src/main/kotlin/com/hegg5577/i18n/ui/KeyEditorDialog.kt` |
| 多翻译引擎 | ✅ | `src/main/kotlin/com/hegg5577/i18n/translator/` |
| Settings 配置面板 | ✅ | `src/main/kotlin/com/hegg5577/i18n/settings/` |
| 硬编码字符串提取 | ✅ | `src/main/kotlin/com/hegg5577/i18n/actions/ExtractStringIntention.kt` |
| Hover 多语言预览 | ✅ | `src/main/kotlin/com/hegg5577/i18n/editor/I18nHoverDocumentationProvider.kt` |
| Key 重命名 | ✅ | `src/main/kotlin/com/hegg5577/i18n/actions/I18nRenameKeyAction.kt` |

---

## 📚 快速查阅索引

### "如何使用这个插件"

👉 读这个：[README.md](README.md)

### "项目完整进度是什么"

👉 读这个：[PROJECT_PROGRESS.md](PROJECT_PROGRESS.md)

### "YAML 配置怎么用"

👉 读这个：[docs/YAML_CONFIG.md](docs/YAML_CONFIG.md)

👉 查看示例：[.i18nrc.example.yml](.i18nrc.example.yml)

### "批量操作怎么实现的"

👉 读这个：[BATCH_OPERATIONS_IMPLEMENTATION.md](BATCH_OPERATIONS_IMPLEMENTATION.md)

### "包名已经改了，旧的 com.up3d.i18n 哪里找"

已完全替换为 `com.hegg5577.i18n`。查看提交 commit 754a2d5

### "还缺什么功能"

只缺 **10%**：

1. **PsiReference 完整集成** — 代码中的引用自动更新
2. **增量扫描** — 性能优化（当前全量扫描）
3. **翻译质量检查** — 参数一致性验证
4. **代码生成** — TypeScript 类型定义

详见 [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md#后续计划)

---

## 🔍 代码位置速查

### 核心功能实现

| 功能 | 文件位置 |
|------|--------|
| 表格多选 | `src/main/kotlin/com/hegg5577/i18n/ui/TranslationTablePanel.kt:63` |
| 批量删除 | `src/main/kotlin/com/hegg5577/i18n/actions/BatchDeleteKeysAction.kt` |
| 批量重命名 | `src/main/kotlin/com/hegg5577/i18n/actions/BatchRenameKeysAction.kt` |
| YAML 加载 | `src/main/kotlin/com/hegg5577/i18n/config/I18nYamlConfigLoader.kt` |
| 删除/重命名逻辑 | `src/main/kotlin/com/hegg5577/i18n/locale/TranslationSaver.kt:84+` |

### 数据模型

| 类 | 职责 |
|---|----|
| `I18nSettings` | 配置管理 + YAML 合并 |
| `LocaleRepository` | 语言包文件操作 |
| `TranslationSaver` | 保存/删除/重命名逻辑 |
| `TranslationTablePanel` | 主UI表格（支持多选） |
| `BatchOperationDialog` | 批量操作确认对话框 |

### 单元测试

| 测试 | 覆盖范围 |
|-----|--------|
| `I18nYamlConfigLoaderTest` | YAML 解析 |
| `TranslationSaverBatchOperationsTest` | 删除/重命名 JSON 逻辑 |

---

## 📋 功能对标 i18n-ally-next

| 功能 | i18n-ally-next | 本插件 | 进度 |
|------|---|---|---|
| 多语言对照表 | ✅ | ✅ | 100% |
| Hover 预览 | ✅ | ✅ | 100% |
| 一键提取 | ✅ | ✅ | 100% |
| 批量翻译 | ✅ | ✅ | 100% |
| Key 重命名 | ✅ | ✅ | 100% |
| 批量删除 | ✅ | ✅ | 100% |
| 批量重命名 | ✅ | ✅ | 100% |
| YAML 配置 | ✅ | ✅ | 100% |
| 代码引用自动更新 | ✅ | ⏳ | 0%（计划中） |
| 增量扫描 | ✅ | ⏳ | 0%（计划中） |
| 翻译质量检查 | ✅ | ⏳ | 0%（计划中） |

---

## 🔄 当前迭代总结

### 新增功能（这一轮）

1. ✅ **YAML 配置文件支持**
   - 项目根目录 `.i18nrc.yml`
   - 团队配置共享
   - 配置优先级 YAML > Settings > 默认值

2. ✅ **批量操作**
   - 表格多选支持
   - 批量删除 Key
   - 批量重命名 Key（前缀/后缀/正则）

3. ✅ **完整文档**
   - README.md — 用户使用指南
   - PROJECT_PROGRESS.md — 完整功能清单
   - 此文件 — 对话查阅索引

4. ✅ **包名整理**
   - com.up3d.i18n → com.hegg5577.i18n
   - 152 处引用已更新

---

## 🎯 关键里程碑

```
第 1-4 阶段：基础框架 ✅
第 5 阶段：删除 .js 支持 ✅
第 6 阶段：多翻译引擎 ✅
第 7 阶段：Settings UI ✅
第 8 阶段：硬编码提取 ✅
第 9 阶段：Hover 预览 ✅
第 10 阶段：Key 重命名 ✅
第 11 阶段：YAML 配置 ✅ (这一轮)
第 12 阶段：批量操作 ✅ (这一轮)
━━━━━━━━━━━━━━━━━━━━━━
总进度：90%
```

---

## 📞 快速帮助

**Q: 代码在哪里？**  
A: `src/main/kotlin/com/hegg5577/i18n/` （已从 com.up3d 改为 com.hegg5577）

**Q: 怎么运行/构建？**  
A: `./gradlew build` （需要 Java 21 + Gradle）

**Q: 单元测试怎么跑？**  
A: `./gradlew test` 或在 IDE 中右键测试类

**Q: 发现 Bug 怎么办？**  
A: 提交 Issue 或检查日志 Help → Show Log

**Q: 想扩展功能怎么办？**  
A: 参考 [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md#技术架构) 的架构说明

**Q: 下一个要做什么功能？**  
A: 见 [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md#后续计划)

---

## 📌 重要文件清单

```
项目根目录
├── README.md ................................. 用户使用指南
├── PROJECT_PROGRESS.md ...................... 完整功能清单
├── CONVERSATION_INDEX.md .................... 本文件（对话查阅）
├── .i18nrc.example.yml ...................... YAML 配置示例
│
├── docs/
│   └── YAML_CONFIG.md ....................... YAML 完整文档
│
├── BATCH_OPERATIONS_IMPLEMENTATION.md ....... 批量操作实现细节
├── YAML_CONFIG_IMPLEMENTATION.md ............ YAML 实现细节
│
├── src/main/kotlin/com/hegg5577/i18n/
│   ├── actions/ .............................. Action 命令
│   ├── config/ ............................... YAML 配置加载
│   ├── editor/ ............................... 编辑器集成
│   ├── locale/ ............................... 语言包操作
│   ├── settings/ ............................. 配置管理
│   ├── translator/ ........................... 翻译引擎
│   ├── ui/ ................................... UI 组件
│   └── startup/ .............................. 启动钩子
│
├── src/test/kotlin/com/hegg5577/i18n/
│   ├── config/ ............................... YAML 测试
│   └── locale/ ............................... 语言包操作测试
│
└── build.gradle.kts .......................... 构建配置
```

---

## 💾 最新 commit 信息

```
754a2d5  chore: 包名迁移 com.up3d.i18n → com.hegg5577.i18n
1b7f355  feat: 批量操作 - 多选表格、删除/重命名 Key
80a8da9  feat: YAML 配置文件支持 - .i18nrc.yml 项目级配置
```

---

**作者**: hegg5577  
**最后更新**: 2026-10-19  
**版本**: 0.1.0
