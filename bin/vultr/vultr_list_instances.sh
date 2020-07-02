#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
THISDIR=$(cd $(dirname ${0}) && pwd)
VCURL=${THISDIR}/vcurl

VULTR_OUTPUT=${1}

if [[ -z "${VULTR_OUTPUT}" ]] ; then
  ${VCURL} server/list | jq .
else
  ${VCURL} server/list | jq -r .[].${VULTR_OUTPUT}
fi
