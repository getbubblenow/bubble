#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
THIS_DIR="$(cd "$(dirname "${0}")" && pwd)"
LOG=/tmp/bubble.upgrade.log

function log {
  echo "$(date): ${1}" >> ${LOG}
}

while : ; do
  sleep 5
  if [[ -f "${UPGRADE_JAR}" ]] ; then
    "${THIS_DIR}/bubble_upgrade.sh"
    if [[ $? -eq 0 ]] ; then
      log "Upgrade completed successfully"
    else
      log "Upgrade failed"
    fi
    rm -f "${UPGRADE_JAR}"
  fi
done
