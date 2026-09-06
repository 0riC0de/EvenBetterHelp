@echo off
title EvenBetterHelp Launcher
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0ops\start-dev.ps1"
pause
