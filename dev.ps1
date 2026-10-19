# i18n Helper - PowerShell 启动脚本
# 用于启动沙盒 IDE 或构建插件包
# 用法: .\dev.ps1 runIde
#       .\dev.ps1 buildPlugin
#       .\dev.ps1 clean build

param(
    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$GradleArgs
)

# 设置 JAVA_HOME
$JAVA_HOME = "D:\IntelliJ IDEA 2026.1.4\jbr"

# 验证 Java 存在
$java_exe = Join-Path $JAVA_HOME "bin\java.exe"
if (-not (Test-Path $java_exe)) {
    Write-Host "[ERROR] Java 找不到: $java_exe" -ForegroundColor Red
    Write-Host ""
    Write-Host "请修改此脚本中的 JAVA_HOME 路径。" -ForegroundColor Yellow
    exit 1
}

Write-Host "[OK] JAVA_HOME: $JAVA_HOME" -ForegroundColor Green
Write-Host ""

# 设置环境变量
$env:JAVA_HOME = $JAVA_HOME

# 构建命令
$gradle_cmd = @(".\gradlew.bat") + $GradleArgs

Write-Host "执行: $($gradle_cmd -join ' ')" -ForegroundColor Cyan
Write-Host ""

# 运行 Gradle
& $gradle_cmd[0] @gradle_cmd[1..$gradle_cmd.Length]

exit $LASTEXITCODE
