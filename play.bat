@echo off
setlocal EnableExtensions
rem Double-click to build the mod and open Minecraft with it. Closing this window closes the game.
cd /d "%~dp0"

set "REQUIRED_JAVA=25"
call :is_java_ok "%JAVA_HOME%"
if not errorlevel 1 goto run

set "FOUND_JAVA_HOME="
for %%R in ("%ProgramFiles%\Java" "%ProgramFiles%\Eclipse Adoptium" "%ProgramFiles%\Microsoft" "%ProgramFiles%\Zulu" "%ProgramFiles%\BellSoft" "%ProgramFiles%\Amazon Corretto" "%LOCALAPPDATA%\Programs\Eclipse Adoptium") do (
    if exist "%%~R" (
        for /f "delims=" %%D in ('dir /b /ad /o-n "%%~R" 2^>nul') do (
            if not defined FOUND_JAVA_HOME (
                call :is_java_ok "%%~R\%%~D"
                if not errorlevel 1 set "FOUND_JAVA_HOME=%%~R\%%~D"
            )
        )
    )
)

if not defined FOUND_JAVA_HOME (
    for /f "delims=" %%J in ('where java 2^>nul') do (
        if not defined FOUND_JAVA_HOME (
            call :is_java_ok "%%~dpJ.."
            if not errorlevel 1 (
                for %%P in ("%%~dpJ..") do set "FOUND_JAVA_HOME=%%~fP"
            )
        )
    )
)

if not defined FOUND_JAVA_HOME (
    for /f "tokens=1,* delims==" %%A in ('java -XshowSettings:properties -version 2^>^&1') do (
        if not defined FOUND_JAVA_HOME (
            for /f "tokens=* delims= " %%K in ("%%A") do (
                if "%%K"=="java.home" (
                    for /f "tokens=* delims= " %%P in ("%%B") do (
                        call :is_java_ok "%%~P"
                        if not errorlevel 1 set "FOUND_JAVA_HOME=%%~P"
                    )
                )
            )
        )
    )
)

if not defined FOUND_JAVA_HOME (
    echo.
    echo ERROR: Java %REQUIRED_JAVA%+ is required, but JAVA_HOME ^("%JAVA_HOME%"^) is missing or older.
    echo Please install JDK %REQUIRED_JAVA% or update JAVA_HOME.
    pause
    exit /b 1
)

set "JAVA_HOME=%FOUND_JAVA_HOME%"

:run
set "GRADLE_ARGS=%*"
if not defined GRADLE_ARGS set "GRADLE_ARGS=runClient"
call gradlew.bat %GRADLE_ARGS%
if errorlevel 1 pause
exit /b %errorlevel%

:is_java_ok
set "CANDIDATE=%~1"
if not defined CANDIDATE exit /b 1
if not exist "%CANDIDATE%\bin\java.exe" exit /b 1
if not exist "%CANDIDATE%\release" exit /b 1
set "JVER="
for /f "usebackq tokens=1,2 delims==" %%A in ("%CANDIDATE%\release") do (
    if "%%A"=="JAVA_VERSION" set "JVER=%%~B"
)
if not defined JVER exit /b 1
set "JMAJOR="
for /f "tokens=1 delims=." %%M in ("%JVER%") do set "JMAJOR=%%M"
if not defined JMAJOR exit /b 1
if %JMAJOR% LSS %REQUIRED_JAVA% exit /b 1
exit /b 0
