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

CURRENT_KEYS_SQL="
SELECT k.ssh_public_key
FROM account_ssh_key k, account a
WHERE a.uuid = k.account
 AND a.admin = true
 AND k.install_ssh_key = true
 AND (k.expiration is null or k.expiration < 1000*extract(epoch from now()))"

AUTH_KEYS="/root/.ssh/authorized_keys"

NEW_KEYS=$(mktemp /root/.ssh/authorized_keys.XXXXXXX)
chmod 600 ${NEW_KEYS} || die "Error setting permissions on new authorized_keys file: ${NEW_KEYS}"

for key in $(echo "${CURRENT_KEYS_SQL}" | PGPASSWORD="$(cat /home/bubble/.BUBBLE_PG_PASSWORD)" psql -U bubble -h 127.0.0.1 bubble -qt) ; do
  if [[ -z "$(echo "${key}" | tr -d [[:space:]])" ]] ; then
    continue
  fi
  KEY="$(bdecrypt "${key}" 2> /dev/null)"
  if [[ ! -z "${KEY}" && "${KEY}" == ssh-rsa* ]] ; then
    echo "${KEY}" >> ${NEW_KEYS}
  fi
done

mv ${NEW_KEYS} ${AUTH_KEYS} || die "Error moving ${NEW_KEYS} -> ${AUTH_KEYS}"

log "Installed new SSH keys ${NEW_KEYS} -> ${AUTH_KEYS}"
