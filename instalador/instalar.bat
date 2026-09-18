@echo off
chcp 1252 >nul
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem  ============================================================
rem   Instala o programa nesta maquina, para o usuario atual.
rem   Nao precisa de senha de administrador e nao precisa de Java.
rem  ============================================================

set "NOME=Adapted Dom Import"
set "ORIGEM=%~dp0%NOME%"
set "DESTINO=%LOCALAPPDATA%\%NOME%"

echo ============================================
echo  Instalando o %NOME%
echo ============================================
echo.

if not exist "%ORIGEM%\%NOME%.exe" (
  echo ERRO: nao achei a pasta "%NOME%" ao lado deste instalador.
  echo.
  echo Descompacte o zip inteiro antes de rodar - rodar de dentro
  echo do zip nao funciona.
  echo.
  pause
  exit /b 1
)

echo Vai instalar em:
echo     %DESTINO%
echo.

rem  Se ja houver instalacao, guarda o config para nao perder os campos
set "TINHA_CONFIG="
if exist "%DESTINO%\app\config.properties" (
  copy /y "%DESTINO%\app\config.properties" "%TEMP%\adapted-config-anterior.properties" >nul
  set "TINHA_CONFIG=1"
  echo Achei uma instalacao anterior. Vou manter as suas configuracoes.
  echo.
)

if exist "%DESTINO%" rmdir /s /q "%DESTINO%"
mkdir "%DESTINO%" 2>nul
xcopy "%ORIGEM%" "%DESTINO%" /e /i /q /y >nul
if errorlevel 1 (
  echo ERRO: nao consegui copiar os arquivos.
  echo O programa esta aberto? Feche e tente de novo.
  pause
  exit /b 1
)

if defined TINHA_CONFIG (
  copy /y "%TEMP%\adapted-config-anterior.properties" "%DESTINO%\app\config.properties" >nul
  del "%TEMP%\adapted-config-anterior.properties" >nul 2>&1
)

echo Criando os atalhos...
set "VBS=%TEMP%\adapted-atalhos.vbs"
> "%VBS%" echo Set w = CreateObject("WScript.Shell")
>> "%VBS%" echo alvo = "%DESTINO%\%NOME%.exe"
>> "%VBS%" echo Set atalho = w.CreateShortcut(w.SpecialFolders("Desktop") ^& "\%NOME%.lnk")
>> "%VBS%" echo atalho.TargetPath = alvo
>> "%VBS%" echo atalho.WorkingDirectory = "%DESTINO%"
>> "%VBS%" echo atalho.IconLocation = alvo
>> "%VBS%" echo atalho.Description = "Gerador do arquivo de importacao"
>> "%VBS%" echo atalho.Save
>> "%VBS%" echo Set menu = w.CreateShortcut(w.SpecialFolders("StartMenu") ^& "\Programs\%NOME%.lnk")
>> "%VBS%" echo menu.TargetPath = alvo
>> "%VBS%" echo menu.WorkingDirectory = "%DESTINO%"
>> "%VBS%" echo menu.IconLocation = alvo
>> "%VBS%" echo menu.Description = "Gerador do arquivo de importacao"
>> "%VBS%" echo menu.Save
cscript //nologo "%VBS%"
if errorlevel 1 echo AVISO: nao consegui criar os atalhos. O programa esta instalado assim mesmo.
del "%VBS%" >nul 2>&1

echo.
echo ============================================
echo  PRONTO
echo ============================================
echo.
echo O atalho "%NOME%" esta na area de trabalho e no menu Iniciar.
echo.
echo ANTES DE USAR, abra o programa e confira:
echo   - o caminho da planilha
echo   - Empresa, Tipo e Competencia
echo   - a pasta onde salvar o arquivo
echo.
echo Para desinstalar, rode o desinstalar.bat desta pasta.
echo.
pause
endlocal
