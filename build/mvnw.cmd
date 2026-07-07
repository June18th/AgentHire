@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "ROOT_DIR=%SCRIPT_DIR%.."

call "%ROOT_DIR%\mvnw.cmd" %*
