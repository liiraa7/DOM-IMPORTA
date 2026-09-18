@echo off
chcp 1252 >nul
setlocal
cd /d "%~dp0"

rem  ============================================================
rem   Monta o programa como aplicativo do Windows, com o Java
rem   embutido: a maquina de destino NAO precisa ter Java.
rem   Isto roda UMA VEZ, na sua maquina, e produz a pasta dist\.
rem  ============================================================

set "NOME=Adapted Dom Import"
set "EMPRESA=Triangulo Contabilidade"

rem  a versao sai do proprio codigo, para nao ter dois lugares para atualizar
set "VERSAO="
for /f "tokens=2 delims==" %%v in ('findstr /c:"static final String VERSAO" src\br\com\triangulo\gerador\GeradorArquivo.java') do set "VERSAO=%%v"
set "VERSAO=%VERSAO: =%"
set "VERSAO=%VERSAO:"=%"
set "VERSAO=%VERSAO:;=%"
if not defined VERSAO set "VERSAO=1.0.0"

echo ============================================
echo  %NOME% %VERSAO% - montagem do aplicativo
echo ============================================
echo.

where jpackage >nul 2>&1
if errorlevel 1 (
  echo Nao achei o jpackage. Ele vem no JDK 14 ou mais novo:
  echo     winget install EclipseAdoptium.Temurin.21.JDK
  echo Feche e abra o terminal depois de instalar.
  pause
  exit /b 1
)

echo [1/4] compilando e conferindo...
call compilar.bat
if errorlevel 1 (
  echo ABORTADO: a compilacao falhou.
  pause
  exit /b 1
)

echo.
echo [2/4] separando o que vai dentro do aplicativo...
if exist build rmdir /s /q build
mkdir build\staging
copy /y gerador-arquivo-txt.jar build\staging\ >nul
rem  Vai o MODELO, nao o seu config: o colega nao pode herdar a sua pasta
rem  nem a sua competencia. Na primeira abertura o programa cria o
rem  config.properties dele a partir deste modelo.
copy /y config-modelo.properties build\staging\ >nul

echo [3/4] montando o aplicativo com o Java embutido...
if exist dist rmdir /s /q dist
rem  --add-modules enxuga o Java embutido: 83 MB em vez de 157 MB.
rem  java.xml entra junto com java.desktop, mas fica declarado de proposito,
rem  para nao quebrar se isso mudar num Java futuro.
jpackage --type app-image ^
    --name "%NOME%" ^
    --app-version %VERSAO% ^
    --vendor "%EMPRESA%" ^
    --description "Gerador do arquivo de importacao no layout 6000/6100" ^
    --icon instalador\logo.ico ^
    --input build\staging ^
    --main-jar gerador-arquivo-txt.jar ^
    --main-class br.com.triangulo.gerador.GeradorArquivo ^
    --add-modules java.base,java.desktop,java.logging,java.xml ^
    --dest dist
if errorlevel 1 (
  echo ABORTADO: o jpackage falhou.
  pause
  exit /b 1
)
copy /y instalador\instalar.bat dist\ >nul
copy /y instalador\desinstalar.bat dist\ >nul
copy /y instalador\LEIA-ME.txt dist\ >nul

echo.
echo [4/4] tentando montar tambem o instalador .msi...
jpackage --type msi ^
    --name "%NOME%" ^
    --app-version %VERSAO% ^
    --vendor "%EMPRESA%" ^
    --description "Gerador do arquivo de importacao no layout 6000/6100" ^
    --icon instalador\logo.ico ^
    --input build\staging ^
    --main-jar gerador-arquivo-txt.jar ^
    --main-class br.com.triangulo.gerador.GeradorArquivo ^
    --add-modules java.base,java.desktop,java.logging,java.xml ^
    --win-per-user-install ^
    --win-shortcut ^
    --win-menu ^
    --win-menu-group "%EMPRESA%" ^
    --dest dist >nul 2>&1
if errorlevel 1 (
  echo     .msi nao foi gerado - falta o WiX Toolset v3 nesta maquina.
  echo     Sem problema: use a pasta dist\%NOME% com o instalar.bat.
  echo     Se quiser o .msi, instale o WiX v3 e rode este .bat de novo:
  echo     https://github.com/wixtoolset/wix3/releases
) else (
  echo     .msi gerado em dist\
)

echo.
echo ============================================
echo  PRONTO
echo ============================================
echo.
echo Para levar para outra maquina:
echo   1. compacte a pasta  dist  inteira em um zip
echo   2. na outra maquina, descompacte em qualquer lugar
echo   3. rode o  instalar.bat  que esta dentro
echo.
echo A maquina de destino NAO precisa ter Java: ele vai embutido.
echo.
pause
endlocal
