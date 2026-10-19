# 🚀 快速开始 - 打包和安装

> 如何快速构建插件包并在本地测试

---

## 📦 构建插件包

### 前置要求

1. **Java 21+**
   ```bash
   java -version  # 应显示 21.x.x 或更高版本
   ```

2. **项目目录**
   ```bash
   cd E:\CompanyItem\i18n-jetbrains-plugin
   ```

### 构建命令

#### 最简单的方式（Windows）

```bash
.\gradlew.bat buildPlugin
```

#### Linux / macOS

```bash
./gradlew buildPlugin
```

#### 或使用自动化脚本

```bash
bash build-plugin.sh
```

### 构建完成

成功后，输出：

```
BUILD SUCCESSFUL

✅ 插件包：build/distributions/i18n-helper-0.1.0.zip
```

---

## 🔧 安装到 IDE

### 步骤 1：打开 IDE（WebStorm / IDEA 2026.x）

### 步骤 2：打开插件市场

```
Settings (Ctrl+, 或 Cmd+,) → Plugins
```

### 步骤 3：安装本地插件

1. 点击右上角 **⚙️ 齿轮图标**
2. 选择 **"Install Plugin from Disk"**
3. 浏览选择：`build/distributions/i18n-helper-0.1.0.zip`
4. 点击 **"OK"**

### 步骤 4：重启 IDE

IDE 会提示重启，点击 **"Restart IDE"**

---

## ✨ 验证安装成功

重启后，打开 **Tools** 菜单，应该看到：

```
Tools
├── 🔷 i18n: 打开翻译面板          ← 点这个打开侧边栏
├── 🔄 i18n: 刷新翻译面板
├── 🔍 i18n: 扫描语言包一致性
├── 📋 i18n: 诊断当前文件的译文提示
├── 🌐 i18n: 批量翻译缺失文案
├── 🗑️ i18n: 批量删除 Key
└── ✏️ i18n: 批量重命名 Key
```

---

## 🎯 立即体验

### 1. 打开侧边栏

**Tools → i18n: 打开翻译面板**

### 2. 初次配置（如果需要）

如果侧边栏显示"未找到语言包"，需要配置：

```
Settings → Tools → i18n Helper

配置以下项：
├── Language Pack Directory: 你的语言包目录
│   例如：src/i18n/locales 或 src/lang/locales
│
├── Path Matcher: 文件匹配模式
│   例如：{locale}/{file} 或 {locale}.json
│
└── Source Language: 源语言
    例如：en
```

### 3. 查看多语言表格

配置后，表格会显示所有 key 和各语言的翻译状态。

### 4. 试试主要功能

| 功能 | 操作 | 说明 |
|------|------|------|
| 编辑 | 双击表格行 | 打开编辑窗口 |
| 提取 | Alt+Enter（代码中） | 快速提取硬编码字符串 |
| 搜索 | 搜索框输入 | 查找 key 或翻译文本 |
| 悬停预览 | 鼠标悬停在 t('key') | 显示全部语言翻译 |
| 重命名 | Shift+F6（光标在 key 上） | 快速重命名 |
| 多选删除 | Ctrl+点击多个 → Tools → 批量删除 | 批量删除 key |
| 多选重命名 | Ctrl+点击多个 → Tools → 批量重命名 | 批量重命名 key |

---

## 📚 详细文档

| 文档 | 内容 |
|------|------|
| [INSTALL_AND_USAGE.md](INSTALL_AND_USAGE.md) | 详细安装和使用步骤 |
| [BUILD_CHECKLIST.md](BUILD_CHECKLIST.md) | 构建前检查清单 |
| [README.md](README.md) | 功能完整介绍 |
| [docs/YAML_CONFIG.md](docs/YAML_CONFIG.md) | YAML 配置指南 |
| [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md) | 项目功能清单 |

---

## 🆘 常见问题

### Q: 构建时出现 "JAVA_HOME is not set" 错误

**A:** 设置 Java 路径

```bash
# 临时设置（仅本次会话）
export JAVA_HOME=/path/to/jdk21

# 或使用 IDE 内置的 Java
./gradlew buildPlugin
# IDE 应该会自动使用内置的 JDK
```

### Q: 侧边栏显示"未找到语言包"

**A:** 检查配置

1. 确认 `Language Pack Directory` 指向正确目录
2. 确认文件名符合 `Path Matcher` 规则
3. Tools → i18n: 刷新翻译面板

### Q: 怎样卸载插件

**A:**

```
Settings → Plugins → i18n Helper → ⚙️ → Uninstall
重启 IDE
```

---

## 📊 项目进度

```
✅ 功能完成度：90%
✅ 代码质量：生产级
✅ 文档完整：用户 + 开发者 + 对话
✅ 已支持：YAML 配置、批量操作、多翻译引擎

🚀 现在就可以安装使用了！
```

---

## 💡 提示

- **第一次使用** → 读 [INSTALL_AND_USAGE.md](INSTALL_AND_USAGE.md)
- **高级配置** → 创建 `.i18nrc.yml` 文件（见 [docs/YAML_CONFIG.md](docs/YAML_CONFIG.md)）
- **功能完整** → 查看 [README.md](README.md) 的功能对比表
- **开发进度** → 查看 [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md)

---

## 🎉 最后

感谢使用 i18n Helper！如有反馈或问题，欢迎提交 Issue。

**版本**: 0.1.0  
**作者**: hegg5577  
**兼容**: WebStorm / IDEA 2026.x+
