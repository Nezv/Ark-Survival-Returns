@echo off
setlocal
title Ark Survival Returns
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-Ark-Mod.ps1" %*
set "launcherExit=%ERRORLEVEL%"
if /i "%~1"=="-Check" exit /b %launcherExit%
if not "%launcherExit%"=="0" (
    echo.
    echo Ark could not start. The error is shown above.
    pause
)
exit /b %launcherExit%
