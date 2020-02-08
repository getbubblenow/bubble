#!/bin/bash

MITM_PORT=${1:?no port provided}
cd /home/mitmproxy/mitmproxy && \
./dev.sh && . ./venv/bin/activate && \
mitmdump \
  --listen-host 0.0.0.0 \
  --listen-port ${MITM_PORT} \
  --showhost \
  --no-http2 \
  --set block_global=true \
  --set block_private=false \
  --set termlog_verbosity=debug \
  --set flow_detail=3 \
  --set stream_large_bodies=5m \
  --set keep_host_header \
  -s ./dns_spoofing.py \
  -s ./bubble_modify.py \
  --mode transparent
