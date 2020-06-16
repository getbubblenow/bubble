#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
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

if [[ $(whoami) != "root" ]] ; then
  die "${0} must be run as root"
fi

ANSIBLE_USER_HOME=$(cd ~root && pwd)
ANSIBLE_SNAPSHOT="/home/bubble/ansible.tgz"

cd ${ANSIBLE_USER_HOME} \
  && tar czf ${ANSIBLE_SNAPSHOT} ./ansible \
  && chmod 400 ${ANSIBLE_SNAPSHOT} \
  && chown bubble ${ANSIBLE_SNAPSHOT} \
  || die "Error creating ansible snapshot"
