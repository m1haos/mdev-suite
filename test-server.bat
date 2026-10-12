@echo off
rem UTF-8 so Russian text in the server console is readable.
chcp 65001 >nul
cd /d "%~dp0"
title M/Dev Studio test server
if not exist "run\26.2" mkdir "run\26.2"
if not exist "run\26.2\server.properties" (
  > "run\26.2\server.properties" (
    echo online-mode=false
    echo motd=M/Dev Studio test server
    echo spawn-protection=0
    echo view-distance=8
    echo difficulty=normal
  )
)
echo Building and starting a Paper 26.2 test server.
echo The first start downloads Gradle, a JDK and Paper, which takes a few minutes.
echo When "Done" appears, join localhost and type "op YourName" here.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -Command "& { 'java: ' + ((Get-Command java -ErrorAction SilentlyContinue).Source) + '  JAVA_HOME: ' + $env:JAVA_HOME; & '.\gradlew.bat' ':eventide:runServer' '--console=plain' 2>&1 } | Tee-Object -FilePath 'run\launcher.log'"
pause
