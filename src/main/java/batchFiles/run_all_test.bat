@echo off
start "" /min "run_directory.bat"
timeout /t 1 >nul
start "" /min "run_server.bat"
start "" /min "run_client.bat"
