# 构建前检查清单

在执行 `gradlew buildPlugin` 前，请检查以下项目：

## ✅ 环境检查

- [ ] **Java 21+** 已安装
  ```bash
  java -version
  # 应显示 java version "21.x.x"
  ```

- [ ] **Gradle Wrapper** 可执行
  ```bash
  ./gradlew --version
  # 应显示 Gradle 版本信息
  ```

## ✅ 配置检查

- [ ] `build.gradle.kts` 中 group 已更新为 `com.hegg5577.i18n`

- [ ] `plugin.xml` 中 vendor 已更新为 `hegg5577`

- [ ] IDE 平台路径正确（如果需要自定义）
  ```
  build.gradle.kts 第 23 行：
  local("D:/webstrom/WebStorm 2026.1.4")
  ```

## 📦 构建步骤

### 方式 1：使用包装脚本

**Linux / macOS:**
```bash
bash build-plugin.sh
```

**Windows (PowerShell):**
```powershell
.\gradlew.bat buildPlugin
```

### 方式 2：直接命令

```bash
./gradlew clean buildPlugin
```

## ⏱️ 构建时间

- 首次构建：5-10 分钟（需要下载依赖）
- 后续构建：1-2 分钟

## 📍 输出位置

成功构建后，插件包位置：

```
build/distributions/i18n-helper-0.1.0.zip
```

## 🚀 安装插件

1. 打开 WebStorm / IDEA
2. Settings → Plugins
3. ⚙️ → "Install Plugin from Disk"
4. 选择上面的 `.zip` 文件
5. 重启 IDE

## ✨ 验证安装

重启后，检查 Tools 菜单中是否出现：

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

如果看到这些菜单，说明安装成功！🎉

## 🐛 常见构建问题

### "JAVA_HOME is not set"

**解决方案：**
```bash
# Linux / macOS
export JAVA_HOME=/path/to/jdk21
export PATH=$JAVA_HOME/bin:$PATH

# Windows
set JAVA_HOME=C:\Program Files\Java\jdk21
set PATH=%JAVA_HOME%\bin;%PATH%
```

### "Cannot find WebStorm 2026.1.4"

**解决方案：**

编辑 `build.gradle.kts` 第 23 行，改为你本机的 WebStorm 路径：

```kotlin
local("C:\\Program Files\\JetBrains\\WebStorm 2026.1")  // Windows
// 或
local("/Applications/WebStorm.app/Contents")  // macOS
```

### "Build failed: Gradle sync failed"

**解决方案：**
```bash
./gradlew clean
./gradlew build --refresh-dependencies
```

## 📋 完整构建日志参考

成功构建的日志应包含以下信息：

```
> Task :compileKotlin
...
> Task :buildPlugin
Building plugin: ...

BUILD SUCCESSFUL in Xs
```

## 📦 插件包信息

构建完成的 `.zip` 文件应包含：

```
i18n-helper-0.1.0.zip
├── i18n-helper/
│   ├── lib/
│   │   └── i18n-helper-0.1.0.jar
│   └── META-INF/
│       └── plugin.xml
```

---

**需要帮助？** 查看 [INSTALL_AND_USAGE.md](INSTALL_AND_USAGE.md)
