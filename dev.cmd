@echo off
REM ============================================================
REM  Gradle launcher for this project (ASCII only on purpose:
REM  cmd.exe reads .cmd files using the OEM codepage, so any
REM  non-ASCII comment would be mangled into fake commands).
REM
REM  Why this exists:
REM  'java' is NOT on PATH on this machine, so the gradlew
REM  launcher aborts with "JAVA_HOME is not set and no 'java'
REM  command could be found in your PATH". This script pins
REM  JAVA_HOME to the JBR 25 that ships with IntelliJ IDEA.
REM
REM  Usage (PowerShell or CMD):
REM    .\dev.cmd build          build the plugin
REM    .\dev.cmd runIde         launch sandbox WebStorm with plugin
REM    .\dev.cmd clean build    any gradle args are passed through
REM ============================================================

set "JAVA_HOME=D:\IntelliJ IDEA 2026.1.4\jbr"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JDK not found: %JAVA_HOME%
    echo         Please edit JAVA_HOME inside dev.cmd.
    exit /b 1
)

call "%~dp0gradlew.bat" %*
