#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
cd /home/mitmproxy/mitmproxy && \
./dev.sh && . ./venv/bin/activate && \
mitmdump \
  --listen-host 0.0.0.0 \
  --listen-port 8888 \
  --showhost \
  --no-http2 \
  --set block_global=true \
  --set block_private=false \
  --set termlog_verbosity=debug \
  --set flow_detail=3 \
  --set stream_large_bodies=5m \
  --set keep_host_header \
  -s ./dns_spoofing.py \
  -s ./bubble_passthru.py \
  -s ./bubble_modify.py \
  --mode transparent
