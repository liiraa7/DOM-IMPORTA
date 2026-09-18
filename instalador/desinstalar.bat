@echo off
chcp 1252 >nul
setlocal
cd /d "%~dp0"

set "NOME=Adapted Dom Import"
set "DESTINO=%LOCALAPPDATA%\%NOME%"

echo ============================================
echo  Desinstalar o %NOME%
echo ============================================
echo.

if not exist "%DESTINO%" (
  echo O programa nao esta instalado nesta conta.
  pause
  exit /b 0
)

echo Vai apagar:
echo     %DESTINO%
echo.
echo O arquivo config.properties, com os seus campos, some junto.
echo Os arquivos txt ja gerados NAO sao tocados.
echo.
set /p RESPOSTA=Tem certeza? digite S para continuar: 
if /i not "%RESPOSTA%"=="S" (
  echo Cancelado. Nada foi apagado.
  pause
  exit /b 0
)

set "VBS=%TEMP%\adapted-remover.vbs"
> "%VBS%" echo Set w = CreateObject("WScript.Shell")
>> "%VBS%" echo Set f = CreateObject("Scripting.FileSystemObject")
>> "%VBS%" echo p = w.SpecialFolders("Desktop") ^& "\%NOME%.lnk"
>> "%VBS%" echo If f.FileExists(p) Then f.DeleteFile(p)
>> "%VBS%" echo p = w.SpecialFolders("StartMenu") ^& "\Programs\%NOME%.lnk"
>> "%VBS%" echo If f.FileExists(p) Then f.DeleteFile(p)
cscript //nologo "%VBS%" >nul 2>&1
del "%VBS%" >nul 2>&1

rmdir /s /q "%DESTINO%"
if exist "%DESTINO%" (
  echo ERRO: nao consegui apagar tudo. O programa esta aberto?
  pause
  exit /b 1
)

echo.
echo Desinstalado.
pause
endlocal
