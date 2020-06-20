#!/bin/bash

THISDIR=$(cd $(dirname ${0}) && pwd)
VCURL=${THISDIR}/vcurl

VULTR_OUTPUT=${1}

if [[ -z "${VULTR_OUTPUT}" ]] ; then
  ${VCURL} snapshot/list | jq .
else
  ${VCURL} snapshot/list | jq -r .[].${VULTR_OUTPUT}
fi
