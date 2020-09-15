#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
PORT=${1:-8888}
MITM_PORT_FILE=/home/mitmproxy/mitmproxy_port
LOG=/var/log/bubble/mitm_monitor.log

function log {
  echo "[mitm${PORT}] $(date): ${1}" | tee -a ${LOG}
}

if [[ -f ${MITM_PORT_FILE} ]] ; then
  MITM_PORT="$(cat ${MITM_PORT_FILE})"
  START=$(date +%s)
  TIMEOUT=30
  while [[ ! -s "${MITM_PORT_FILE}" ]] ; do
    log "MITM_PORT_FILE was empty: ${MITM_PORT_FILE} -- waiting for it to exist"
    if [[ $(expr $(date +%s) - ${START}) -gt ${TIMEOUT} ]] ; then
      log "timeout waiting for MITM_PORT_FILE to exist: ${MITM_PORT_FILE} -- starting anyway"
      break
    fi
    sleep 5s
  done
  if [[ -s ${MITM_PORT_FILE} ]] ; then
    MITM_PORT="$(cat ${MITM_PORT_FILE})"
    if [[ ! -z "${MITM_PORT}" && ${MITM_PORT} -ne ${PORT} ]] ; then
      log "Our port (${PORT}) is not the primary mitm port (${MITM_PORT}), delaying startup by 30 seconds"
      sleep 30s
    fi
  fi
fi

log "Starting mitmproxy on port ${PORT} ..."

VENV_DIR="/home/mitmproxy/mitmproxy/venv"
SETUP_VENV=1
if [[ -d ${VENV_DIR} && $(find ${VENV_DIR} -type f -name "redis*" | wc -c | tr -d ' ') -gt 0 ]] ; then
  log "venv dir looks OK, skipping venv setup"
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
