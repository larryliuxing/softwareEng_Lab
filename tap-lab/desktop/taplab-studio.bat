@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo 需要先安装 JDK 17，并把 java 加到 PATH。
  pause
  exit /b 1
)
if exist "%~dp0build\libs\taplab-studio.jar" (
  java -jar "%~dp0build\libs\taplab-studio.jar"
) else if exist "%~dp0taplab-studio.jar" (
  java -jar "%~dp0taplab-studio.jar"
) else (
  echo 还没有 jar。请先在 tap-lab 目录运行 gradlew.bat :desktop:studioJar
  pause
  exit /b 1
)
