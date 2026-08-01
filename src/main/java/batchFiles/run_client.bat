@echo off
setlocal
set "DIR_HOST=%~1"
set "DIR_PORT=%~2"
if not defined DIR_HOST set "DIR_HOST=localhost"
if not defined DIR_PORT set "DIR_PORT=9999"

pushd "%~dp0..\..\..\.." || exit /b 1
call mvn -q javafx:run -Djavafx.args="--directory-host=%DIR_HOST% --directory-port=%DIR_PORT%"
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
