@echo off
setlocal EnableExtensions
rem ============================================================================
rem  Measures the chatbot: asks every question in eval\questions.json with the real local model,
rem  scores the answers and writes eval\report.md. Takes roughly 10 to 20 minutes on a CPU-only PC.
rem
rem    eval.bat                       all questions
rem    eval.bat --chatbot.eval.split=holdout     only the hold-out set (or dev)
rem    eval.bat --chatbot.eval.only=D1,O3        only these question ids
rem
rem  Exit code: 0 = all targets met, 1 = some target missed, 2 = could not run.
rem ============================================================================

cd /d "%~dp0"
if not exist "target\company-chatbot.jar" (
    call "%~dp0setup.bat" || exit /b 2
)
curl -s http://localhost:11434/api/tags >nul 2>nul
if errorlevel 1 (
    call "%~dp0setup.bat" || exit /b 2
)

java %JAVA_OPTS% -jar "target\company-chatbot.jar" --chatbot.eval.enabled=true --spring.main.web-application-type=none --logging.level.root=WARN --logging.level.com.example.chatbot=INFO %*
set "RC=%ERRORLEVEL%"
echo.
if exist "eval\report.md" echo Report: %~dp0eval\report.md
if "%RC%"=="0" echo Result: all targets met.
if "%RC%"=="1" echo Result: some targets were missed. See the report for every miss.
if "%RC%"=="2" echo Result: the evaluation could not run.
exit /b %RC%
