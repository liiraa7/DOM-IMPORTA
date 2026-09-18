@echo off
chcp 1252 >nul
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================
echo  Diagnostico do Gerador de Arquivo TXT
echo ============================================
echo.
echo PASTA ONDE ESTE .BAT ESTA:
echo     %~dp0
echo.

echo JAVA:
where java 2>nul
if errorlevel 1 echo     NAO ENCONTRADO - falta o Java de execucao
java -version 2>&1
echo.

if not exist "config.properties" (
  if exist "config-modelo.properties" (
    copy /y "config-modelo.properties" "config.properties" >nul
    echo Criei o config.properties a partir do modelo.
    echo.
  )
)

echo ARQUIVOS NESTA PASTA:
if exist "gerador-arquivo-txt.jar" (echo     jar ................. OK) else (echo     jar ................. FALTANDO)
if exist "config.properties" (echo     config.properties .... OK) else (echo     config.properties .... FALTANDO)
if exist "src\br\com\triangulo\gerador\GeradorArquivo.java" (echo     fonte ............... OK) else (echo     fonte ............... FALTANDO)
if exist "exemplo\planilha-exemplo.xlsx" (echo     planilha de teste ... OK) else (echo     planilha de teste ... FALTANDO)
if exist ".vscode\tasks.json" (echo     tarefas do VS Code .. OK) else (echo     tarefas do VS Code .. FALTANDO)
echo.

echo GIT:
git rev-parse --show-toplevel 2>nul
if errorlevel 1 (
  echo     esta pasta NAO e o clone do GitHub
) else (
  git log --oneline -1 2>nul
  git status -sb 2>nul
)
echo.

echo PLANILHA CONFIGURADA:
set "PLAN="
if exist "config.properties" (
  for /f "tokens=1,* delims==" %%a in ('findstr /b /c:"planilha.caminho=" config.properties') do set "PLAN=%%b"
)
if defined PLAN (
  echo     !PLAN!
  set "PLANW=!PLAN:/=\!"
  if exist "!PLANW!" (echo     planilha ............ OK) else (echo     planilha ............ NAO ENCONTRADA neste caminho)
) else (
  echo     nenhuma no config.properties
)
echo.

echo TESTE 1 - o Java consegue abrir o jar?
java -jar "gerador-arquivo-txt.jar" --ajuda
echo.

echo TESTE 2 - gera o txt da planilha de teste
if exist "exemplo\conferir.bat" call "exemplo\conferir.bat"
echo.

echo ============================================
echo  Fim do diagnostico
echo ============================================
echo.
echo Copie TUDO o que apareceu acima e mande para o Claude.
pause
endlocal
