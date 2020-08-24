#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
PORT=${1:-8888}
echo "Starting mitmproxy on port ${PORT} ..."
cd /home/mitmproxy/mitmproxy && \
./dev.sh && . ./venv/bin/activate && \
mitmdump \
  --listen-host 0.0.0.0 \
  --listen-port ${PORT} \
  --showhost \
  --no-http2 \
  --set block_global=false \
  --set block_private=false \
  --set termlog_verbosity=warn \
  --set flow_detail=0 \
  --set stream_large_bodies=5m \
  --set keep_host_header \
  -s ./bubble_debug.py \
  -s ./dns_spoofing.py \
  -s ./bubble_conn_check.py \
  -s ./bubble_modify.py \
  --mode transparent
