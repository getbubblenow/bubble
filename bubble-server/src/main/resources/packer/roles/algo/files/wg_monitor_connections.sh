#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
LOG=/tmp/bubble.wg_monitor_connections.log

function die {
  echo 1>&2 "${1}"
  log "${1}"
  exit 1
}

function log {
  echo "$(date): ${1}" >> ${LOG}
}

ALGO_CONFIGS=/root/ansible/roles/algo/algo/configs
BUBBLE_DEVICE_DIR=/home/bubble/wg_devices
if [[ ! -d ${BUBBLE_DEVICE_DIR} ]] ; then
  mkdir -p ${BUBBLE_DEVICE_DIR} && chown -R bubble ${BUBBLE_DEVICE_DIR} && chmod 700 ${BUBBLE_DEVICE_DIR} || die "Error creating ${BUBBLE_DEVICE_DIR}"
fi

while : ; do
  peer=""
  IFS=$'\n'
  for line in $(wg show all) ; do
      if [[ ! -z "${peer}" ]] ; then
        if [[ $(echo "${line}" | tr -d ' ') == allowed* ]] ; then
          for ip in $(echo "${line}" | cut -d: -f2- | tr ',' '\n' | tr -d ' ' | cut -d/ -f1) ; do
            device_uuids="$(find $(find $(find ${ALGO_CONFIGS} -type d -name wireguard) -type d -name public) -type f | xargs grep -l ${peer} | xargs -n 1 basename)"
            if [[ $(echo "${device_uuids}" | wc -l | tr -d ' ') -gt 1 ]] ; then
              log "Multiple device UUIDs found for IP ${ip} (not recording anything): ${device_uuids}"
              continue
            fi
            device="$(echo "${device_uuids}" | head -1 | tr -d ' ')"

            ip_file="${BUBBLE_DEVICE_DIR}/ip_$(echo ${ip})"
            if [[ ! -f ${ip_file} ]] ; then
              touch ${ip_file} && chown bubble ${ip_file} && chmod 400 ${ip_file} || log "Error creating ${ip_file}"
            fi
            device_exists=$(grep -c  "${ip}" ${ip_file})
            if [[ ${device_exists} -eq 0 ]] ; then
              log "recorded device ${device} for IP ${ip}"
              echo "${device}" > ${ip_file} || log "Error writing ${device} to ${ip_file}"
            fi

            device_file="${BUBBLE_DEVICE_DIR}/device_$(echo ${device})"
            if [[ ! -f ${device_file} ]] ; then
              touch ${device_file} && chown bubble ${device_file} && chmod 400 ${device_file} || log "Error creating ${ip_file}"
            fi
            ip_exists=$(grep -c  "${ip}" ${device_file})
            if [[ ${ip_exists} -eq 0 ]] ; then
              log "recorded IP ${ip} for device ${device}"
              echo "${ip}" >> ${device_file} || log "Error writing ${ip} to ${device_file}"
            fi

          done
          peer=""
        fi

      elif [[ ${line} == peer* ]] ; then
        peer="$(echo "${line}" | awk '{print $NF}')"
      fi
  done
  sleep 30s
done
