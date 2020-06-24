#!/bin/bash

THISDIR=$(cd $(dirname ${0}) && pwd)
VCURL=${THISDIR}/vcurl

SNAPSHOT_FILTER=${1?no snapshot filter provided}

for snap in $(${VCURL} snapshot/list | jq -r '.[].description' | grep "${SNAPSHOT_FILTER}") ; do
  ${THISDIR}/vultr_delete_snapshot.sh "${snap}"
done
