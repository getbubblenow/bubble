#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
LOG=/var/log/bubble/mitm_monitor.log

function die {
  echo 1>&2 "${1}"
  log "${1}"
  exit 1
}

function log {
  echo "$(date): ${1}" >> ${LOG}
}

BUBBLE_MITM_MARKER=/home/bubble/.mitm_monitor
ROOT_KEY_MARKER=/usr/share/bubble/mitm_monitor
MITM_PORT_FILE=/home/mitmproxy/mitmproxy_port

TOTAL_MEM=$(free | grep -m 1 Mem: | awk '{print $2}')

# For 1GB system, MIN_PCT_FREE is 2%
# For 2GB system, MIN_PCT_FREE is 5%
# For 4GB system, MIN_PCT_FREE is 11%
MIN_PCT_FREE=$(expr $(expr $(expr ${TOTAL_MEM} / 500) \* 14) / 10000)

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
  PORT=${1}
  log "Flushing PREROUTING before enabling MITM services"
  iptables -F PREROUTING  -t nat || log "Error flushing port forwarding when enabling MITM services"
  log "Enabling MITM port forwarding on TCP port 80 -> ${PORT}"
  iptables -I PREROUTING 1 -t nat -p tcp --dport 80 -j REDIRECT --to-ports ${PORT} || log "Error enabling MITM port forwarding 80 -> 8888"
  log "Enabling MITM port forwarding on TCP port 443 -> ${PORT}"
  iptables -I PREROUTING 1 -t nat -p tcp --dport 443 -j REDIRECT --to-ports ${PORT} || log "Error enabling MITM port forwarding 443 -> 8888"
  echo -n on > ${ROOT_KEY_MARKER}
}

function ensureMitmOff {
  log "Flushing PREROUTING to disable MITM services"
  iptables -F PREROUTING  -t nat || log "Error flushing port forwarding when disabling MITM services"
  echo -n off > ${ROOT_KEY_MARKER} || log "Error writing 'off' to ${ROOT_KEY_MARKER}"
}

function fullMitmReset {
  log "Full mitm reset starting"
  ensureMitmOn 8888
  echo 8888 > ${MITM_PORT_FILE}
  supervisorctl restart mitm8888
  supervisorctl restart mitm9999
  log "Full mitm reset completed"
}

log "Watching marker file ${BUBBLE_MITM_MARKER} MIN_PCT_FREE=${MIN_PCT_FREE}%"
sleep 2s && touch ${BUBBLE_MITM_MARKER} || log "Error touching ${BUBBLE_MITM_MARKER}" # first time through, always check and set on/off state
while : ; do
  if [[ $(stat -c %Y ${BUBBLE_MITM_MARKER}) -gt $(stat -c %Y ${ROOT_KEY_MARKER}) ]] ; then
    if [[ "$(cat ${BUBBLE_MITM_MARKER} | tr -d [[:space:]])" == "on" ]] ; then
      if [[ ! -f "${MITM_PORT_FILE}" ]] ; then
        log "Error: port file does not exist: ${MITM_PORT_FILE}"
      else
        MITM_PORT="$(cat ${MITM_PORT_FILE})"
        if [[ -z "${MITM_PORT}" ]] ; then
          log "Error: port file was empty: ${MITM_PORT_FILE}"
        else
            ensureMitmOn ${MITM_PORT}
        fi
      fi
    elif [[ "$(cat ${BUBBLE_MITM_MARKER} | tr -d [[:space:]])" == "off" ]] ; then
      ensureMitmOff
    else
      log "Error: marker file ${BUBBLE_MITM_MARKER} contained invalid value: $(cat ${BUBBLE_MITM_MARKER} | head -c 5)"
    fi
  fi

  # Check process memory usage, restart mitm if memory goes above max % allowed
  if [[ ! -f "${MITM_PORT_FILE}" ]] ; then
    log "Warn: No mitm port found in file: ${MITM_PORT_FILE}, resetting mitm"
    fullMitmReset
  else
    MITM_PORT="$(cat ${MITM_PORT_FILE})"
    if [[ -z "${MITM_PORT}" ]] ; then
      log "Warn: No mitm port found in file: ${MITM_PORT_FILE} (resetting mitm)"
      fullMitmReset
    else
      MITM_PID=$(netstat -nlpt4 | grep :${MITM_PORT} | awk '{print $7}' | cut -d/ -f1)
      if [[ -z "${MITM_PID}" ]] ; then
        log "Warn: No mitm PID found listening on ${MITM_PORT} via netstat, may be starting up"
      else
        PCT_FREE=$(expr $(free | grep -m 1 Mem: | awk '{print $7"00 / "$2}'))
        PCT_MEM="$(ps q ${MITM_PID} -o %mem --no-headers | tr -d [[:space:]] | cut -f1 -d. | sed 's/[^0-9]*//g')"
        # log "Info: mitm pid ${MITM_PID} using ${PCT_MEM}% of memory"
        if [[ -z "${PCT_MEM}" ]] ; then
          log "Error: could not determine mitm % memory. pid was ${MITM_PID}"
        else
          if [[ ${PCT_FREE} -lt ${MIN_PCT_FREE} ]] ; then
            log "Warn: switching mitm port: ${PCT_FREE}% free < ${MIN_PCT_FREE}% min. mitm${MITM_PORT} using ${PCT_MEM}%"
            if [[ "${MITM_PORT}" == "8888" ]] ; then
              ensureMitmOn 9999
              echo 9999 > ${MITM_PORT_FILE}
              supervisorctl restart mitm8888
            else
              ensureMitmOn 8888
              echo 8888 > ${MITM_PORT_FILE}
              supervisorctl restart mitm9999
            fi
          fi
        fi
      fi
    fi
  fi
  sleep 5s
done
