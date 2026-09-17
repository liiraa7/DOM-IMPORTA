@echo off
chcp 1252 >nul
setlocal
cd /d "%~dp0"

set "JAR=%~dp0gerador-arquivo-txt.jar"

if not exist "%JAR%" (
  echo ============================================
  echo  Nao achei o gerador-arquivo-txt.jar
  echo ============================================
  echo.
  echo Esta pasta:
  echo     %~dp0
  echo nao tem o jar do programa.
  echo.
  echo Confira se voce esta na pasta que veio do GitHub - a que tem
  echo as pastas src e exemplo dentro. Se voce mexeu no codigo,
  echo rode o compilar.bat antes.
  echo.
  pause
  exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
  echo ============================================
  echo  Nao encontrei o Java nesta maquina
  echo ============================================
  echo.
  echo Instale o Java de execucao, 8 ou mais novo, e tente de novo:
  echo     winget install EclipseAdoptium.Temurin.21.JRE
  echo.
  pause
  exit /b 1
)

rem  O javaw abre a janela sem console: se algo der errado ele falha
rem  CALADO e parece que o programa nao abriu. Por isso conferimos
rem  antes, com o java normal, se o jar abre mesmo.
java -jar "%JAR%" --ajuda >nul 2>"%TEMP%\gerador_erro.txt"
if errorlevel 1 (
  echo ============================================
  echo  O Java nao conseguiu abrir o programa
  echo ============================================
  echo.
  type "%TEMP%\gerador_erro.txt"
  echo.
  echo Se a mensagem falar em "class file version", o jar foi compilado
  echo para um Java mais novo do que o desta maquina: rode o compilar.bat,
  echo que usa o alvo Java 8.
  echo.
  pause
  exit /b 1
)
del "%TEMP%\gerador_erro.txt" >nul 2>&1

where javaw >nul 2>&1
if errorlevel 1 (
  rem  Sem javaw nesta maquina: abre com o java normal e fica um
  rem  console preto atras da janela. Feio, mas funciona.
  start "Gerador de Arquivo TXT" java -jar "%JAR%"
) else (
  start "" javaw -jar "%JAR%"
)
endlocal
