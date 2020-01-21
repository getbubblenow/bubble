#!/bin/bash

LOG=/tmp/bubble.mitmdump_monitor.log

function die {
  echo 1>&2 "${1}"
  log "${1}"
  exit 1
}

function log {
  echo "$(date): ${1}" >> ${LOG}
}

BUBBLE_MITM_MARKER=/home/bubble/.mitmdump_monitor
ROOT_KEY_MARKER=/usr/share/bubble/mitmdump_monitor

# Start with MITM proxy turned off
if [[ ! -f ${BUBBLE_MITM_MARKER} ]] ; then
  echo off > ${BUBBLE_MITM_MARKER} && chown bubble ${BUBBLE_MITM_MARKER}
fi
if [[ ! -f ${ROOT_KEY_MARKER} ]] ; then
  sleep 1s
  mkdir -p "$(dirname ${ROOT_KEY_MARKER})" && chmod 755 "$(dirname ${ROOT_KEY_MARKER})"
  echo on > ${ROOT_KEY_MARKER} && touch ${ROOT_KEY_MARKER} && chmod 644 ${ROOT_KEY_MARKER}
fi

function ensureMitmOn {
  ok80=$(iptables -vnL PREROUTING  -t nat | tail +3 | grep REDIRECT | grep -c "tcp dpt:80 redir ports 8888")
  if [[ ${ok80} -eq 0 ]] ; then
    log "Enabling MITM port forwarding on TCP port 80 -> 8888"
    iptables -I PREROUTING 1 -t nat -p tcp --dport 80 -j REDIRECT --to-ports 8888 || log "Error enabling MITM port 80 forwarding"
  fi
  ok443=$(iptables -vnL PREROUTING  -t nat | tail +3 | grep REDIRECT | grep -c "tcp dpt:443 redir ports 8888")
  if [[ ${ok443} -eq 0 ]] ; then
    log "Enabling MITM port forwarding on TCP port 443 -> 8888"
    iptables -I PREROUTING 1 -t nat -p tcp --dport 443 -j REDIRECT --to-ports 8888 || log "Error enabling MITM port 443 forwarding"
  fi
  echo -n on > ${ROOT_KEY_MARKER}
}

function ensureMitmOff {
  log "Disabling MITM port forwarding"
  iptables -F PREROUTING  -t nat || log "Error disabling MITM port forwarding"
  echo -n off > ${ROOT_KEY_MARKER}
}

log "Watching marker file ${BUBBLE_MITM_MARKER} ..."
while : ; do
  if [[ $(stat -c %Y ${BUBBLE_MITM_MARKER}) -gt $(stat -c %Y ${ROOT_KEY_MARKER}) ]] ; then
    if [[ ! -z "$(cmp -b ${ROOT_KEY_MARKER} ${BUBBLE_MITM_MARKER})" ]] ; then
      if [[ "$(cat ${BUBBLE_MITM_MARKER} | tr -d [[:space:]])" == "on" ]] ; then
        ensureMitmOn
      elif [[ "$(cat ${BUBBLE_MITM_MARKER} | tr -d [[:space:]])" == "off" ]] ; then
        ensureMitmOff
      else
        log "Error: marker file ${BUBBLE_MITM_MARKER} contained invalid value: $(cat ${BUBBLE_MITM_MARKER} | head -c 5)"
      fi
    fi
  fi
  sleep 5s
done
