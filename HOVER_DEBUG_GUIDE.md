# Hover 功能调试指南

## 问题
"Edit this key" 链接点击不能打开编辑弹窗，且 Hover 预览不显示设置里的语言

## 调试步骤

### 1. 卸载并重新安装插件
```
IDE → File → Settings → Plugins
↓
搜索 "i18n Helper" → 点击卸载 → 重启 IDE
↓
File → Settings → Plugins → ⚙️ → Install Plugin from Disk
↓
选择: ./build/distributions/i18n-jetbrains-plugin-0.1.0.zip
↓
重启 IDE
```

### 2. 打开日志查看窗口
```
IDE → Help → Show Log in File Explorer
↓
会打开文件夹，找到 idea.log 文件
↓
用记事本打开，滚到最下面
```

### 3. 在代码中悬停测试

打开 ai-station-web/src 中任何 `.tsx` 或 `.ts` 文件，找到这样的代码：
```
t('dashboard.operation')
或
t('some.other.key')
```

**用鼠标悬停在这个 key 上**（不要点击，就是把鼠标放在上面）

### 4. 查看日志输出

在 idea.log 中搜索 `I18nHoverDocumentationProvider`，应该看到：

**如果 Hover 被触发：**
```
[I18nHoverDocumentationProvider] generateDoc called, element=..., original=...
[I18nHoverDocumentationProvider] No translations found for key: dashboard.operation ...
```

**如果 Hover 没被触发：**
那说明问题在 plugin.xml 或 IDE 没有正确识别文件类型

### 5. 检查设置

IDE → File → Settings → i18n Helper

检查：
- ✅ localesPath 是否正确（应该是 `src/i18n/locales`）
- ✅ displayLocales 是否包含 `zh` 或其他语言
- ✅ sourceLocale 是否是 `en`

如果设置都是空的，可能没有初始化。手动设置一遍。

## 常见问题

### 问题 1: Hover 没反应
**原因**：DocumentationProvider 没有被触发
**解决**：
- 检查 plugin.xml 中的 `language` 属性是否匹配你的文件类型
- 如果文件是 `.jsx` 或 `.tsx`，可能需要在 plugin.xml 中注册这些语言

### 问题 2: Hover 显示但链接不打开
**原因**：`getDocumentationElementForLink` 没有被调用
**解决**：
- 查看日志中是否有 `Handling link:` 的输出
- 可能是 IDE 版本对超链接的支持有问题

### 问题 3: Hover 显示但没有 displayLocales 中的语言
**原因**：displayLocales 为空或没有正确加载
**解决**：
- 在 IDE Settings 中手动配置 displayLocales
- 检查 YAML 配置是否有问题

## 下一步操作

将日志输出贴给我，我来分析具体是哪一步出问题了。
