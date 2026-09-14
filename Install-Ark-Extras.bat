@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-Ark-Extras.ps1"
if errorlevel 1 (
    echo Installation failed. See the error above.
    pause
    exit /b 1
)
pause
