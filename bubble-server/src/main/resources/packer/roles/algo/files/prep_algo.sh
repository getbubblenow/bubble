#!/bin/bash

ALGO_BASE="$(cd "$(dirname "$0")" && pwd)"
cd "${ALGO_BASE}" || exit 1

virtualenv -p python3 .env \
  && source .env/bin/activate \
  && python3 -m pip install -U pip virtualenv \
  && python3 -m pip install -r requirements.txt
