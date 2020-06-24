#!/bin/bash

THISDIR=$(cd $(dirname ${0}) && pwd)
VCURL=${THISDIR}/vcurl

SNAPSHOT_FILTER=${1}

if [[ -z "${SNAPSHOT_FILTER}" ]] ; then
  ${VCURL} snapshot/list | jq .
else
  ${VCURL} snapshot/list | jq -r '.[].description' | grep "${SNAPSHOT_FILTER}"
fi
