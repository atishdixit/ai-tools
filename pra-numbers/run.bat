@echo off
rem One-click start for Windows: creates a private virtual environment on first use, installs the UI library, and opens the app.
rem Needs Python 3.10+ (the "py" launcher that comes with python.org installers is used; "python" works too).
cd /d "%~dp0"

where py >nul 2>nul
if %errorlevel%==0 ( set "PYTHON=py -3" ) else ( set "PYTHON=python" )

if not exist ".venv\Scripts\python.exe" (
    echo Creating virtual environment in .venv ...
    %PYTHON% -m venv .venv
    if errorlevel 1 ( echo Could not create the virtual environment. Is Python 3.10+ installed? & exit /b 1 )
)

echo Installing / checking requirements ...
".venv\Scripts\python.exe" -m pip install --quiet --disable-pip-version-check -r requirements.txt
if errorlevel 1 ( echo Installing the requirements failed. & exit /b 1 )

echo Starting the app. Your browser opens at http://localhost:8501  (Ctrl+C here to stop)
".venv\Scripts\python.exe" -m streamlit run app.py
