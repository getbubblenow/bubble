#!/bin/bash

BUBBLE_HOME="/home/bubble"
UPGRADE_JAR="${BUBBLE_HOME}/api/.upgrade.jar"
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
    log "Restarting API..."
    supervisorctl restart bubble || die "Error restarting bubble"

    OK=255
    START_VERIFY=$(date +%s)
    VERIFY_TIMEOUT=180
    VERIFY_URL="https://$(hostname):1443/api/auth/ready"
    if [[ ${OK} -ne 0 && $(expr $(date +%s) - ${START_VERIFY} -le ${VERIFY_TIMEOUT}) ]] ; then
      sleep 10s
      log "Verifying ${VERIFY_URL} is OK...."
      curl "${VERIFY_URL}" 2>&1 | tee -a ${LOG}
      OK=$?
    fi

    if [[ ${OK} -eq 0 ]] ; then
      echo "ok"
    else
      echo "error"
    fi
}

BACKUP_JAR=$(mktemp /tmp/bubble.jar.XXXXXXX)
log "Backing up to ${BACKUP_JAR} ..."
cp ${BUBBLE_JAR} ${BACKUP_JAR} || die "Error backing up existing jar before upgrade ${BUBBLE_JAR} ${BACKUP_JAR}"

log "Upgrading..."
mv ${UPGRADE_JAR} ${BUBBLE_JAR} || die "Error moving ${UPGRADE_JAR} -> ${BUBBLE_JAR}"

log "Verifying upgrade..."
API_OK=$(verify_api_ok)
if [[ -z "${API_OK}" || "${API_OK}" != "ok" ]] ; then
  log "Error starting upgraded API, reverting...."
  cp ${BACKUP_JAR} ${BUBBLE_JAR} || die "Error restoring API jar from backup!"
  API_OK=$(verify_api_ok)
  if [[ -z "${API_OK}" || "${API_OK}" != "ok" ]] ; then
    log "Error starting API from backup!"
  fi
else
  log "Upgrading web site files..."
  cd ~bubble && jar xf ${BUBBLE_JAR} site && chown -R bubble:bubble site || die "Error updating web files..."
fi
