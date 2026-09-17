@echo off
chcp 1252 >nul
setlocal
cd /d "%~dp0.."

rem  Rito antes de empacotar: gera o txt da planilha-exemplo.xlsx e compara
rem  byte a byte com o exemplo\gabarito.txt.

if not exist "gerador-arquivo-txt.jar" (
  echo ERRO: o gerador-arquivo-txt.jar nao esta aqui. Rode o compilar.bat.
  exit /b 1
)

rem  Copia do config: o programa regrava a linha saida.destino a cada geracao
rem  e o config-exemplo.properties tem de ficar como esta no repositorio.
copy /y "exemplo\config-exemplo.properties" "%TEMP%\gerador-config-teste.properties" >nul

if exist "exemplo\saida-do-teste.txt" del "exemplo\saida-do-teste.txt"
java -jar "gerador-arquivo-txt.jar" --console --config "%TEMP%\gerador-config-teste.properties"
if errorlevel 1 (
  echo ERRO: a geracao falhou.
  exit /b 1
)

fc /b "exemplo\saida-do-teste.txt" "exemplo\gabarito.txt" >nul
if errorlevel 1 (
  echo.
  echo ERRO: o txt saiu DIFERENTE do gabarito. Rode o fc para ver onde:
  echo     fc /b exemplo\saida-do-teste.txt exemplo\gabarito.txt
  exit /b 1
)

echo OK: o txt saiu igual ao gabarito, byte a byte.
endlocal
