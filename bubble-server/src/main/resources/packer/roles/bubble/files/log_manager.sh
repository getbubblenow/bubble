#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
LOG=/var/log/bubble/log_manager.log

function log {
  echo "$(date): ${1}" >> ${LOG}
}

BUBBLE_LOGS_FOLDER=/var/log/bubble
POSTGRES_LOGS_FOLDER=$(readlink -f "${BUBBLE_LOGS_FOLDER}"/postgresql)
REDIS_LOG_FLAG_KEY="bubble.StandardSelfNodeService.bubble_server_logs_enabled"

REDIS_LOG_FLAG_VALUE=$(echo "get ${REDIS_LOG_FLAG_KEY}" | redis-cli | xargs echo | tr '[:upper:]' '[:lower:]')

log "starting log manager with REDIS_LOG_FLAG_VALUE=${REDIS_LOG_FLAG_VALUE}"
if [[ ${REDIS_LOG_FLAG_VALUE} == true ]]; then
  is_reload_needed=false
  is_psql_restart_needed=false
  # Cannot use -L option in find here as links are actually find's target:
  for logFile in $(find "${BUBBLE_LOGS_FOLDER}"/* -type l ! -name postgresql); do
    log "recreating real bubble log file: ${logFile}"
    rm "${logFile}"
    touch "${logFile}"
    if [[ "${logFile}" == "${LOG}" ]]; then
      log "...starting fresh log after activation..."
    fi
    is_reload_needed=true
  done
  for psqlLogFile in $(find "${POSTGRES_LOGS_FOLDER}"/* -type l); do
    log "removing postgres link log file making room for a real one: ${logFile}"
    rm "${psqlLogFile}"
    is_psql_restart_needed=true
  done

  if [[ ${is_psql_restart_needed} == true ]]; then
    log "restarting postgres service"
    service postgresql restart
  fi
  if [[ ${is_reload_needed} == true ]]; then
    log "reloading supervisor"
    supervisorctl reload
  fi
else
  # following dir link with -L option, so no need for special postgres for loop in this case:
  for logFile in $(find -L "${BUBBLE_LOGS_FOLDER}"/* -type f); do
    log "force-creating link to /dev/null instead of log file ${logFile}"
    ln -sf /dev/null "${logFile}"
  done
fi

log "ending log manager"
