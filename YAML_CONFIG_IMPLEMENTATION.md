# YAML 配置文件支持 - 实现摘要

## 功能概述

添加了对项目根目录 `.i18nrc.yml` 配置文件的支持。允许团队在源码控制中共享 i18n 配置，覆盖全局 IDE Settings。

## 实现细节

### 1. YAML 库集成

- **依赖**: SnakeYAML 2.2（compileOnly + testImplementation）
- **位置**: `build.gradle.kts`

### 2. 核心组件

#### `I18nYamlConfigLoader`
- **文件**: `com/up3d/i18n/config/I18nYamlConfigLoader.kt`
- **职责**:
  - 从项目根目录加载 `.i18nrc.yml`
  - 提供类型转换工具方法（getString、getBoolean、getStringList）
  - 错误处理和日志记录

#### `I18nSettings` 扩展
- **文件**: `com/up3d/i18n/settings/I18nSettings.kt`
- **变更**:
  - 新增 `initProject(Project)` 方法，在项目打开时调用
  - 新增 `applyYamlOverrides()` 方法，将 YAML 配置合并到状态中
  - 新增 `hasYamlConfig` 属性，判断是否成功加载了 YAML 配置
  - 配置优先级：`.i18nrc.yml` > Settings XML > 默认值

#### `I18nProjectOpenListener`
- **文件**: `com/up3d/i18n/startup/I18nProjectOpenListener.kt`
- **职责**: 实现 `ProjectManagerListener`，在项目打开时触发配置加载

### 3. 插件描述符更新

- **文件**: `src/main/resources/META-INF/plugin.xml`
- **变更**: 注册 `projectListener` 扩展点

### 4. 文档和示例

- **YAML 配置文档**: `docs/YAML_CONFIG.md`
  - 完整的配置项列表
  - 示例配置（基础、完整、命名空间、OpenAI）
  - 常见问题解答
  
- **示例文件**: `.i18nrc.example.yml`
  - 所有配置项的注释说明
  - 默认值和可选值

### 5. 单元测试

- **I18nYamlConfigLoaderTest.kt**: 类型转换工具的单元测试
- **I18nYamlConfigLoaderIntegrationTest.kt**: YAML 文件加载的集成测试

## 支持的配置项

**路径相关**:
- `localesPath`
- `pathMatcher`
- `sourceLocale`

**展示相关**:
- `displayLocales`
- `supportedExtensions`
- `sourceDirs`
- `keyStyle`

**命名空间**:
- `namespaceEnabled`
- `namespaceDelimiter`

**行内提示**:
- `inlineAnnotationEnabled`
- `annotationLocale`
- `fallbackToSource`

**批量翻译**:
- `overrideExisting`

**翻译引擎**:
- `activeTranslationEngine`
- `deeplApiKey`
- `deeplIsFreeApi`
- `openaiApiKey`
- `openaiModel`
- `openaiBaseUrl`

## 配置优先级

1. **项目 `.i18nrc.yml`** —— 最高优先级
2. **IDE Settings** (`.idea/i18n-helper.xml`)
3. **插件内置默认值** —— 最低优先级

## 使用示例

项目根目录创建 `.i18nrc.yml`:

```yaml
localesPath: src/lang/locales
sourceLocale: en
displayLocales:
  - zh
  - en
activeTranslationEngine: deepl
deeplApiKey: your-api-key
```

提交到 git，团队成员打开项目时自动应用配置。

## 后续改进

- [ ] 支持 `.i18nrc.yaml` 作为备选文件名
- [ ] 支持 YAML 配置的热加载（无需重启 IDE）
- [ ] 在 Settings UI 中显示当前活跃的 YAML 配置文件路径
- [ ] YAML schema 验证
