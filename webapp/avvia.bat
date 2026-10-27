@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   WatchTracker - avvio della webapp
echo ============================================
echo.

rem --- Individua il Java che usera' Maven (JAVA_HOME se impostato, altrimenti quello nel PATH)
if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
  echo JAVA_HOME = %JAVA_HOME%
) else (
  set "JAVA_EXE=java"
  echo JAVA_HOME non impostato: uso il java nel PATH
)

set "JV_FILE=%TEMP%\watchtracker_javaver.txt"
"%JAVA_EXE%" -version > "%JV_FILE%" 2>&1
if errorlevel 1 (
  echo.
  echo [ERRORE] Java non trovato in "%JAVA_EXE%".
  echo          Installa un JDK 17 o superiore e imposta JAVA_HOME alla sua cartella.
  del "%JV_FILE%" 2>nul
  goto :fine_errore
)

set "JV="
for /f "tokens=3" %%v in ('findstr /i /c:"version" "%JV_FILE%"') do if not defined JV set "JV=%%~v"
del "%JV_FILE%" 2>nul
for /f "tokens=1,2 delims=." %%a in ("%JV%") do (
  set "JMAJ=%%a"
  set "JMIN=%%b"
)
if "%JMAJ%"=="1" set "JMAJ=%JMIN%"
echo Java trovato: %JV%

if %JMAJ% LSS 17 (
  echo.
  echo [ERRORE] Serve un JDK 17 o superiore, ma JAVA_HOME/PATH punta a Java %JV%.
  echo          Imposta JAVA_HOME alla cartella di un JDK 17+ ^(es. set JAVA_HOME=C:\util\jdk-17.0.11^)
  echo          oppure cambialo nelle variabili d'ambiente di Windows e riapri il terminale.
  goto :fine_errore
)

echo.
echo Avvio con Maven Wrapper... al primo avvio scarica Maven e le dipendenze ^(serve internet^).
echo Quando compare "Started WatchTrackerApplication" apri http://localhost:8080
echo Per fermare: CTRL+C
echo.

call "%~dp0mvnw.cmd" spring-boot:run %*
if errorlevel 1 (
  echo.
  echo [ERRORE] L'avvio e' fallito: leggi i messaggi qui sopra.
  goto :fine_errore
)
goto :eof

:fine_errore
echo.
pause
exit /b 1
