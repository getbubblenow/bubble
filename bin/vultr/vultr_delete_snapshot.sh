#!/bin/bash

SNAPSHOTID=${1:?no snapshot provided}
THISDIR=$(cd $(dirname ${0}) && pwd)
VCURL=${THISDIR}/vcurl

if [[ ${SNAPSHOTID} == "-i" ]] ; then
  SNAPSHOTID=${2:?no snapshot ID provided}
  echo "Deleting snapshot with SNAPSHOTID: ${SNAPSHOTID}"
  ${VCURL} snapshot/destroy -X POST -d "SNAPSHOTID=${SNAPSHOTID}" || echo "Error deleting snapshot: ${SNAPSHOTID}"

else
  SNAPSHOT_NAME="${SNAPSHOTID}"
  echo "Deleting snapshot named: ${SNAPSHOT_NAME}"
  ${0} -i $(${VCURL} snapshot/list | jq -r 'to_entries | .[] | select(.value.description=="'${SNAPSHOT_NAME}'") | .value.SNAPSHOTID') || echo "Error deleting snapshot named: ${SNAPSHOT_NAME}"
fi
