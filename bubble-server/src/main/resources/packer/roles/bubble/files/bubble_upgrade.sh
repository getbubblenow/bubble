#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
BUBBLE_HOME="/home/bubble"
UPGRADE_JAR="${BUBBLE_HOME}/upgrade.jar"
BUBBLE_JAR="${BUBBLE_HOME}/api/bubble.jar"
LOG=/tmp/bubble.upgrade.log

function die {
  echo 1>&2 "${1}"
  log "${1}"
  exit 1
}

function log {
  echo "$(date): ${1}" >> ${LOG}
}

function verify_api_ok {
    log "verify_api_ok: Restarting API..."
    if supervisorctl restart bubble > /dev/null 2>> ${LOG} ; then
      log "verify_api_ok: Restarted API"
    else
      log "verify_api_ok: Error restarting API"
      echo "error"
      return
    fi

    sleep 20s
    CURL_STATUS=255
    START_VERIFY=$(date +%s)
    VERIFY_TIMEOUT=180
    VERIFY_URL="https://$(hostname):1443/api/auth/ready"
    while [[ $(expr $(date +%s) - ${START_VERIFY}) -le ${VERIFY_TIMEOUT} ]] ; do
      log "verify_api_ok: Verifying ${VERIFY_URL} is OK...."
      CURL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${VERIFY_URL}")
      if [[ -z "${CURL_STATUS}" || ${CURL_STATUS} -ne 200 ]] ; then
        log "verify_api_ok: curl ${VERIFY_URL} returned not-ok HTTP status: ${CURL_STATUS}"
        sleep 4s
        continue
      else
        break
      fi
    done

    log "verify_api_ok: while loop ended, CURL_STATUS=${CURL_STATUS}, (date - start)=$(expr $(date +%s) - ${START_VERIFY}), VERIFY_TIMEOUT=${VERIFY_TIMEOUT}"
    if [[ ! -z "${CURL_STATUS}" && ${CURL_STATUS} -eq 200 ]] ; then
      echo "ok"
    else
      echo "error"
    fi
}

BACKUP_JAR="$(mktemp /tmp/bubble.jar.XXXXXXX)"
log "Backing up to ${BACKUP_JAR} ..."
cp "${BUBBLE_JAR}" "${BACKUP_JAR}" || die "Error backing up existing jar before upgrade ${BUBBLE_JAR} ${BACKUP_JAR}"

log "Upgrading..."
mv "${UPGRADE_JAR}" "${BUBBLE_JAR}" || die "Error moving ${UPGRADE_JAR} -> ${BUBBLE_JAR}"

log "Verifying upgrade..."
API_OK=$(verify_api_ok)
if [[ -z "${API_OK}" || "${API_OK}" != "ok" ]] ; then
  log "Error starting upgraded API (API_OK=${API_OK}), reverting...."
  cp "${BACKUP_JAR}" "${BUBBLE_JAR}" || die "Error restoring API jar from backup!"
  API_OK=$(verify_api_ok)
  if [[ -z "${API_OK}" || "${API_OK}" != "ok" ]] ; then
    die "Error starting API from backup (API_OK=${API_OK})"
  fi
else
  log "Upgrading web site files..."
  cd ~bubble && unzip -o "${BUBBLE_JAR}" 'site/*' && chown -R bubble:bubble site || die "Error updating web files..."
fi
