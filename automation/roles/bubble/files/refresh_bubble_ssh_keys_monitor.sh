#!/bin/bash

LOG=/tmp/bubble.ssh_keys_monitor.log

function die {
  echo 1>&2 "${1}"
  log "${1}"
  exit 1
}

function log {
  echo "$(date): ${1}" >> ${LOG}
}

SSH_KEY_BASE=/root/.ssh
if [[ ! -d ${SSH_KEY_BASE} ]] ; then
  die "SSH key directory ${SSH_KEY_BASE} not found"
fi

BUBBLE_KEY_MARKER=/home/bubble/.refresh_ssh_keys
ROOT_KEY_MARKER=${SSH_KEY_BASE}/.refresh_ssh_keys

if [[ ! -f ${BUBBLE_KEY_MARKER} ]] ; then
  touch ${BUBBLE_KEY_MARKER} && chown bubble ${BUBBLE_KEY_MARKER}
fi
if [[ ! -f ${ROOT_KEY_MARKER} ]] ; then
  touch ${ROOT_KEY_MARKER}
fi

log "Watching marker file..."
while : ; do
  if [[ $(stat -c %Y ${BUBBLE_KEY_MARKER}) -gt $(stat -c %Y ${ROOT_KEY_MARKER}) ]] ; then
    touch ${ROOT_KEY_MARKER}
    sleep 5s
    log "Refreshing Bubble SSH keys..."
    /usr/local/sbin/refresh_bubble_ssh_keys.sh && log "Bubble SSH keys successfully refreshed" || log "Error refreshing Bubble SSH keys"
  fi
  sleep 10s
done
