@echo off
start "" /min "run_directory.bat"
timeout /t 2 >nul
start "" /min "run_server.bat"
start "" /min "run_server2.bat"
start "" /min "run_server3.bat"
start "" /min "run_server4.bat"
start "" /min "run_server5.bat"
timeout /t 2 >nul
start "" /min "run_client.bat"
