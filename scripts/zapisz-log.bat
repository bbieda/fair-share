@echo off
rem Saves the scanner log and any crash from the phone to phone-log.txt in the project folder.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0uruchom-na-telefonie.ps1" -SaveLog
echo.
pause
