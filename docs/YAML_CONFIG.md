# i18n tool plus - YAML 配置文件

## 概述

`.i18nrc.yml` 是项目根目录的可选配置文件，用于覆盖当前项目的 IDE Settings。每个项目可以有独立的配置，支持团队共享。

配置优先级（高 → 低）：
1. 项目 `.i18nrc.yml` 配置（团队共享，提交到 Git）
2. IDE Settings（`.idea/i18n-helper.xml`，项目本地配置）
3. 插件内置默认值

这样设计让每个项目都能独立配置，即使多个项目在同一 IDE 中也互不影响。

## 配置项

### 路径与定位

| 配置项 | 类型 | 默认值 | 说明 |
|:--------|:------|:--------|:------|
| `localesPath` | string | `src/i18n/locales` | 兼容旧的单目录配置，相对项目根或绝对路径 |
| `localesPaths` | array | `[]` | 多个语言包根目录，非空时替代 `localesPath`；每个目录使用相同路径模式 |
| `pathMatcher` | string | `{locale}/{file}` | 路径匹配模式，支持 `{locale}` 和 `{namespace}` 占位符 |
| `sourceLocale` | string | `en` | 源语言代码，作为「缺失」判定的基准 |

### 展示与扫描

| 配置项 | 类型 | 默认值 | 说明 |
|:--------|:------|:--------|:------|
| `displayLocales` | array | `[zh]` | 侧边栏优先展示的语言，按列表顺序排列（可在表格表头拖动调整） |
| `supportedExtensions` | array | `[js,jsx,mjs,cjs,ts,tsx,vue,html,htm]` | 参与扫描的文件后缀 |
| `sourceDirs` | array | `[src]` | 扫描源码的目录（相对项目根），用于「缺失检测」 |
| `keyStyle` | string | `nested` | key 组织形式：`nested`（嵌套）/ `flat`（平铺）/ `auto`（两者兼容） |

### 行内提示与高级选项

| 配置项 | 类型 | 默认值 | 说明 |
|:--------|:------|:--------|:------|
| `inlineAnnotationEnabled` | boolean | `true` | 行内译文提示是否启用 |
| `annotationLocale` | string | 空 | 行内提示语言；留空跟随首选展示语言 |
| `fallbackToSource` | boolean | `true` | 首选语言缺 key 时是否回退显示源语言原文 |
| `overrideExisting` | boolean | `false` | 关闭时补齐缺失和空白译文；开启时重新翻译已有译文，确认弹窗提示覆盖范围 |
| `namespaceEnabled` | boolean | `false` | 启用 `ns:key` 查询及隔离，路径模式必须含 `{namespace}` |
| `namespaceDelimiter` | string | `:` | 命名空间分隔符，支持自定义多个字符；不能包含空白、引号或反斜杠 |

### 翻译引擎

| 配置项 | 类型 | 默认值 | 说明 |
|:--------|:------|:--------|:------|
| `activeTranslationEngine` | string | `google-free` | 引擎 ID：`google`、`google-cn`、`deepl`、`baidu`、`libretranslate`、`openai`、`ollama`；兼容旧 `google-free` |
| `deeplApiKey` | string | `` | DeepL API Key（申请地址：https://www.deepl.com/docs-api） |
| `deeplIsFreeApi` | boolean | `true` | DeepL 是否使用免费版 API |
| `openaiApiKey` | string | `` | OpenAI API Key |
| `openaiModel` | string | `gpt-3.5-turbo` | OpenAI 模型名称 |
| `openaiBaseUrl` | string | `https://api.openai.com/v1` | OpenAI API 基础 URL（支持代理或自建 API） |
| `googleApiKey` | string | 空 | 可选，填写后改用 Google Cloud v2；国际与国内选项共用 |
| `googleApiUrl` | string | 空 | 自定义完整 Google 请求地址；留空时根据引擎与密钥选择端点 |
| `deeplApiUrl` | string | 空 | 自定义完整请求地址（含 `/v2/translate`）；非空时优先于免费版开关 |
| `baiduAppId` | string | 空 | 百度通用翻译 App ID |
| `baiduApiSecret` | string | 空 | 百度通用翻译密钥 |
| `baiduApiUrl` | string | `https://fanyi-api.baidu.com/api/trans/vip/translate` | 百度完整请求地址 |
| `libreTranslateApiUrl` | string | `http://localhost:5000/translate` | LibreTranslate 完整请求地址 |
| `libreTranslateApiKey` | string | 空 | 自托管实例启用鉴权时填写 |
| `ollamaBaseUrl` | string | `http://localhost:11434` | Ollama 服务地址，也接受完整 `/api/chat` 端点 |
| `ollamaModel` | string | 空 | 本机已下载的模型全名，必须填写 |

设置页只显示当前引擎的配置。DeepL 地址下拉框可选免费版或专业版，也可直接输入兼容 DeepL v2 的中转地址；自定义服务必须实现对应引擎的协议，仅修改地址不能转换协议。测试连接使用当前尚未保存的输入，并在后台请求固定文本 `hello`。

Google 不填密钥使用免费端点，填写后使用 Google Cloud API。`google-cn` 的免费端点为 `translate.google.cn`，是否可用取决于服务与网络，不保证能在国内直连。OpenAI 需配置兼容 Chat Completions 的服务；Ollama 需先启动本地服务并下载模型。

`editor-llm` 使用 VS Code 的模型接口，不属于本插件支持的引擎。

## 配置作用域

### IDE 项目设置与 YAML 配置

**IDE 项目设置** (IDE Settings)
- 位置：`Preferences > Tools > i18n tool plus`
- 作用：仅当前项目生效，保存在 `.idea/i18n-helper.xml`
- 场景：设置常用的翻译引擎 API Key

**项目配置** (`.i18nrc.yml`)
- 位置：项目根目录
- 作用：仅该项目生效
- 场景：不同项目的翻译文件位置、源语言、显示语言顺序可能不同

### 示例：多项目场景

每个项目可以有完全不同的配置，不相互影响：

```
workspace/
├── project-a/
│   ├── .i18nrc.yml
│   │   localesPath: locales/          ← A 项目翻译在 locales/
│   │   displayLocales: [zh, en, ja]   ← A 项目显示这 3 种语言
│   │   sourceLocale: en
│   └── locales/
│       ├── en.json
│       ├── zh.json
│       └── ja.json
│
└── project-b/
    ├── .i18nrc.yml
    │   localesPath: src/i18n/         ← B 项目翻译在 src/i18n/
    │   displayLocales: [en, fr, de]   ← B 项目显示这 3 种语言
    │   sourceLocale: fr               ← B 项目以法语为源语言
    └── src/i18n/
        ├── fr.json
        ├── en.json
        └── de.json
```

打开 Project A 时，自动加载 A 的配置；打开 Project B 时，自动加载 B 的配置。  
每个项目完全独立，不需要在项目之间手动切换配置。IDE 点击“应用”后仍会保留已加载的 YAML 覆盖值，并回显实际生效的设置；修改 YAML 文件本身后需要重新打开项目。

## 示例配置

### 基础配置

```yaml
# 基础路径和语言设置
localesPath: src/lang/locales
sourceLocale: en
pathMatcher: "{locale}/{file}"

# 展示语言（在表格表头可拖动调整顺序）
displayLocales:
  - zh
  - en
  - ja

# 扫描范围
supportedExtensions:
  - js
  - jsx
  - ts
  - tsx
  - vue
```

### 完整配置（含翻译引擎）

```yaml
# 路径
localesPath: src/lang/locales
sourceLocale: en
pathMatcher: "{locale}/{file}"
keyStyle: nested

# 展示
displayLocales:
  - zh
  - en
supportedExtensions:
  - js
  - jsx
  - ts
  - tsx
  - vue

# 行内提示
inlineAnnotationEnabled: true
annotationLocale: zh
fallbackToSource: true

# 翻译引擎配置
activeTranslationEngine: deepl
deeplApiKey: "YOUR_DEEPL_API_KEY"
deeplIsFreeApi: false
```

### 多文件路径配置

```yaml
localesPath: src/i18n/locales
sourceLocale: en
pathMatcher: "{namespace}.{locale}.json"  # common.zh.json, common.en.json
keyStyle: nested

namespaceEnabled: true
namespaceDelimiter: ":"
# common.en.json 内的 title 对应源码 t('common:title')。
```

### 使用 OpenAI 翻译

```yaml
activeTranslationEngine: openai
openaiApiKey: "sk-..."
openaiModel: "gpt-4"
openaiBaseUrl: "https://api.openai.com/v1"

# 如果使用代理或自建 API
# openaiBaseUrl: "https://proxy.example.com/v1"
```

## 常见问题

### Q: 什么时候应该使用 `.i18nrc.yml`？

A: 当你需要：
- **团队共享配置** — 把 `.i18nrc.yml` 提交到 git，所有成员自动使用相同配置
- **覆盖 IDE 项目设置** — 某个项目有特殊的目录结构或翻译需求
- **版本控制** — 配置变化被 git 追踪，便于团队协作

### Q: `.i18nrc.yml` 和 Settings 配置有什么区别？

A: 
- **Settings** 存储在当前项目的 `.idea/i18n-helper.xml`；它是普通项目配置文件，是否共享取决于团队的版本控制规则
- **`.i18nrc.yml`** 应该提交到 git，所有团队成员共享，优先级更高

### Q: 修改 `.i18nrc.yml` 后需要重启 IDE 吗？

A: 目前需重新打开项目以重新加载 YAML，尚不支持热加载。

### Q: 如何禁用某项功能？

A:
```yaml
# 禁用行内提示
inlineAnnotationEnabled: false

# 关闭批量翻译覆盖，保留已有的非空译文
overrideExisting: false
```

### Q: 多个项目如何使用不同的 `.i18nrc.yml`？

A: 每个项目的根目录放一份 `.i18nrc.yml` 即可。IDE 会在项目打开时自动加载。

翻译由用户手动触发；`activeTranslationEngine` 留空不会禁用翻译，而是兼容回退到默认 Google 引擎。

## 验证配置

启用 IDE 日志记录可查看配置加载情况：

在 WebStorm/IDEA 的日志窗口（Help → Show Log in Finder）查找包含 `i18nrc.yml` 的日志行：
```
加载 .i18nrc.yml 配置：/path/to/project/.i18nrc.yml
```

如果配置格式错误，日志会显示：
```
解析 .i18nrc.yml 失败：...
```

### 多个目录与命名空间

```yaml
localesPaths:
  - packages/web/locales
  - packages/admin/locales
pathMatcher: "{locale}/{namespace}.json"
namespaceEnabled: true
namespaceDelimiter: ":"
sourceLocale: en
```

例如 `packages/web/locales/en/common.json` 中的 `{"title":"Title"}` 对应 `t('common:title')`。
设置页的「多个语言包目录」每行填写一个路径；留空后使用原单目录配置。
不启用命名空间时继续使用文件内的原始 Key。启用后需要同步调整源码中的调用，JSON 字段不添加前缀。

同一语言中，多个目录的相同命名空间与相同 Key 会报冲突，提示具体文件路径；不会按目录顺序覆盖。
新增译文按照源语言文件所在的根目录及命名空间定位，目标文件缺失时停止保存。
提取全新 Key 时需输入完整命名空间；若多个根目录都有该命名空间且无法确定归属，会拒绝猜测。
跨命名空间改名只迁移到原根目录下已存在的文件，所有语言准备成功后才统一保存。

配置优先级仍为 YAML > IDE 设置；YAML 仅声明旧的 `localesPath` 时会清除设置中的多目录覆盖，
同时声明两项时优先使用非空的 `localesPaths`。
