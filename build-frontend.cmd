@echo off
echo Starting Frontend Build...
powershell -ExecutionPolicy Bypass -File "%~dp0build-frontend.ps1"
if %errorlevel% neq 0 (
    echo Build failed!
    pause
    exit /b %errorlevel%
)
echo Build successful!
pause
