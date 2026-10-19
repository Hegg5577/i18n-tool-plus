# 🔧 启动沙盒 IDE 问题排查

如果 `./dev.cmd runIde` 无法启动沙盒 WebStorm，请按照以下步骤排查：

---

## 快速诊断

### 方法 1：检查 Java 环境

```batch
REM 在 CMD 或 PowerShell 中执行：
java -version

REM 应该显示 Java 21+ 版本
```

如果显示"java 不是内部或外部命令"，说明需要设置 JAVA_HOME。

### 方法 2：检查 IDE 位置

dev.cmd 默认查找的 IDE 位置：
- `D:\IntelliJ IDEA 2026.1.4\jbr`
- `D:\webstrom\WebStorm 2026.1.4\jbr`

检查你的 IDE 是否在这些位置。

### 方法 3：运行诊断脚本

```batch
.\dev.cmd --version
```

如果能显示 Gradle 版本，说明 Java 环境正常。

---

## 常见问题和解决方案

### Q1: "JAVA_HOME is not set"

**症状**: `dev.cmd runIde` 显示 JAVA_HOME 未设置

**解决方案**:

#### 方式 A：修改 dev.cmd（推荐）

编辑 `dev.cmd` 第 19 行，改为你的 IDE 路径：

```batch
REM 改这一行：
set "JAVA_HOME=D:\你的IDE路径\jbr"

REM 例如：
set "JAVA_HOME=D:\Program Files\IntelliJ IDEA 2026.1.4\jbr"
set "JAVA_HOME=C:\Users\你的用户名\.jdks\jdk-21"
```

#### 方式 B：设置系统环境变量

1. 打开 Windows 环境变量设置
   ```
   系统设置 → 高级系统设置 → 环境变量
   ```

2. 新增用户变量 `JAVA_HOME`：
   ```
   变量名：JAVA_HOME
   变量值：D:\IntelliJ IDEA 2026.1.4\jbr
   ```

3. 重启 CMD/PowerShell 后重试

#### 方式 C：使用 PowerShell

```powershell
$env:JAVA_HOME = "D:\IntelliJ IDEA 2026.1.4\jbr"
.\dev.cmd runIde
```

### Q2: "JDK not found"

**症状**: dev.cmd 显示 JDK 未找到

**解决方案**:

1. 检查 IDE 是否真的安装了
   ```batch
   dir "D:\IntelliJ IDEA 2026.1.4\jbr\bin\java.exe"
   ```

2. 如果输出"找不到文件"，说明路径不对

3. 查找你的 IDE 和 JBR 的实际位置：
   ```batch
   dir "D:\" /s /b | findstr "IntelliJ\|WebStorm\|jbr"
   ```

4. 修改 dev.cmd 中的路径

### Q3: 沙盒 IDE 启动但看不到插件

**症状**: IDE 启动了，但 Tools 菜单中没有 i18n 选项

**解决方案**:

这是正常的！沙盒 IDE 是干净的环境，需要重新构建插件：

```batch
REM 重新构建和启动
.\dev.cmd clean buildPlugin runIde
```

或

```batch
REM 分步执行
.\dev.cmd clean
.\dev.cmd buildPlugin
.\dev.cmd runIde
```

### Q4: 沙盒 IDE 启动很慢

**症状**: `dev.cmd runIde` 执行后要等很久才出现 IDE

**原因**: 第一次启动需要下载和配置 IDE，可能需要 5-10 分钟

**解决方案**:

- 第一次启动正常需要较长时间
- 可以查看 `.gradle` 目录的下载进度
- 如果一直卡住（30+ 分钟），可能是网络问题，重新运行

### Q5: "BUILD FAILED"

**症状**: 构建失败

**解决方案**:

1. 检查错误信息，找到具体失败原因
2. 清理构建缓存：
   ```batch
   .\dev.cmd clean
   ```

3. 重新构建：
   ```batch
   .\dev.cmd buildPlugin
   ```

4. 如果仍然失败，检查日志：
   ```
   build/tmp/logs/
   ```

---

## 手动启动方式

如果 dev.cmd 有问题，可以手动设置 JAVA_HOME 并运行：

### PowerShell

```powershell
# 方式 1：设置环境变量后运行
$env:JAVA_HOME = "D:\IntelliJ IDEA 2026.1.4\jbr"
.\gradlew.bat runIde

# 方式 2：使用 Gradle 任务直接运行
.\gradlew.bat -Dorg.gradle.java.home="D:\IntelliJ IDEA 2026.1.4\jbr" runIde
```

### CMD

```batch
REM 方式 1：设置环境变量后运行
set JAVA_HOME=D:\IntelliJ IDEA 2026.1.4\jbr
gradlew.bat runIde

REM 方式 2：使用临时变量
setlocal
set JAVA_HOME=D:\IntelliJ IDEA 2026.1.4\jbr
call gradlew.bat runIde
endlocal
```

---

## 查找 JBR 位置

如果不确定 JBR 在哪里，可以搜索：

### Windows 文件资源管理器

按 `Ctrl+F` 搜索：
```
jbr
```

常见位置：
- `C:\Program Files\JetBrains\IntelliJ IDEA 2026.x\jbr`
- `D:\IntelliJ IDEA 2026.x\jbr`
- `C:\Users\用户名\.jdks\jdk-21`
- `C:\Users\用户名\AppData\Local\JetBrains\IntelliJ IDEA 2026.x\jbr`

### CMD 搜索

```batch
REM 搜索所有 jbr
dir C:\ /s /b 2>nul | findstr /i "jbr" | head -10

REM 搜索 IDEA 和 WebStorm
dir D:\ /s /b 2>nul | findstr /i "IntelliJ\|WebStorm" | head -10
```

---

## 最终验证

当一切就绪后，执行：

```batch
.\dev.cmd runIde
```

应该看到：
1. `[OK]` 消息，找到了 Java
2. Gradle 开始构建
3. IDE 沙盒启动并出现 IDE 窗口

如果都正常，在沙盒 IDE 中：
1. 打开 Tools 菜单
2. 应该看到 "i18n Helper" 的菜单项
3. 点击打开翻译面板

---

## 快捷命令

```batch
REM 常用 Gradle 任务
.\dev.cmd build              # 编译项目
.\dev.cmd buildPlugin        # 构建插件包
.\dev.cmd runIde             # 启动沙盒 IDE
.\dev.cmd clean              # 清理构建文件
.\dev.cmd test               # 运行单元测试

REM 组合命令
.\dev.cmd clean buildPlugin runIde   # 完整重建 + 启动
```

---

## 需要更多帮助？

如果问题仍未解决，请收集以下信息：

1. **Java 版本**
   ```batch
   java -version
   ```

2. **Gradle 版本**
   ```batch
   .\dev.cmd --version
   ```

3. **IDE 路径**
   ```batch
   dir "D:\IntelliJ IDEA 2026.1.4"
   dir "D:\IntelliJ IDEA 2026.1.4\jbr\bin"
   ```

4. **错误日志**
   - 复制 dev.cmd 的完整错误输出
   - 查看 `build/tmp/logs/` 中的日志文件

然后联系开发者并提供这些信息。

---

**版本**: 0.1.0  
**最后更新**: 2026-10-19
