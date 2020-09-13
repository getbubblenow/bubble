#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
PORT=${1:-8888}
echo "Starting mitmproxy on port ${PORT} ..."

VENV_DIR="/home/mitmproxy/mitmproxy/venv"
SETUP_VENV=1
if [[ -d ${VENV_DIR} && $(find ${VENV_DIR} -type f -name "redis*" | wc -c | tr -d ' ') -gt 0 ]] ; then
  echo "venv dir looks OK, skipping venv setup"
  SETUP_VENV=0
fi

cd /home/mitmproxy/mitmproxy && \
./dev.sh ${SETUP_VENV} && . ./venv/bin/activate && \
BUBBLE_PORT=${PORT} mitmdump \
  --listen-host 0.0.0.0 \
  --listen-port ${PORT} \
  --showhost \
  --no-http2 \
  --set block_global=false \
  --set block_private=false \
  --set termlog_verbosity=warn \
  --set flow_detail=0 \
  --set stream_large_bodies=1 \
  --set keep_host_header \
  -s ./bubble_conn_check.py \
  -s ./bubble_request.py \
  -s ./bubble_modify.py \
  --mode transparent
