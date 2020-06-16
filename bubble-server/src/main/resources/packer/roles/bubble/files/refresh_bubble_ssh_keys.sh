#!/bin/bash

LOG=/tmp/bubble.refresh_bubble_ssh_keys.log

function die {
  echo 1>&2 "${1}"
  log "${1}"
  exit 1
}

function log {
  echo "$(date): ${1}" >> ${LOG}
}

CURRENT_KEYS_SQL='
SELECT k.ssh_public_key
FROM account_ssh_key k, account a
WHERE a.uuid = k.account
 AND a.admin = true
 AND k.install_ssh_key = true
 AND (k.expiration is null or k.expiration < 1000*extract(epoch from now()))'

AUTH_KEYS="/root/.ssh/authorized_keys"

NEW_KEYS=$(mktemp /root/.ssh/authorized_keys.XXXXXXX)
chmod 600 ${NEW_KEYS} || die "Error setting permissions on new authorized_keys file: ${NEW_KEYS}"

KEY_COUNT=0
for key in $(echo "${CURRENT_KEYS_SQL}" | PGPASSWORD="$(cat /home/bubble/.BUBBLE_PG_PASSWORD)" psql -U bubble -h 127.0.0.1 bubble -qt) ; do
  if [[ -z "$(echo "${key}" | tr -d [[:space:]])" ]] ; then
    continue
  fi
  KEY="$(bdecrypt "${key}" 2> /dev/null)"
  if [[ ! -z "${KEY}" && "${KEY}" == ssh-rsa* ]] ; then
    log "Adding authorized key: $(echo "${KEY}" | tr -d '\n')"
    echo "${KEY}" >> ${NEW_KEYS}
    KEY_COUNT=$(expr ${KEY_COUNT} + 1)
  else
    log "Warning: NOT adding malformed key: $(echo "${KEY}" | tr -d '\n')"
  fi
done

if [[ ${KEY_COUNT} -eq 0 ]] ; then
  # Sanity check that we can even talk to psql
  # We may be out of memory, in which case we do not want to erase existing installed keys
  if [[ -z "$(echo 'SELECT count(*) FROM account_ssh_key' | PGPASSWORD="$(cat /home/bubble/.BUBBLE_PG_PASSWORD)" psql -U bubble -h 127.0.0.1 bubble)" ]] ; then
    log "Warning: error calling psql, not installing/uninstalling any keys"
    exit 0
  fi
fi

# Retain self-generated ansible setup key
PUB_FILE="$(cd ~root && pwd)/.ssh/bubble_rsa.pub"
if [[ -f "${PUB_FILE}" ]] ; then
  cat "${PUB_FILE}" >> ${NEW_KEYS}
fi

mv ${NEW_KEYS} ${AUTH_KEYS} || die "Error moving ${NEW_KEYS} -> ${AUTH_KEYS}"

log "Installed ${KEY_COUNT} authorized SSH keys: ${NEW_KEYS} -> ${AUTH_KEYS}"
