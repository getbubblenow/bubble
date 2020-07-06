#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
ALGO_BASE="$(cd "$(dirname "$0")" && pwd)"
cd "${ALGO_BASE}" || exit 1

virtualenv -p python3 .env \
  && source .env/bin/activate \
  && python3 -m pip install -U pip virtualenv \
  && python3 -m pip install -r requirements.txt
