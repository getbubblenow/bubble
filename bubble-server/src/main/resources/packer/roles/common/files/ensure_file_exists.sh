#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
TARGET_FILE=${1:?no target file provided}
TIMEOUT=${2:?no timeout provided}
LOG=/tmp/ensure_file_$(echo ${TARGET_FILE} | tr '/' '_').log

start=$(date +%s)
while [[ ! -f ${TARGET_FILE} && $(expr $(date +%s) - ${start}) -le ${TIMEOUT} ]] ; do
  echo "$(date): $0: waiting for target file to exist ${TARGET_FILE} (will timeout after ${TIMEOUT} seconds)" | tee -a ${LOG}
  sleep 1s
done

if [[ ! -f ${TARGET_FILE} ]] ; then
  echo "target file did not get created: ${TARGET_FILE} (timeout after ${TIMEOUT} seconds)" | tee -a ${LOG}
  exit 1
fi
echo "target file has been created: ${TARGET_FILE}" | tee -a ${LOG}