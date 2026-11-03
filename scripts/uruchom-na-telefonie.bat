@echo off
rem Builds FairShare, installs it on the phone connected by USB and starts it (details: docs\skaner.md).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0uruchom-na-telefonie.ps1" %*
echo.
pause
