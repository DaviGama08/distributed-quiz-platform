@echo off
start "" /min "%~dp0run_directory.bat"
timeout /t 1 >nul
start "" /min "%~dp0run_server.bat"
start "" /min "%~dp0run_client.bat"
