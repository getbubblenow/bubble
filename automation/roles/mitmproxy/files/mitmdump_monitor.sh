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
  log "Flushing PREROUTING before enabling MITM services"
  iptables -F PREROUTING  -t nat || log "Error flushing port forwarding when enabling MITM services"
  log "Enabling MITM port forwarding on TCP port 80 -> 8888"
  iptables -I PREROUTING 1 -t nat -p tcp --dport 80 -j REDIRECT --to-ports 8888 || log "Error enabling MITM port forwarding 80 -> 8888"
  log "Enabling MITM port forwarding on TCP port 443 -> 8888"
  iptables -I PREROUTING 1 -t nat -p tcp --dport 443 -j REDIRECT --to-ports 8888 || log "Error enabling MITM port forwarding 443 -> 8888"
  echo -n on > ${ROOT_KEY_MARKER}
}

function ensureMitmOff {
  log "Flushing PREROUTING to disable MITM services"
  iptables -F PREROUTING  -t nat || log "Error flushing port forwarding when disabling MITM services"
  echo -n off > ${ROOT_KEY_MARKER}
}

log "Watching marker file ${BUBBLE_MITM_MARKER} ..."
touch ${BUBBLE_MITM_MARKER}  # first time through, always check and set on/off state
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
