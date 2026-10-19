# 插件安装与使用指南

## 📦 构建插件包

### 前提条件

- **JDK 21+** — 必须安装 Java 21 或更高版本
- **Gradle** — 已随项目包含（使用 gradlew）

### 快速构建

#### 方式 1：使用构建脚本（推荐）

```bash
# Linux / macOS
bash build-plugin.sh

# Windows
.\gradlew.bat buildPlugin
```

#### 方式 2：手动执行

```bash
./gradlew clean buildPlugin
```

### 输出位置

构建完成后，插件包位置为：

```
build/distributions/i18n-helper-0.1.0.zip
```

---

## 🔧 安装插件

### 步骤 1：打开 IDE 插件管理

**WebStorm / IDEA 2026.x**

```
Settings/Preferences → Plugins
```

或使用快捷键：
- **Windows/Linux**: Ctrl + Alt + S → 搜索 Plugins
- **macOS**: Cmd + , → 搜索 Plugins

### 步骤 2：安装本地插件

在 Plugins 窗口中：

1. 点击右上角 **⚙️ 齿轮图标**
2. 选择 **"Install Plugin from Disk"**
3. 浏览到 `build/distributions/i18n-helper-0.1.0.zip`
4. 选中后点击 **"OK"**

### 步骤 3：重启 IDE

IDE 会提示是否立即重启。点击 **"Restart IDE"** 以立即应用插件。

---

## ✨ 首次使用

### 打开侧边栏面板

重启后，依次打开：

```
Tools → i18n: 打开翻译面板
```

或按快捷键组合打开右侧工具窗口（Alt + 12）。

### 初始化配置

**第一次使用时需要配置：**

1. **打开 Settings**
   ```
   Settings/Preferences → Tools → i18n Helper
   ```

2. **配置关键项**（根据你的项目调整）

   | 配置项 | 示例值 | 说明 |
   |--------|--------|------|
   | Language Pack Directory | `src/lang/locales` | 语言包根目录 |
   | Path Matcher | `{locale}/{file}` | 文件匹配模式 |
   | Source Language | `en` | 源语言代码 |
   | Display Locales | `zh, en` | 侧边栏展示语言 |

3. **（可选）配置翻译引擎**

   ```
   Settings → Tools → i18n Helper → 翻译引擎 标签页
   ```

   - 默认：Google Translate（免费，无需密钥）✓
   - 或选择：DeepL / OpenAI（需 API 密钥）

### 创建项目配置文件（团队推荐）

如果是团队项目，在项目根目录创建 `.i18nrc.yml`：

```yaml
localesPath: src/lang/locales
sourceLocale: en
displayLocales:
  - zh
  - en
supportedExtensions:
  - js
  - jsx
  - ts
  - tsx
  - vue
inlineAnnotationEnabled: true
```

提交到 git，所有团队成员会自动使用相同配置。

---

## 🎯 核心功能演示

### 1️⃣ 查看多语言对照表

侧边栏表格显示所有 key 和各语言的翻译状态：

```
key                    │ zh      │ en
─────────────────────────────────────────
common.ok              │ 好的    │ OK
common.cancel          │ 取消    │ Cancel
common.loading         │ [空]    │ Loading
error.network          │ [缺失]  │ Network Error
```

### 2️⃣ 搜索和过滤

- **搜索框** — 输入关键词快速定位
- **过滤按钮** — 查看特定状态的 key
  - 全部
  - 待填写（值为空）
  - 缺失（语言包没有）

### 3️⃣ 编辑翻译

**方法 1：双击表格行**

```
表格中双击任意行 → 打开 KeyEditorDialog → 填写各语言翻译 → 保存
```

**方法 2：从代码中提取**

在 JavaScript/TypeScript 代码中：

```javascript
const msg = "Hello World";  // 光标在这里
// 按 Alt+Enter → 选择"提取为 i18n key" → 输入 key 名称和翻译
```

### 4️⃣ Hover 预览

将鼠标悬停在代码中的 `t('key')` 上：

```javascript
t('common.ok')
 ↓ 悬停显示
┌─────────────────┐
│ common.ok       │
├─────────────────┤
│ zh: 好的        │
│ en: OK          │
├─────────────────┤
│ 👉 点击编辑     │
└─────────────────┘
```

### 5️⃣ 批量操作

**删除多个 key：**

```
1. Ctrl/Cmd + 点击选中多个 key
2. Tools → i18n: 批量删除 Key
3. 确认对话框显示影响范围
4. 点击"删除"
```

**重命名多个 key：**

```
1. 多选 key
2. Tools → i18n: 批量重命名 Key
3. 选择重命名方式（前缀/后缀/正则）
4. 输入旧值和新值
5. 确认执行
```

---

## 🐛 常见问题

### Q: 打开后侧边栏显示"未找到语言包"

**A:** 检查配置

1. Settings → Tools → i18n Helper
2. 确认 "Language Pack Directory" 指向正确的语言包目录
3. 确认文件名匹配 "Path Matcher" 规则
4. 重新扫描：Tools → i18n: 刷新翻译面板

### Q: 翻译引擎无法连接

**A:** 检查网络和 API 密钥

- **Google** — 需要能访问 Google 翻译（国内可能需要代理）
- **DeepL** — 需要有效的 API 密钥
- **OpenAI** — 需要有效的 API 密钥和模型选择

### Q: 修改后没有立即生效

**A:** 手动刷新

```
Tools → i18n: 刷新翻译面板
```

或按 IDE 的刷新快捷键（通常 F5）

### Q: 怎么卸载插件

**A:** 

```
Settings/Preferences → Plugins → i18n Helper → ⚙️ → Uninstall
重启 IDE
```

---

## 📋 快速参考

### 快捷键

| 操作 | 快捷键 |
|------|--------|
| 打开翻译面板 | Alt + 12 |
| 提取硬编码字符串 | Alt + Enter（光标在字符串上） |
| 重命名 Key | Shift + F6（光标在 t('key') 上） |
| IDE 设置 | Ctrl+, (或 Cmd+,) |

### 菜单位置

所有功能都在 **Tools** 菜单中：

```
Tools
├── i18n: 打开翻译面板
├── i18n: 刷新翻译面板
├── i18n: 扫描语言包一致性
├── i18n: 诊断当前文件的译文提示
├── i18n: 批量翻译缺失文案
├── i18n: 批量删除 Key
└── i18n: 批量重命名 Key
```

---

## 📞 遇到问题

### 查看日志

如果功能不正常，检查 IDE 日志：

```
WebStorm / IDEA
Help → Show Log in Finder/Explorer
搜索包含 "i18n" 的日志条目
```

### 重新启动

最有效的解决方案是重启 IDE：

```
File → Restart IDE
```

### 反馈和支持

如果遇到 Bug 或有建议，请提交 Issue。

---

## 🚀 下一步

- 阅读 [README.md](README.md) 了解更多功能
- 查看 [docs/YAML_CONFIG.md](docs/YAML_CONFIG.md) 了解高级配置
- 浏览 [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md) 了解开发进度

---

**版本**: 0.1.0  
**兼容**: WebStorm / IDEA 2026.x+  
**开发者**: hegg5577
