@echo off
REM Builds the Smart Library desktop app for Windows (no installer tool needed).
REM Requires: JDK 17+ on PATH (it includes jpackage). Run from anywhere.
setlocal
cd /d "%~dp0.."

echo [1/3] Building the application jar...
call mvnw.cmd -q clean package -DskipTests || goto :error

echo [2/3] Preparing input folder...
if exist build\input rmdir /s /q build\input
mkdir build\input
copy /y target\smart-library-1.0.0.jar build\input\ >nul || goto :error

echo [3/3] Creating the desktop app with jpackage...
if exist dist\SmartLibrary rmdir /s /q dist\SmartLibrary
jpackage --type app-image --name SmartLibrary --input build\input --main-jar smart-library-1.0.0.jar ^
  --main-class org.springframework.boot.loader.launch.JarLauncher ^
  --java-options "-Dlibrary.desktop.enabled=true" ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --dest dist --win-console || goto :error

echo.
echo Done:  dist\SmartLibrary\SmartLibrary.exe
echo (Remove "--win-console" in this script to hide the console window once everything works.)
exit /b 0

:error
echo.
echo BUILD FAILED. Read the messages above.
exit /b 1
