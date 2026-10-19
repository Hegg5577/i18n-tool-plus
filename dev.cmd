@echo off
REM ============================================================
REM  i18n Helper - Gradle 启动脚本（简化版）
REM ============================================================

setlocal

REM 直接设置 JAVA_HOME 为 IDEA 的 JBR
set "JAVA_HOME=D:\IntelliJ IDEA 2026.1.4\jbr"

REM 验证 Java 是否存在
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Java 找不到: %JAVA_HOME%\bin\java.exe
    echo.
    echo 请修改此脚本中的 JAVA_HOME 路径。
    echo.
    pause
    exit /b 1
)

REM 显示信息
echo [OK] JAVA_HOME: %JAVA_HOME%
echo.
echo 执行: gradlew.bat %*
echo.

REM 运行 Gradle
call "%~dp0gradlew.bat" %*

endlocal
exit /b %errorlevel%
