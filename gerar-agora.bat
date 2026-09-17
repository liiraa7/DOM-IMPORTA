@echo off
chcp 1252 >nul
setlocal
cd /d "%~dp0"

rem  Gera o txt sem abrir janela, usando o config.properties.
rem
rem  NO AGENDADOR DE TAREFAS do Windows, configure assim:
rem      Programa:              %~dp0gerar-agora.bat
rem      Adicionar argumentos:  --agendador
rem      Iniciar em:            %~dp0
rem  O --agendador tira o "pause" do fim: sem ele, uma falha deixaria a
rem  tarefa pendurada esperando alguem apertar uma tecla.
rem
rem  E deixe saida.sobrescrever=sempre no config.properties, senao a
rem  segunda execucao recusa gravar porque o txt do dia anterior existe.

set "PAUSAR=1"
if /i "%~1"=="--agendador" set "PAUSAR="

if not exist "gerador-arquivo-txt.jar" (
  echo Nao achei o gerador-arquivo-txt.jar nesta pasta:
  echo     %~dp0
  echo Voce esta na pasta que veio do GitHub?
  if defined PAUSAR pause
  exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
  echo Nao encontrei o Java nesta maquina.
  echo Instale o Java de execucao, 8 ou mais novo, e tente de novo.
  if defined PAUSAR pause
  exit /b 1
)

java -jar "gerador-arquivo-txt.jar" --console
if errorlevel 1 (
  echo.
  echo A geracao FALHOU. A mensagem esta logo acima, e tambem no
  echo gerador_arquivo.log desta pasta.
  echo.
  echo Se falar que o arquivo ja existe: apague o txt antigo, ou
  echo troque saida.sobrescrever para sempre no config.properties.
  if defined PAUSAR pause
  exit /b 1
)

echo.
echo Pronto. O caminho do txt esta na linha acima.
if defined PAUSAR pause
endlocal
