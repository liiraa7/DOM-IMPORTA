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

rem  Copia do config: o programa regrava as linhas de saida a cada geracao
rem  e o config-exemplo.properties tem de ficar como esta no repositorio.
copy /y "exemplo\config-exemplo.properties" "%TEMP%\gerador-config-teste.properties" >nul

rem  O nome sai de saida.empresa + saida.tipo + saida.competencia do config
set "SAIDA=exemplo\TESTE_GABARITO_082025.txt"
if exist "%SAIDA%" del "%SAIDA%"
java -jar "gerador-arquivo-txt.jar" --console --config "%TEMP%\gerador-config-teste.properties"
if errorlevel 1 (
  echo ERRO: a geracao falhou.
  exit /b 1
)

fc /b "%SAIDA%" "exemplo\gabarito.txt" >nul
if errorlevel 1 (
  echo.
  echo ERRO: o txt saiu DIFERENTE do gabarito. Rode o fc para ver onde:
  echo     fc /b %SAIDA% exemplo\gabarito.txt
  exit /b 1
)

echo OK: o txt saiu igual ao gabarito, byte a byte.
endlocal
