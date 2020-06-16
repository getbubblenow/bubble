#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
LOG=/tmp/bubble.algo_refresh_users_monitor.log

function die {
  echo 1>&2 "${1}"
  log "${1}"
  exit 1
}

function log {
  echo "$(date): ${1}" >> ${LOG}
}

ALGO_BASE=/root/ansible/roles/algo/algo
if [[ ! -d ${ALGO_BASE} ]] ; then
  die "Algo VPN directory ${ALGO_BASE} not found"
fi

CA_PASS_FILE="/home/bubble/.BUBBLE_ALGO_CA_KEY_PASSWORD"
if [[ ! -f "${CA_PASS_FILE}" ]] ; then
  die "No CA password file found: ${CA_PASS_FILE}"
fi
if [[ ! -f "${ALGO_BASE}/config.cfg.hbs" ]] ; then
  die "No ${ALGO_BASE}/config.cfg.hbs found"
fi

BUBBLE_USER_MARKER=/home/bubble/.algo_refresh_users
ALGO_USER_MARKER=${ALGO_BASE}/.algo_refresh_users

if [[ ! -f ${BUBBLE_USER_MARKER} ]] ; then
  touch ${BUBBLE_USER_MARKER} && chown bubble ${BUBBLE_USER_MARKER}
fi
if [[ ! -f ${ALGO_USER_MARKER} ]] ; then
  touch ${ALGO_USER_MARKER}
fi

log "Watching marker file..."
while : ; do
  if [[ $(stat -c %Y ${BUBBLE_USER_MARKER}) -gt $(stat -c %Y ${ALGO_USER_MARKER}) ]] ; then
    touch ${ALGO_USER_MARKER}
    sleep 5s
    log "Refreshing VPN users..."
    /usr/local/bin/algo_refresh_users.sh && log "VPN users successfully refreshed" || log "Error refreshing Algo VPN users"
  fi
  sleep 10s
done
