#!/usr/bin/env bash
# One-command start for macOS / Linux: creates a virtual environment on first use, installs the UI library, opens the app.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -x ".venv/bin/python" ]; then
  echo "Creating virtual environment in .venv ..."
  python3 -m venv .venv
fi

echo "Installing / checking requirements ..."
.venv/bin/python -m pip install --quiet --disable-pip-version-check -r requirements.txt

echo "Starting the app at http://localhost:8501  (Ctrl+C to stop)"
exec .venv/bin/python -m streamlit run app.py
