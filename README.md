# i18n Helper - JetBrains 插件

完全对标 VSCode [i18n-ally-next](https://github.com/hemeng召/i18n-ally-next) 的 JetBrains IDE 版本。  
为 WebStorm / IDEA / Rider 等 JetBrains IDE 提供强大的国际化管理能力。

> **开发者**: hegg5577  
> **状态**: 生产级（功能完成度 90%）  
> **平台**: WebStorm / IDEA 2026.x+

---

## ✨ 核心特性

### 📋 多语言对照表
- 侧边栏实时展示所有语言的翻译状态
- 按语言分列，清晰区分：已翻译 / 空值 / 缺失 / 过期
- 搜索 + 过滤 + 排序

### 🔍 智能检测
- 代码中缺失的 key（代码用但语言包没定）
- 语言包中过期的 key（语言包有但代码不用）
- 语言间不一致的 key

### ✏️ 便捷编辑
- **双击编辑** — 表格行打开详情编辑
- **一键提取** — Alt+Enter 将硬编码字符串提取为 i18n key
- **批量操作** — 多选 key 后批量删除/重命名
- **快捷重命名** — Shift+F6 重命名，自动更新所有语言包

### 🌐 多翻译引擎
- **Google Translate** — 免费、无需密钥（推荐）
- **DeepL API** — 专业翻译质量（需 API 密钥）
- **OpenAI ChatGPT** — 支持代理和自建 API

### 🎯 IDE 深度集成
- **Hover 预览** — 鼠标悬停 `t('key')` 显示全部语言翻译
- **代码导航** — 点击 key 跳转到定义
- **行内提示** — 代码中直接显示翻译文本
- **快捷菜单** — 右键集成批量操作

### ⚙️ 灵活配置
- **全局 Settings** — IDE 级配置（Tools → Options/Preferences）
- **项目级 YAML** — `.i18nrc.yml` 配置（可提交 git，团队共享）
- **配置优先级** — 项目 YAML > IDE Settings > 默认值

---

## 🚀 快速开始

### 1. 打开侧边栏面板

**Tools → i18n: 打开翻译面板**

或按 Alt+12 打开右侧 i18n Helper 工具窗口

### 2. 配置语言包路径

首次使用时，Tools → Options/Preferences → Tools → i18n Helper

设置：
- **Language Pack Directory** — 语言包根目录（如 `src/i18n/locales`）
- **Path Matcher** — 文件匹配模式（如 `{locale}/{file}`）
- **Source Language** — 源语言（通常为 `en`）

### 3. 编辑翻译

在表格中：
- **搜索** — 搜索框快速定位 key
- **过滤** — 按状态过滤（全部/待填写/缺失）
- **双击** — 打开 Key 编辑窗口

### 4. 批量操作

Ctrl/Cmd + 点击选中多个 key，然后：
- **Tools → i18n: 批量删除 Key** — 删除选中 key
- **Tools → i18n: 批量重命名 Key** — 前缀/后缀/正则替换

### 5. 配置翻译引擎（可选）

Tools → Options/Preferences → Tools → i18n Helper → 翻译引擎

选择引擎：
- Google（默认，无需密钥）
- DeepL（输入 API 密钥）
- OpenAI（输入 API 密钥）

---

## 📖 详细文档

| 文档 | 内容 |
|------|------|
| [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md) | 完整项目进度和功能清单 |
| [docs/YAML_CONFIG.md](docs/YAML_CONFIG.md) | YAML 配置文件完整指南 |
| [.i18nrc.example.yml](.i18nrc.example.yml) | 配置示例（带详细注释） |
| [BATCH_OPERATIONS_IMPLEMENTATION.md](BATCH_OPERATIONS_IMPLEMENTATION.md) | 批量操作实现细节 |

---

## 🎯 使用场景

### 场景 1：初始化新项目

```bash
# 1. 创建示例配置
cp .i18nrc.example.yml .i18nrc.yml

# 2. 编辑配置
# - 修改 localesPath 为你的语言包目录
# - 修改 displayLocales 为项目使用的语言

# 3. 提交到 git
git add .i18nrc.yml
```

### 场景 2：团队协作翻译

1. 团队提交了 `.i18nrc.yml` 到 git
2. 所有成员打开项目后自动读取团队配置
3. 在侧边栏表格中填充缺失翻译
4. 支持多人同时编辑不同语言

### 场景 3：代码中提取硬编码字符串

```javascript
// 光标在字符串上，Alt+Enter
const msg = "Hello World";
// ↓ 弹出提取对话框
// 输入 key: common.greeting
// 自动翻译后保存
// ↓ 代码自动变为：
const msg = t('common.greeting');
```

### 场景 4：批量重命名 key

```bash
# 选中多个 common_* 的 key，打开批量重命名
# 选择"前缀替换"
# 旧前缀：common_
# 新前缀：common.
# ↓ 自动重命名所有语言包中的相关 key
```

---

## 🔧 技术栈

- **Kotlin 2.4** — 主开发语言
- **IntelliJ Platform 2026.x** — IDE 框架
- **GSON** — JSON 处理
- **SnakeYAML** — YAML 配置解析

---

## 📋 功能清单

| 功能 | 状态 | 说明 |
|------|------|------|
| 多语言对照表 | ✅ | 侧边栏表格 + 搜索 + 过滤 |
| 缺失/过期检测 | ✅ | 自动扫描代码和语言包 |
| Key 编辑 | ✅ | 支持多语言同步编辑 |
| 多翻译引擎 | ✅ | Google / DeepL / OpenAI |
| Settings 面板 | ✅ | 配置所有选项 |
| 硬编码提取 | ✅ | Alt+Enter 快速提取 |
| Hover 预览 | ✅ | 悬停显示全部翻译 |
| Key 重命名 | ✅ | Shift+F6 + 全量更新 |
| 批量删除 | ✅ | 多选后一键删除 |
| 批量重命名 | ✅ | 前缀/后缀/正则替换 |
| YAML 配置 | ✅ | 项目级 .i18nrc.yml |
| **总体** | **90%** | 缺 PsiReference 完整集成 |

---

## 🐛 已知限制

1. **代码引用不自动更新** — 删除/重命名 key 时，代码中的引用需手动更新
   - 后续版本会通过 PsiReference 完整重构解决
   
2. **全量扫描** — 每次刷新都扫描全部文件
   - 大型项目可能需要几秒
   - 后续版本支持增量扫描

3. **正则表达式学习成本** — 批量重命名时的正则替换需要用户学习
   - 提供了前缀/后缀替换作为简化方案

---

## 📞 反馈与贡献

- 发现 Bug 或建议功能？请提交 Issue
- 欢迎提交 PR 改进代码
- 问题排查：检查 IDE 日志（Help → Show Log in Finder/Explorer）

---

## 📜 许可证

MIT License

---

## 🙏 鸣谢

- 原版灵感：[i18n-ally-next](https://github.com/hemeng召/i18n-ally-next) (VS Code)
- 参考项目：[up3d-education-web](https://github.com/up3d/education-web) (i18next + React)

---

**开发者**: hegg5577  
**最后更新**: 2026-10-19  
**版本**: 0.1.0
