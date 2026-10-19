@echo off
REM ============================================================
REM  Gradle launcher for this project with troubleshooting
REM ============================================================

setlocal enabledelayedexpansion

REM 尝试查找 JDK 的多个位置
set "JAVA_HOME="

REM 1. 检查 IDEA 的 JBR
if exist "D:\IntelliJ IDEA 2026.1.4\jbr\bin\java.exe" (
    set "JAVA_HOME=D:\IntelliJ IDEA 2026.1.4\jbr"
    echo [OK] 使用 IDEA 的 JBR: %JAVA_HOME%
    goto found_java
)

REM 2. 检查 WebStorm 的 JBR
if exist "D:\webstrom\WebStorm 2026.1.4\jbr\bin\java.exe" (
    set "JAVA_HOME=D:\webstrom\WebStorm 2026.1.4\jbr"
    echo [OK] 使用 WebStorm 的 JBR: %JAVA_HOME%
    goto found_java
)

REM 3. 检查系统 JAVA_HOME
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        echo [OK] 使用系统 JAVA_HOME: %JAVA_HOME%
        goto found_java
    )
)

REM 4. 尝试从 PATH 中查找
for /f "delims=" %%A in ('where java.exe 2^>nul') do (
    set "JAVA_PATH=%%A"
    echo [OK] 从 PATH 中找到 Java: !JAVA_PATH!
    goto found_java
)

REM 未找到 Java
echo [ERROR] 未能找到 Java（JDK）
echo.
echo 请尝试以下方法之一：
echo   1. 在 dev.cmd 第 19 行中修改 JAVA_HOME 路径
echo   2. 设置系统环境变量 JAVA_HOME
echo   3. 确保 Java 在系统 PATH 中
echo.
exit /b 1

:found_java
REM 验证 Java 版本
echo.
echo 验证 Java：
"%JAVA_HOME%\bin\java.exe" -version 2>&1 | find "version"
if errorlevel 1 (
    echo [WARNING] 无法验证 Java 版本，但继续尝试...
)

echo.
echo 启动 Gradle 任务：%*
echo.

REM 运行 Gradle
call "%~dp0gradlew.bat" %*

if errorlevel 1 (
    echo.
    echo [ERROR] Gradle 任务失败！
    echo 错误代码: %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)

endlocal
