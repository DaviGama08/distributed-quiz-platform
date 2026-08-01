@echo off
setlocal
set "DIR_HOST=%~1"
set "DIR_PORT=%~2"
set "DATA_DIR=%~3"
set "MULTICAST_INTERFACE=%~4"
set "CLIENT_PORT=%~5"
set "DB_COPY_PORT=%~6"
if not defined DIR_HOST set "DIR_HOST=localhost"
if not defined DIR_PORT set "DIR_PORT=9999"
if not defined DATA_DIR set "DATA_DIR=PROJECT"
if not defined MULTICAST_INTERFACE set "MULTICAST_INTERFACE=AUTO"
if not defined CLIENT_PORT set "CLIENT_PORT=5010"
if not defined DB_COPY_PORT set "DB_COPY_PORT=17010"

pushd "%~dp0..\..\..\.." || exit /b 1
call mvn -q exec:java -Dexec.mainClass=pt.isec.server.MainServer -Dexec.args="%DIR_HOST% %DIR_PORT% %DATA_DIR% %MULTICAST_INTERFACE% %CLIENT_PORT% %DB_COPY_PORT%"
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
