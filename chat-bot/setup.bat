@echo off
setlocal EnableExtensions EnableDelayedExpansion
rem ============================================================================
rem  Company chatbot: one-time setup (safe to run again)
rem
rem    1. checks Java 21+
rem    2. checks that Ollama is installed, and starts it if it is not running
rem    3. downloads the two models if they are missing (about 2.3 GB, once)
rem    4. builds the application jar
rem
rem    setup.bat              normal setup
rem    setup.bat rebuild      also rebuild the jar even if it exists
rem    setup.bat tests        also run the automated tests
rem
rem  Override the models with the environment variables CHATBOT_LLM_MODEL and CHATBOT_EMBED_MODEL.
rem ============================================================================

cd /d "%~dp0"
if not defined CHATBOT_LLM_MODEL   set "CHATBOT_LLM_MODEL=llama3.2:3b"
if not defined CHATBOT_EMBED_MODEL set "CHATBOT_EMBED_MODEL=nomic-embed-text"

echo.
echo === Company chatbot setup ===

rem ---------------------------------------------------------------- 1. Java
echo.
echo [1/4] Checking Java ...
where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Java was not found. Install JDK 21 or newer, open a NEW terminal and run setup.bat again.
    echo         Example:  winget install EclipseAdoptium.Temurin.21.JDK
    exit /b 1
)
set "JAVA_MAJOR="
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "RAW=%%~V"
    for /f "tokens=1 delims=." %%M in ("!RAW!") do set "JAVA_MAJOR=%%M"
)
if defined JAVA_MAJOR if !JAVA_MAJOR! LSS 21 (
    echo [ERROR] Java 21 or newer is required ^(found !JAVA_MAJOR!^).
    exit /b 1
)
echo       Java OK ^(version !JAVA_MAJOR!^)

rem ---------------------------------------------------------------- 2. Ollama
echo.
echo [2/4] Checking Ollama ...
set "OLLAMA_EXE="
for /f "delims=" %%P in ('where ollama 2^>nul') do if not defined OLLAMA_EXE set "OLLAMA_EXE=%%P"
if not defined OLLAMA_EXE if exist "%LOCALAPPDATA%\Programs\Ollama\ollama.exe" set "OLLAMA_EXE=%LOCALAPPDATA%\Programs\Ollama\ollama.exe"
if not defined OLLAMA_EXE (
    echo [ERROR] Ollama is not installed. It runs the language model on this PC for free.
    echo         Install it from https://ollama.com  ^(or:  winget install Ollama.Ollama^), then run setup.bat again.
    exit /b 1
)
echo       Found: !OLLAMA_EXE!
call :ensure_ollama_running || exit /b 1

rem ---------------------------------------------------------------- 3. Models
echo.
echo [3/4] Checking models ...
call :ensure_model "%CHATBOT_LLM_MODEL%"   || exit /b 1
call :ensure_model "%CHATBOT_EMBED_MODEL%" || exit /b 1

rem ---------------------------------------------------------------- 4. Build
echo.
echo [4/4] Building the application ...
set "REBUILD=0"
if /i "%~1"=="rebuild" set "REBUILD=1"
if /i "%~2"=="rebuild" set "REBUILD=1"
set "RUNTESTS=0"
if /i "%~1"=="tests" set "RUNTESTS=1"
if /i "%~2"=="tests" set "RUNTESTS=1"

if exist "target\company-chatbot.jar" if "!REBUILD!"=="0" if "!RUNTESTS!"=="0" (
    echo       target\company-chatbot.jar already exists ^(use "setup.bat rebuild" to rebuild^)
    goto :done
)
if "!RUNTESTS!"=="1" (
    echo       Building and running the automated tests ^(first run downloads dependencies^) ...
    call "%~dp0mvnw.cmd" -B -q clean package
) else (
    echo       Building ^(first run downloads dependencies, about a minute^) ...
    call "%~dp0mvnw.cmd" -B -q -DskipTests clean package
)
if errorlevel 1 (
    echo [ERROR] The build failed. Run  mvnw.cmd clean package  to see the details.
    exit /b 1
)

:done
echo.
echo === Setup complete ===
echo   Start the chatbot:  run.bat      ^(then open http://localhost:8090^)
echo   Measure its quality: eval.bat
exit /b 0

rem ---------------------------------------------------------------- helpers

:ensure_ollama_running
curl -s http://localhost:11434/api/tags >nul 2>nul
if not errorlevel 1 (
    echo       Ollama is running.
    exit /b 0
)
echo       Ollama is not running; starting it in the background ...
start "" /min "!OLLAMA_EXE!" serve
set /a TRIES=0
:wait_ollama
ping -n 2 127.0.0.1 >nul
curl -s http://localhost:11434/api/tags >nul 2>nul
if not errorlevel 1 (
    echo       Ollama started.
    exit /b 0
)
set /a TRIES+=1
if !TRIES! GEQ 30 (
    echo [ERROR] Ollama did not start. Open the Ollama app manually and run setup.bat again.
    exit /b 1
)
goto :wait_ollama

:ensure_model
set "MODEL=%~1"
"!OLLAMA_EXE!" list 2>nul | findstr /b /c:"%MODEL%" >nul
if not errorlevel 1 (
    echo       %MODEL% is installed.
    exit /b 0
)
echo       %MODEL% is not installed; downloading it ^(this can take a few minutes^) ...
"!OLLAMA_EXE!" pull %MODEL%
if errorlevel 1 (
    echo [ERROR] Could not download %MODEL%. Check that the model name is right ^(see https://ollama.com/library^) and that you are online.
    exit /b 1
)
exit /b 0
