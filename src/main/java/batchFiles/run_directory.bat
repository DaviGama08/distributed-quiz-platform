@echo off
setlocal
set "DIR_PORT=%~1"
set "QUEUE_CAPACITY=%~2"
set "MAX_PACKET_SIZE=%~3"
set "TTL_MILLIS=%~4"
if not defined DIR_PORT set "DIR_PORT=9999"
if not defined QUEUE_CAPACITY set "QUEUE_CAPACITY=1024"
if not defined MAX_PACKET_SIZE set "MAX_PACKET_SIZE=65507"
if not defined TTL_MILLIS set "TTL_MILLIS=17000"

pushd "%~dp0..\..\..\.." || exit /b 1
call mvn -q exec:java -Dexec.mainClass=pt.isec.directory.MainDirectory -Dexec.args="%DIR_PORT% %QUEUE_CAPACITY% %MAX_PACKET_SIZE% %TTL_MILLIS%"
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
