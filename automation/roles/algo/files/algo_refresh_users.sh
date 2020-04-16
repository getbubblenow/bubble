#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
LOG=/tmp/bubble.algo_refresh_users.log

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

log "Regenerating algo config..."
java -cp /home/bubble/current/bubble.jar bubble.main.BubbleMain generate-algo-conf --algo-config ${ALGO_BASE}/config.cfg.hbs || die "Error writing algo config.cfg"

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

log "VPN users successfully sync'd to bubble"
