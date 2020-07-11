#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
THIS_DIR="$(cd "$(dirname "${0}")" && pwd)"

BUBBLE_HOME="/home/bubble"
UPGRADE_JAR="${BUBBLE_HOME}/upgrade.jar"
LOG=/tmp/bubble.upgrade.log

function log {
  echo "$(date): ${1}" >> ${LOG}
}

log "Watching ${UPGRADE_JAR} for upgrades"
while : ; do
  sleep 5
  if [[ -f "${UPGRADE_JAR}" ]] ; then
    log "${UPGRADE_JAR} exists, upgrading..."
    "${THIS_DIR}/bubble_upgrade.sh"
    if [[ $? -eq 0 ]] ; then
      log "Upgrade completed successfully"
    else
      log "Upgrade failed"
    fi
    rm -f "${UPGRADE_JAR}"
  fi
done
