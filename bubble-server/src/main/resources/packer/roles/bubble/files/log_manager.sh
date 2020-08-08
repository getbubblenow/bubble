#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
BUBBLE_LOGS_FOLDER=/var/log/bubble
REDIS_LOG_FLAG_KEY="bubble.StandardSelfNodeService.bubble_server_logs_enabled"

REDIS_LOG_FLAG_VALUE=$(echo "get ${REDIS_LOG_FLAG_KEY}" | redis-cli | xargs echo | tr '[:upper:]' '[:lower:]')

if [[ ${REDIS_LOG_FLAG_VALUE} == true ]]; then
  is_reload_needed=false
  for logFile in $(find "${BUBBLE_LOGS_FOLDER}"/* -type l); do
    rm "${logFile}"
    touch "${logFile}"
    is_reload_needed=true
  done
  if [[ ${is_reload_needed} == true ]]; then
    supervisorctl reload
  fi
else
  for logFile in $(find "${BUBBLE_LOGS_FOLDER}"/* -type f); do
    ln -sf /dev/null "${logFile}"
  done
fi
