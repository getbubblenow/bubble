#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
#

SCRIPT="${0}"
SCRIPT_DIR=$(cd $(dirname ${SCRIPT}) && pwd)

LOG=/tmp/$(basename ${0}).log

function die {
  echo 1>&2 "${1}"
  log "${1}"
  exit 1
}

function log {
  echo "${1}" | tee -a ${LOG}
}

if [[ $(whoami) != "bubble" ]] ; then
  if [[ $(whoami) == "root" ]] ; then
    sudo -H -u bubble ${0}
    exit $?
  fi
  die "${0} must be run as bubble"
fi

if [[ -z "${LOCALSTORAGE_BASE_DIR}" ]] ; then
  if [[ -f "${HOME}/bubble/current/bubble.env" ]] ; then
      LOCALSTORAGE_BASE_DIR=$(cat "${HOME}/bubble/current/bubble.env" | grep -v '^#' | grep LOCALSTORAGE_BASE_DIR | awk -F '=' '{print $2}' | tr -d ' ')
  fi
fi
if [[ -z "${LOCALSTORAGE_BASE_DIR}" ]] ; then
  log "LOCALSTORAGE_BASE_DIR env var not defined, using ${HOME}/.bubble_local_storage"
  LOCALSTORAGE_BASE_DIR="${HOME}/.bubble_local_storage"
fi

if [[ -z "${BUBBLE_JAR}" ]] ; then
  if [[ -f "${HOME}/current/bubble.jar" ]] ; then
    BUBBLE_JAR="${HOME}/current/bubble.jar"
  fi
fi
if [[ -z "${BUBBLE_JAR}" ]] ; then
  die "BUBBLE_JAR env var not set and no jar file found"
fi

ROLE_DIR="${HOME}/role_tgz"
if [[ ! -d "${ROLE_DIR}" ]] ; then
  die "role_tgz dir not found: ${ROLE_DIR}"
fi

NETWORK_UUID="$(cat ${HOME}/self_node.json | jq -r .network)"
find ${ROLE_DIR} -type f -name "*.tgz" | while read role_tgz ; do
   path="automation/roles/$(basename ${role_tgz})"
   dest="${LOCALSTORAGE_BASE_DIR}/${NETWORK_UUID}/${path}"
   if [[ ! -f ${dest} ]] ; then
     mkdir -p $(dirname ${dest}) || die "Error creating destination directory"
     cp ${role_tgz} ${dest} || die "Error copying role archive"
     log "installed role ${role_tgz} -> ${dest}"
   else
     log "role already installed ${role_tgz} -> ${dest}"
   fi
done
