@echo off
start "" /min "%~dp0run_directory.bat"
timeout /t 2 >nul
start "" /min "%~dp0run_server.bat"
start "" /min "%~dp0run_server2.bat"
start "" /min "%~dp0run_server3.bat"
timeout /t 2 >nul
start "" /min "%~dp0run_client.bat"
start "" /min "%~dp0run_client.bat"
