#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
LOG=/var/log/bubble/algo_refresh_users.log

ALGO_BASE=/root/ansible/roles/algo/algo
REFRESH_MARKER=${ALGO_BASE}/.refreshing_users

function die {
  rm -f ${REFRESH_MARKER}
  echo 1>&2 "${1}"
  log "${1}"
  exit 1
}

function log {
  echo "$(date): ${1}" >> ${LOG}
}

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

START_TIME=$(date +%s)
REFRESH_TIMEOUT=300
OK_TO_REFRESH=0
if [[ -f ${REFRESH_MARKER} ]] ; then
  log "Refresh marker exists: ${REFRESH_MARKER}, waiting for previous refresh run to finish"
  while [[ $(expr $(date +%s) - ${START_TIME}) -lt ${REFRESH_TIMEOUT} ]] ; do
    if [[ ! -f ${REFRESH_MARKER} ]] ; then
      OK_TO_REFRESH=1
      break
    fi
    sleep 1s
  done
  if [[ ${OK_TO_REFRESH} -eq 0 ]] ; then
    log "Timeout waiting for previous refresh, continuing anyway"
  fi
  touch ${REFRESH_MARKER}
fi

ALGO_CONFIG="${ALGO_BASE}/config.cfg"
ALGO_CONFIG_SHA="$(sha256sum ${ALGO_CONFIG} | cut -f1 -d' ')"

log "Regenerating algo config..."
java -cp /home/bubble/api/bubble.jar bubble.main.BubbleMain generate-algo-conf --algo-config ${ALGO_CONFIG}.hbs || die "Error writing algo config.cfg"
NEW_ALGO_CONFIG_SHA="$(sha256sum ${ALGO_CONFIG} | cut -f1 -d' ')"

if [[ ! -z "${ALGO_CONFIG_SHA}" && "${ALGO_CONFIG_SHA}" == "${NEW_ALGO_CONFIG_SHA}" ]] ; then
  log "Algo configuration is unchanged, not refreshing: ${ALGO_CONFIG}"

else
  log "Updating algo VPN users..."
  cd ${ALGO_BASE} && \
  python3 -m virtualenv --python="$(command -v python3)" .env \
    && source .env/bin/activate \
    && python3 -m pip install -U pip virtualenv \
    && python3 -m pip install -r requirements.txt \
    && ansible-playbook users.yml --tags update-users --skip-tags debug \
      -e "ca_password=$(cat ${CA_PASS_FILE})
          provider=local
          server=localhost
          store_cakey=true
          ondemand_cellular=false
          ondemand_wifi=false
          store_pki=true
          dns_adblocking=false
          ssh_tunneling=false
          endpoint={{ endpoint }}
          server_name={{ server_name }}" 2>&1 | tee -a ${LOG} || die "Error running algo users.yml"

  # Archive configs in a place that the BackupService can pick them up
  log "Sync'ing algo VPN users to bubble..."
  CONFIGS_BACKUP=/home/bubble/.BUBBLE_ALGO_CONFIGS.tgz
  cd ${ALGO_BASE} && tar czf ${CONFIGS_BACKUP} configs && chgrp bubble ${CONFIGS_BACKUP} && chmod 660 ${CONFIGS_BACKUP} || die "Error backing up algo configs"
  cd /home/bubble && rm -rf configs/* && tar xzf ${CONFIGS_BACKUP} && chgrp -R bubble configs && chown -R bubble configs && chmod 500 configs || die "Error unpacking algo configs to bubble home"

  log "VPN users successfully sync'd to bubble. Refresh completed in $(expr $(date +%s) - ${START_TIME}) seconds"
fi

rm -f ${REFRESH_MARKER}