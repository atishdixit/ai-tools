@echo off
setlocal EnableExtensions EnableDelayedExpansion
rem ============================================================================
rem  Starts the company chatbot and opens it in the browser: http://localhost:8090
rem
rem    run.bat                 start (runs setup.bat first if the app has not been built)
rem    run.bat nobrowser       do not open the browser
rem
rem  Put the company's files in  company-data\  and click "Re-index documents" in the page
rem  (or restart) to teach the bot new information. Environment: CHATBOT_PORT, CHATBOT_COMPANY,
rem  CHATBOT_LLM_MODEL, CHATBOT_EMBED_MODEL, JAVA_OPTS.
rem ============================================================================

cd /d "%~dp0"
if not defined CHATBOT_PORT set "CHATBOT_PORT=8090"

if not exist "target\company-chatbot.jar" (
    echo The application has not been built yet; running setup first ...
    call "%~dp0setup.bat" || exit /b 1
)

rem make sure Ollama is answering (setup.bat does the full check and the downloads)
curl -s http://localhost:11434/api/tags >nul 2>nul
if errorlevel 1 (
    echo Ollama is not running; running setup to start it ...
    call "%~dp0setup.bat" || exit /b 1
)

netstat -ano | findstr /r /c:":%CHATBOT_PORT% .*LISTENING" >nul
if not errorlevel 1 (
    echo [ERROR] Port %CHATBOT_PORT% is already in use ^(is the chatbot already running?^).
    echo         Close the other program, or choose another port:  set CHATBOT_PORT=8091
    exit /b 1
)

if /i not "%~1"=="nobrowser" (
    start "" /b cmd /c "ping -n 9 127.0.0.1 >nul & start http://localhost:%CHATBOT_PORT%"
)

echo.
echo Starting the chatbot on http://localhost:%CHATBOT_PORT%  ^(press Ctrl+C to stop^)
echo The first start indexes the documents, which takes about 20 seconds.
echo.
java %JAVA_OPTS% -jar "target\company-chatbot.jar"
exit /b %ERRORLEVEL%
