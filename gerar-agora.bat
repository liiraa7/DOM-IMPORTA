@echo off
chcp 1252 >nul
setlocal
cd /d "%~dp0"

rem  Gera o txt sem abrir janela, usando o config.properties.
rem  E este que se coloca no Agendador de Tarefas do Windows.
rem  Para o agendador nao travar num arquivo que ja existe, deixe
rem  saida.sobrescrever=sempre no config.properties.

java -jar "gerador-arquivo-txt.jar" --console
if errorlevel 1 (
  echo.
  echo A geracao falhou. Veja o gerador_arquivo.log.
  exit /b 1
)
endlocal
