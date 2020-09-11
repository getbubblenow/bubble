#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
LOG=/var/log/bubble/flex_keys_monitor.log

function die {
  echo 1>&2 "${1}"
  log "${1}"
  exit 1
}

function log {
  echo "$(date): ${1}" >> ${LOG}
}

SSH_KEY_BASE=/home/bubble-flex/.ssh
if [[ ! -d ${SSH_KEY_BASE} ]] ; then
  mkdir ${SSH_KEY_BASE}
fi
chown -R bubble-flex ${SSH_KEY_BASE} && chmod 700 ${SSH_KEY_BASE}

BUBBLE_FLEX_KEYS=/home/bubble/.ssh/flex_authorized_keys
AUTH_FLEX_KEYS=${SSH_KEY_BASE}/authorized_keys

if [[ ! -f ${AUTH_FLEX_KEYS} ]] ; then
  touch ${AUTH_FLEX_KEYS}
fi
chown bubble-flex ${AUTH_FLEX_KEYS} && chmod 600 ${AUTH_FLEX_KEYS}

if [[ ! -f ${BUBBLE_FLEX_KEYS} ]] ; then
  touch ${BUBBLE_FLEX_KEYS} && chown bubble ${BUBBLE_FLEX_KEYS} && chmod 600 ${BUBBLE_FLEX_KEYS} && sleep 2s
fi

log "Watching flex keys file ${BUBBLE_FLEX_KEYS} ..."
while : ; do
  if [[ $(stat -c %Y ${BUBBLE_FLEX_KEYS}) -gt $(stat -c %Y ${AUTH_FLEX_KEYS}) ]] ; then
    cat ${BUBBLE_FLEX_KEYS} > ${AUTH_FLEX_KEYS} \
      && log "Updated ${BUBBLE_FLEX_KEYS} > ${AUTH_FLEX_KEYS}" \
      || log "Error overwriting ${BUBBLE_FLEX_KEYS} > ${AUTH_FLEX_KEYS}"
    # Just for sanity's sake
    chown -R bubble-flex ${SSH_KEY_BASE} && chmod 700 ${SSH_KEY_BASE}
    chown bubble-flex ${AUTH_FLEX_KEYS} && chmod 600 ${AUTH_FLEX_KEYS}
  fi
  sleep 10s
done
