@echo off
rem Double-click to build the mod and open Minecraft with it. Closing this window closes the game.
cd /d "%~dp0"
call gradlew.bat runClient
if errorlevel 1 pause
