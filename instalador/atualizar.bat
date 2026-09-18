@echo off
chcp 1252 >nul
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem  ============================================================
rem   Atualiza o programa ja instalado nesta maquina.
rem   Troca so o miolo - 66 KB - e nao mexe nas configuracoes.
rem   Nao precisa desinstalar nem reinstalar nada.
rem
rem   De onde ele tira a versao nova, nesta ordem:
rem     1. o gerador-arquivo-txt.jar que estiver AO LADO deste .bat
rem     2. a pasta da rede configurada logo abaixo
rem   Para usar a rede, ponha aqui o caminho onde fica a versao nova:
rem  ============================================================

set "NOME=Adapted Dom Import"
set "ORIGEM_REDE="

set "DESTINO=%LOCALAPPDATA%\%NOME%\app"
set "JAR=gerador-arquivo-txt.jar"

echo ============================================
echo  Atualizar o %NOME%
echo ============================================
echo.

rem  ---- o programa esta instalado?
if not exist "%DESTINO%\%JAR%" (
  echo O %NOME% nao esta instalado nesta conta.
  echo.
  echo Procurei em:
  echo     %DESTINO%
  echo.
  echo Use o instalar.bat para instalar pela primeira vez.
  echo.
  pause
  exit /b 1
)

rem  ---- esta aberto? o arquivo fica travado e a copia falha
tasklist /fi "imagename eq %NOME%.exe" 2>nul | find /i "%NOME%.exe" >nul
if not errorlevel 1 (
  echo O programa esta ABERTO. Feche a janela dele e rode este
  echo atualizar.bat de novo.
  echo.
  pause
  exit /b 1
)

rem  ---- de onde vem a versao nova
set "ORIGEM="
if exist "%~dp0%JAR%" set "ORIGEM=%~dp0"
if not defined ORIGEM if defined ORIGEM_REDE if exist "%ORIGEM_REDE%\%JAR%" set "ORIGEM=%ORIGEM_REDE%\"

if not defined ORIGEM (
  echo Nao achei a versao nova do programa.
  echo.
  echo Ponha o arquivo %JAR% na mesma pasta deste
  echo atualizar.bat, ou configure a pasta da rede na linha
  echo ORIGEM_REDE, la em cima neste arquivo.
  echo.
  pause
  exit /b 1
)

echo Versao nova encontrada em:
echo     %ORIGEM%
echo.

rem  ---- ja e o mesmo arquivo? entao nao ha o que fazer, e dizer
rem  "atualizado" seria mentira
fc /b "%ORIGEM%%JAR%" "%DESTINO%\%JAR%" >nul 2>&1
if not errorlevel 1 (
  echo ============================================
  echo  NADA A FAZER
  echo ============================================
  echo.
  echo O programa instalado JA E esta versao - o arquivo e
  echo identico, byte a byte. Nao troquei nada.
  echo.
  echo Se voce esperava uma versao nova, entao o arquivo que veio
  echo junto deste .bat e o antigo: gere o pacote de atualizacao de
  echo novo, ou peca o arquivo atualizado a quem cuida do programa.
  echo.
  pause
  exit /b 0
)

echo Instalado agora:
for %%F in ("%DESTINO%\%JAR%") do echo     %%~tF   %%~zF bytes
echo Versao nova:
for %%F in ("%ORIGEM%%JAR%") do echo     %%~tF   %%~zF bytes
echo.

rem  ---- guarda a versao atual, para poder voltar atras
copy /y "%DESTINO%\%JAR%" "%DESTINO%\%JAR%.anterior" >nul
if errorlevel 1 (
  echo AVISO: nao consegui guardar copia da versao atual.
  echo.
)

copy /y "%ORIGEM%%JAR%" "%DESTINO%\%JAR%" >nul
if errorlevel 1 (
  echo ERRO: nao consegui copiar o arquivo novo.
  echo.
  echo Quase sempre e porque o programa ainda esta aberto.
  echo Feche e tente de novo.
  echo.
  pause
  exit /b 1
)

rem  ---- o modelo de configuracao tambem pode ter mudado
if exist "%ORIGEM%config-modelo.properties" (
  copy /y "%ORIGEM%config-modelo.properties" "%DESTINO%\" >nul
)

echo ============================================
echo  ATUALIZADO
echo ============================================
echo.
echo Ficou assim:
for %%F in ("%DESTINO%\%JAR%") do echo     %%~tF   %%~zF bytes
echo.
echo As suas configuracoes NAO foram mexidas: planilha, empresa,
echo tipo, competencia e pasta continuam como estavam.
echo.
echo Abra o programa e confira a versao no canto direito da
echo faixa azul.
echo.
echo Se algo sair errado, a versao anterior esta guardada em:
echo     %DESTINO%\%JAR%.anterior
echo Para voltar atras, renomeie ela tirando o ".anterior".
echo.
pause
endlocal
