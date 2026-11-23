@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title StorageESP - Forge 1.21.11 Build
color 0B
set "CODE=1"
echo =====================================================
echo             STORAGE ESP - FORGE 1.21.11
echo =====================================================
echo.
echo This window will stay open even if there is an error.
echo.
if not exist "build.gradle" (
  echo ERROR: build.gradle is missing.
  echo Extract ALL files from the ZIP first: right click ZIP,
  echo choose Extract All, then open StorageESP and run BUILD_MOD.cmd.
  goto done
)
where powershell.exe >nul 2>&1
if errorlevel 1 (
  echo ERROR: Windows PowerShell not found.
  goto done
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0BUILD_MOD.ps1"
set "CODE=%ERRORLEVEL%"
if not "%CODE%"=="0" (
  echo.
  echo BUILD FAILED. If asked for help, send build-log.txt.
) else (
  echo.
  echo BUILD SUCCEEDED - check build\libs for the JAR.
)
:done
echo.
echo -----------------------------------------------------
echo Press any key to close this window.
pause >nul
exit /b %CODE%
