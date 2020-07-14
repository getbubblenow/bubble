#!/bin/bash

LOG=/tmp/dhparams.log
DH_PARAMS=/etc/nginx/dhparams.pem

function log {
  echo "$(date): ${1}" | tee -a ${LOG}
}

rval=255
start=$(date +%s)
TIMEOUT=600  # 10 minute timeout
RUN_OPENSSL=${1-wait}

while [[ $(expr $(date +%s) - ${start}) -le ${TIMEOUT} ]] ; do

  if [[ -s ${DH_PARAMS} && $(grep -c "BEGIN DH PARAMETERS" ${DH_PARAMS}) -gt 0 ]] ; then
    log "BEGIN-PRE-SUCCESS: ${DH_PARAMS} is already OK:"
    cat ${DH_PARAMS} >> ${LOG}
    log "END-PRE-SUCCESS"
    exit 0
  else
    log "BEGIN-PRE-FAILURE: ${DH_PARAMS} is NOT OK:"
    cat ${DH_PARAMS} >> ${LOG}
    log "END-PRE-FAILURE"
  fi

  if [[ ${RUN_OPENSSL} == 'run' ]] ; then
    log "BEGIN-RUNNING: openssl dhparam -out ${DH_PARAMS} 2048 ..."
    /usr/bin/openssl dhparam -out ${DH_PARAMS} 2048 2>&1 | tee -a ${LOG}
    log "END-RUNNING: openssl dhparam -out ${DH_PARAMS} 2048 ..."
    rval=$?
    log "BEGIN-RUNNING-COMPLETED: openssl dhparam -out ${DH_PARAMS} 2048 returned exit status ${rval} with contents: "
    cat ${DH_PARAMS} >> ${LOG}
    log "END-RUNNING-COMPLETED"

    HEADER_COUNT=$(grep -c "BEGIN DH PARAMETERS" ${DH_PARAMS})
    if [[ ${rval} -eq 0 && -s ${DH_PARAMS} && ${HEADER_COUNT} -gt 0 ]] ; then
      log "BEGIN-SUCCESS: created ${DH_PARAMS}: "
      cat ${DH_PARAMS} >> ${LOG}
      log "END-SUCCESS"
      exit 0
    fi

    if [[ ${rval} -ne 0 ]] ; then
      log "BEGIN-ERROR: command 'openssl dhparam -out ${DH_PARAMS} 2048' returned ${rval}, retrying. dhparams="
      cat ${DH_PARAMS} >> ${LOG}
      log "END-ERROR"

    elif [[ ! -s ${DH_PARAMS} ]] ; then
      log "BEGIN-ERROR: command 'openssl dhparam -out ${DH_PARAMS} 2048' returned ${rval} and produced empty file, retrying. dhparams="
      cat ${DH_PARAMS} >> ${LOG}
      log "END-ERROR"

    elif [[ ${HEADER_COUNT} -le 0 ]] ; then
      log "BEGIN-ERROR: command 'openssl dhparam -out ${DH_PARAMS} 2048' returned ${rval} and produced invalid file, retrying. dhparams="
      cat ${DH_PARAMS} >> ${LOG}
      log "END-ERROR"
    fi
  fi

  log "WAIT -- sleeping before next check"
  sleep 5s
done

log "BEGIN-TIMEOUT: failed to create ${DH_PARAMS} dhparams="
cat ${DH_PARAMS} >> ${LOG}
log "END-TIMEOUT"

exit 1
