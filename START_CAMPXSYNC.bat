@echo off
title CampXSync Stack Launcher
echo ===================================================
echo   Launching CampXSync Core Service Stack...
echo ===================================================
powershell.exe -ExecutionPolicy Bypass -NoProfile -File "%~dp0scripts\dev\start_stack.ps1"
pause

