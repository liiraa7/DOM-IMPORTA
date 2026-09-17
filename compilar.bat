@echo off
chcp 1252 >nul
setlocal
cd /d "%~dp0"

rem  So precisa rodar isto se voce MEXER no codigo-fonte.
rem  O jar ja vem pronto no zip.

echo ============================================
echo  Gerador de Arquivo TXT - compilacao
echo ============================================
echo.

where javac >nul 2>&1
if errorlevel 1 (
  echo Voce tem apenas o Java de execucao, sem o compilador.
  echo Para compilar, instale o JDK:
  echo     winget install EclipseAdoptium.Temurin.21.JDK
  echo Para so USAR o programa, nao precisa: rode o rodar.bat.
  exit /b 1
)

set "FONTE="
if exist "src\br\com\triangulo\gerador\GeradorArquivo.java" set "FONTE=src\br\com\triangulo\gerador\GeradorArquivo.java"
if not defined FONTE if exist "GeradorArquivo.java" set "FONTE=GeradorArquivo.java"
if not defined FONTE for /r %%f in (GeradorArquivo.java) do if not defined FONTE set "FONTE=%%f"
if not defined FONTE (
  echo ERRO: nao encontrei o GeradorArquivo.java.
  exit /b 1
)

echo [1/3] compilando %FONTE% com alvo Java 8...
if exist out rmdir /s /q out
mkdir out
rem  --release 8 e a rede de seguranca: recusa qualquer API de Java 9+.
rem  -Werror porque o rito manda passar sem um aviso sequer.
rem  -options so cala o "source value 8 is obsolete" do proprio JDK novo.
javac --release 8 -Xlint:all,-options -Werror -encoding UTF-8 -d out "%FONTE%"
if errorlevel 1 goto :falhou

echo [2/3] montando o jar...
jar --create --file gerador-arquivo-txt.jar ^
    --main-class br.com.triangulo.gerador.GeradorArquivo -C out .
if errorlevel 1 goto :falhou

echo [3/3] conferindo contra a planilha-exemplo...
call exemplo\conferir.bat
if errorlevel 1 goto :falhou

echo.
echo PRONTO: gerador-arquivo-txt.jar  (roda em Java 8 ou superior)
echo.
echo Para gerar a pasta com o .exe (precisa do JDK 14 ou mais novo):
echo     jpackage --type app-image --name "Gerador de Arquivo TXT" ^^
echo         --input . --main-jar gerador-arquivo-txt.jar
goto :fim

:falhou
echo.
echo ABORTADO: a compilacao falhou.
exit /b 1

:fim
endlocal
