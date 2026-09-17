@echo off
chcp 1252 >nul
setlocal
cd /d "%~dp0"

where java >nul 2>&1
if errorlevel 1 (
  echo Nao encontrei o Java nesta maquina.
  echo Instale o Java de execucao (Java 8 ou mais novo) e tente de novo.
  pause
  exit /b 1
)

start "" javaw -jar "gerador-arquivo-txt.jar"
endlocal
