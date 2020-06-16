#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
LOG=/tmp/init_certbot.log

function log {
  echo "$(date): ${1}" >> ${LOG}
}

LE_EMAIL="${1}"
SERVER_NAME="${2}"
SERVER_ALIAS="${3}"

START=$(date +%s)
ATTEMPT=0
while [ ${ATTEMPT} -le 20 ] ; do
  if [[ ${OK} -ne 1 ]] ; then
    log "certbot failed (attempt ${ATTEMPT})"
    sleep $(expr ${ATTEMPT})s
  fi
  OK=1
  ATTEMPT=$(expr ${ATTEMPT} + 1)
  if [[ $(find /etc/letsencrypt/accounts -type f -name regr.json | xargs grep -l \"${LE_EMAIL}\" | wc -l | tr -d ' ') -eq 0 ]] ; then
    log "certbot register starting: certbot register --agree-tos -m "${LE_EMAIL}" --non-interactive"
    certbot register --agree-tos -m "${LE_EMAIL}" --non-interactive 2>&1 | tee -a ${LOG} || OK=0
    log "certbot register completed, OK=${OK}"
  fi

  if [[ ${OK} -eq 1 ]] ; then
    if [[ ! -f /etc/letsencrypt/live/${SERVER_NAME}/fullchain.pem || ! -f /etc/letsencrypt/live/${SERVER_ALIAS}/fullchain.pem ]] ; then
      log "certbot certonly ${SERVER_NAME} starting: certbot certonly --standalone --non-interactive -d ${SERVER_NAME}"
      certbot certonly --standalone --non-interactive -d ${SERVER_NAME} 2>&1 | tee -a ${LOG} || OK=0
      log "certbot certonly ${SERVER_NAME} completed, OK=${OK}"
      if [[ ${OK} -eq 1 ]] ; then
        log "certbot certonly ${SERVER_ALIAS} starting: certbot certonly --standalone --non-interactive -d ${SERVER_ALIAS}"
        certbot certonly --standalone --non-interactive -d ${SERVER_ALIAS} 2>&1 | tee -a ${LOG} || OK=0
        log "certbot certonly ${SERVER_ALIAS} completed, OK=${OK}"
      fi
    else
      log "Starting certbot renew: certbot renew --standalone --non-interactive"
      certbot renew --standalone --non-interactive 2>&1 | tee -a ${LOG} || OK=0
      log "Starting certbot renew completed, OK=${OK}"
    fi
  fi

  if [[ ${OK} -eq 1 ]] ; then
    DURATION=$(expr $(date +%s) - ${START})
    log "init_certbot successful, duration=${DURATION} seconds"
    exit 0
  fi
done

DURATION=$(expr $(date +%s) - ${START})
log "init_certbot failed after ${ATTEMPT} attempts, duration=${DURATION} seconds"
exit 1
