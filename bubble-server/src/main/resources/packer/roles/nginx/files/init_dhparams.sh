#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
LOG=/var/log/bubble/dhparams.log
DH_PARAMS=/etc/nginx/dhparams.pem

function log {
  echo "$(date): ${1}" | tee -a ${LOG}
}

function log_dhparam {
  if [[ -f "${DH_PARAMS}" ]] ; then
    if [[ -s "${DH_PARAMS}" ]] ; then
      cat ${DH_PARAMS} | tee -a ${LOG}
    else
      echo "(${DH_PARAMS} file exists but is empty)" | tee -a ${LOG}
    fi
  else
    echo "(${DH_PARAMS} file does not exist)" | tee -a ${LOG}
  fi
}

rval=255
start=$(date +%s)
TIMEOUT=600  # 10 minute timeout
RUN_OPENSSL=${1-wait}

while [[ $(expr $(date +%s) - ${start}) -le ${TIMEOUT} ]] ; do

  if [[ -s ${DH_PARAMS} && $(grep -c "BEGIN DH PARAMETERS" ${DH_PARAMS}) -gt 0 ]] ; then
    log "BEGIN-PRE-SUCCESS: ${DH_PARAMS} is already OK:"
    log_dhparam
    log "END-PRE-SUCCESS"
    exit 0
  else
    log "BEGIN-PRE-FAILURE: ${DH_PARAMS} is NOT OK:"
    log_dhparam
    log "END-PRE-FAILURE"
  fi

  if [[ ${RUN_OPENSSL} == 'run' ]] ; then
    log "BEGIN-RUNNING: openssl dhparam -out ${DH_PARAMS} 2048 ..."
    /usr/bin/openssl dhparam -out ${DH_PARAMS} 2048 2>&1 | tee -a ${LOG}
    log "END-RUNNING: openssl dhparam -out ${DH_PARAMS} 2048 ..."
    rval=$?
    log "BEGIN-RUNNING-COMPLETED: openssl dhparam -out ${DH_PARAMS} 2048 returned exit status ${rval} with contents: "
    log_dhparam
    log "END-RUNNING-COMPLETED"

    HEADER_COUNT=$(grep -c "BEGIN DH PARAMETERS" ${DH_PARAMS})
    if [[ ${rval} -eq 0 && -s ${DH_PARAMS} && $(cat ${DH_PARAMS} | tr -d '\n' | tr -d '[[:blank:]]' | wc -c) -gt 100 && ${HEADER_COUNT} -gt 0 ]] ; then
      log "BEGIN-SUCCESS: created ${DH_PARAMS}: "
      log_dhparam
      log "END-SUCCESS (will recheck)"
    fi

    if [[ ${rval} -ne 0 ]] ; then
      log "BEGIN-ERROR: command 'openssl dhparam -out ${DH_PARAMS} 2048' returned ${rval}, retrying. dhparams="
      log_dhparam
      log "END-ERROR"

    elif [[ ! -s ${DH_PARAMS} || $(cat ${DH_PARAMS} | tr -d '\n' | tr -d '[[:blank:]]' | wc -c) -le 100 ]] ; then
      log "BEGIN-ERROR: command 'openssl dhparam -out ${DH_PARAMS} 2048' returned ${rval} and produced empty (or short) file, retrying. dhparams="
      log_dhparam
      log "END-ERROR"

    elif [[ ${HEADER_COUNT} -le 0 ]] ; then
      log "BEGIN-ERROR: command 'openssl dhparam -out ${DH_PARAMS} 2048' returned ${rval} and produced invalid file, retrying. dhparams="
      log_dhparam
      log "END-ERROR"
    fi
  fi

  log "WAIT -- sleeping before next check"
  sleep 5s
done

log "BEGIN-TIMEOUT: failed to create ${DH_PARAMS} dhparams="
log_dhparam
log "END-TIMEOUT"

exit 1
