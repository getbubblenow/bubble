#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
BUBBLE_HOME="/home/bubble"
RESTORE_MARKER="${BUBBLE_HOME}/.restore"
RESTORE_RUN_MARKER="${BUBBLE_HOME}/.restore_run"

SELF_NODE="self_node.json"
BUBBLE_SELF_NODE="${BUBBLE_HOME}/${SELF_NODE}"

ADMIN_PORT=${1:?no admin port provided}
TIMEOUT=${2:-3600}  # 60 minutes default timeout

LOG=/tmp/bubble.restore.log

function die {
  echo 1>&2 "${1}"
  log "${1}"
  exit 1
}

function log {
  echo "$(date): ${1}" >> ${LOG}
}

START=$(date +%s)
while [[ ! -f "${RESTORE_MARKER}" ]] ; do
  sleep 5
  if [[ $(expr $(date +%s) - ${START}) -gt ${TIMEOUT} ]] ; then
      break
  fi
done

if [[ ! -f "${RESTORE_MARKER}" ]] ; then
  die "Restore marker was never created: ${RESTORE_MARKER}"
fi

# was a restore already attempted? only one attempt is allowed. start another restore (with a new node) if you need to try again
if [[ -f ${RESTORE_RUN_MARKER} ]] ; then
  die "Restore was already attempted, cannot attempt again"
fi
touch ${RESTORE_RUN_MARKER}

# Ensure there is only one self_node.json in the backup. Otherwise maybe we have more than once backup, can't restore.
SELF_NODE_COUNT=$(find ${BUBBLE_HOME}/restore -type f -name "${SELF_NODE}" | wc -l | tr -d ' ')
if [[ ${SELF_NODE_COUNT} -eq 0 ]] ; then
  die "Cannot restore, restore base could not be determined (no ${SELF_NODE} found under ${BUBBLE_HOME}/restore)"
elif [[ ${SELF_NODE_COUNT} -gt 1 ]] ; then
  die "Cannot restore, restore base could not be determined (multiple ${SELF_NODE} files found under ${BUBBLE_HOME}/restore): $(find ${BUBBLE_HOME}/restore -type f -name "${SELF_NODE}")"
fi

# set RESTORE_BASE, ensure it is set
RESTORE_BASE=$(dirname $(find ${BUBBLE_HOME}/restore -type f -name "${SELF_NODE}" | head -1))
if [[ -z "${RESTORE_BASE}" ]] ; then
  die "Cannot restore,  restore base could not be determined (no ${SELF_NODE} found under ${BUBBLE_HOME}/restore)"
fi

# stop bubble service
log "Stopping bubble service"
supervisorctl stop bubble

# stop mitmdump service
log "Stopping mitmproxy service"
supervisorctl stop mitmdump

# restore bubble.jar
log "Restoring bubble.jar"
cp ${RESTORE_BASE}/bubble.jar ${BUBBLE_HOME}/current/bubble.jar

# set wasRestored flag in self_node.json
log "Adding wasRestored=true to ${SELF_NODE}"
TEMP_SELF=$(mktemp /tmp/self_node.XXXXXXX.json)
cat ${BUBBLE_SELF_NODE} | jq '.wasRestored = true' > ${TEMP_SELF} || die "Error adding 'wasRestored' flag to ${SELF_NODE}"
cat ${TEMP_SELF} > ${BUBBLE_SELF_NODE} || die "Error rewriting ${SELF_NODE}"

log "Setting ownership of json files to bubble user"
chown bubble ${BUBBLE_HOME}/*.json || die "Error changing ownership of json files to bubble user"

# restore dot files
log "Restoring bubble dotfiles"
cp ${RESTORE_BASE}/dotfiles/.BUBBLE_* ${BUBBLE_HOME}/ || die "Error restoring dotfiles"

# restore mitm configs
log "Restoring mitm certs"
cp -R ${RESTORE_BASE}/mitm_certs ${BUBBLE_HOME}/ || die "Error restoring mitm certs"

# drop and recreate database from backup (but preserve bubble_node and bubble_node_key for current node)
log "Restoring bubble database"
cp ${RESTORE_BASE}/bubble.sql.gz ${BUBBLE_HOME}/sql/ \
  && chown -R bubble ${BUBBLE_HOME}/sql \
  && chgrp -R postgres ${BUBBLE_HOME}/sql \
  && chmod 550 ${BUBBLE_HOME}/sql \
  && chmod 440 ${BUBBLE_HOME}/sql/* || die "Error restoring bubble database archive"
su - postgres bash -c "cd ${BUBBLE_HOME}/sql && full_reset_db.sh drop restored_node" || die "Error restoring database"

# Remove old keys
log "Removing node keys"
echo "DELETE FROM bubble_node_key" | bsql.sh

# restore local storage
log "Restoring bubble LocalStorage"
rm -rf ${BUBBLE_HOME}/.bubble_local_storage/* && rsync -ac ${RESTORE_BASE}/LocalStorage/* ${BUBBLE_HOME}/.bubble_local_storage/ || die "Error restoring LocalStorage"

# flush redis
log "Flushing redis"
echo "FLUSHALL" | redis-cli || die "Error flushing redis"

# restore algo configs
log "Restoring algo configs"
CONFIGS_BACKUP=/home/bubble/.BUBBLE_ALGO_CONFIGS.tgz
if [[ ! -f ${CONFIGS_BACKUP} ]] ; then
  log "Warning: Algo VPN configs backup not found: ${CONFIGS_BACKUP}, not installing algo"
else
  ANSIBLE_HOME="/root"
  ANSIBLE_DIR="${ANSIBLE_HOME}/ansible"
  ID_FILE="${ANSIBLE_HOME}/.ssh/bubble_rsa"
  SSH_OPTIONS="--ssh-extra-args '-o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -o PreferredAuthentications=publickey -i ${ID_FILE}'"

  ALGO_BASE=${ANSIBLE_DIR}/roles/algo/algo
  if [[ ! -d ${ALGO_BASE} ]] ; then
    die "Error restoring Algo VPN: directory ${ALGO_BASE} not found"
  fi
  cd ${ALGO_BASE} && tar xzf ${CONFIGS_BACKUP} || die "Error restoring algo VPN configs"

  cd "${ANSIBLE_DIR}" && \
    . ./venv/bin/activate && \
    bash -c \
      "ansible-playbook ${SSH_OPTIONS} --tags 'algo_related,always' --inventory ./hosts ./playbook.yml 2>&1 >> ${LOG}" \
  || die "Error running ansible in post-restore. journalctl -xe = $(journalctl -xe | tail -n 50)"
fi

# restart mitm proxy service
log "Restarting mitmproxy"
supervisorctl restart mitmdump

# restart bubble service
log "Restore complete: restarting bubble API"
supervisorctl restart bubble

# verify service is running OK
log "Pausing for a bit, then verifying bubble server has successfully restarted after restore"
sleep 60
curl https://$(hostname):${ADMIN_PORT}/api/.bubble || log "Error restarting bubble server - port ${ADMIN_PORT}"

# remove restore markers, we are done
log "Cleaning up temp files"
rm -f ${RESTORE_MARKER} ${RESTORE_RUN_MARKER}
