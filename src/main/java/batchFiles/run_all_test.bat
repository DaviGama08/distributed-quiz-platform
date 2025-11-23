@echo off
start "" /min "run_directory.bat"
timeout /t 2 >nul
start "" /min "run_server.bat"
timeout /t 2 >nul
start "" /min "run_client.bat"
