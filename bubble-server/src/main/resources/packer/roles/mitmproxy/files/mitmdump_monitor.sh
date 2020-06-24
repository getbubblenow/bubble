#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
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
MITMDUMP_PID_FILE=/home/mitmproxy/mitmdump.pid
MAX_MITM_PCT_MEM=18

# Start with MITM proxy turned on, or refresh value
if [[ ! -f ${BUBBLE_MITM_MARKER} ]] ; then
  echo -n on > ${BUBBLE_MITM_MARKER} && chown bubble ${BUBBLE_MITM_MARKER} || log "Error writing 'on' to ${ROOT_KEY_MARKER}"
else
  touch ${BUBBLE_MITM_MARKER}
fi
if [[ ! -f ${ROOT_KEY_MARKER} ]] ; then
  sleep 1s
  mkdir -p "$(dirname ${ROOT_KEY_MARKER})" && chmod 755 "$(dirname ${ROOT_KEY_MARKER})" || log "Error creating or setting permissions on ${ROOT_KEY_MARKER}"
  echo -n on > ${ROOT_KEY_MARKER} && touch ${ROOT_KEY_MARKER} && chmod 644 ${ROOT_KEY_MARKER} || log "Error writing 'on' to ${ROOT_KEY_MARKER}"
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
  echo -n off > ${ROOT_KEY_MARKER} || log "Error writing 'off' to ${ROOT_KEY_MARKER}"
}

log "Watching marker file ${BUBBLE_MITM_MARKER} ..."
sleep 2s && touch ${BUBBLE_MITM_MARKER} || log "Error touching ${BUBBLE_MITM_MARKER}" # first time through, always check and set on/off state
while : ; do
  if [[ $(stat -c %Y ${BUBBLE_MITM_MARKER}) -gt $(stat -c %Y ${ROOT_KEY_MARKER}) ]] ; then
    if [[ "$(cat ${BUBBLE_MITM_MARKER} | tr -d [[:space:]])" == "on" ]] ; then
      ensureMitmOn
    elif [[ "$(cat ${BUBBLE_MITM_MARKER} | tr -d [[:space:]])" == "off" ]] ; then
      ensureMitmOff
    else
      log "Error: marker file ${BUBBLE_MITM_MARKER} contained invalid value: $(cat ${BUBBLE_MITM_MARKER} | head -c 5)"
    fi
  fi

  # Check process memory usage, restart mitmdump if memory goes above max % allowed
  if [[ -f ${MITMDUMP_PID_FILE} && -s ${MITMDUMP_PID_FILE} ]] ; then
    MITM_PID="$(cat ${MITMDUMP_PID_FILE})"
    PCT_MEM="$(ps q ${MITM_PID} -o %mem --no-headers | tr -d [[:space:]] | cut -f1 -d. | sed 's/[^0-9]*//g')"
    # log "Info: mitmdump pid ${MITM_PID} using ${PCT_MEM}% of memory"
    if [[ ! -z "${PCT_MEM}" ]] ; then
      if [[ ${PCT_MEM} -ge ${MAX_MITM_PCT_MEM} ]] ; then
        log "Warn: mitmdump: pid=$(cat ${MITMDUMP_PID_FILE}) memory used > max, restarting: ${PCT_MEM}% >= ${MAX_MITM_PCT_MEM}%"
        supervisorctl restart mitmdump
      fi
    else
      log "Error: could not determine mitmdump % memory, maybe PID file ${MITMDUMP_PID_FILE} is out of date? pid found was ${MITM_PID}"
    fi
  else
    log "Error: mitmdump PID file ${MITMDUMP_PID_FILE} not found or empty"
  fi
  sleep 5s
done
