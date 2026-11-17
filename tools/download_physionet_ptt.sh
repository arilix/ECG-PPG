#!/usr/bin/env bash
set -euo pipefail

# Download PhysioNet Pulse Transit Time PPG Dataset v1.1.0.
# Usage: bash tools/download_physionet_ptt.sh [destination]

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DESTINATION="${1:-${ROOT_DIR}/data/physionet/pulse-transit-time-ppg/1.1.0}"
VENV_DIR="${ROOT_DIR}/.venv-physionet"

if ! command -v python3 >/dev/null 2>&1; then
  echo "Error: python3 is required (Python 3.10 or newer)." >&2
  exit 1
fi

PYTHON_VERSION="$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')"
if ! python3 -c 'import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)'; then
  echo "Error: Python 3.10+ required; found ${PYTHON_VERSION}." >&2
  exit 1
fi

mkdir -p "$(dirname "${DESTINATION}")"
if [[ ! -x "${VENV_DIR}/bin/python" ]]; then
  python3 -m venv "${VENV_DIR}"
fi

"${VENV_DIR}/bin/python" -m pip install --upgrade physionet
"${VENV_DIR}/bin/physionet" download pulse-transit-time-ppg \
  --version 1.1.0 \
  --output "${DESTINATION}"

echo "Download complete: ${DESTINATION}"
