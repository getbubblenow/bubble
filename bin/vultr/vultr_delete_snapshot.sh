#!/bin/bash

SNAPSHOTID=${1:?no SNAPSHOTID provided}
THISDIR=$(cd $(dirname ${0}) && pwd)
VCURL=${THISDIR}/vcurl

if [[ ${SNAPSHOTID} == "-n" ]] ; then
  SNAPSHOT_NAME=${2:?no snapshot name provided}
  echo "Deleting snapshot named: ${SNAPSHOT_NAME}"
  ${0} $(${VCURL} snapshot/list | jq -r 'to_entries | .[] | select(.value.description=="'${SNAPSHOT_NAME}'") | .value.SNAPSHOTID') || echo "Error deleting snapshot named: ${SNAPSHOT_NAME}"

else
  echo "Deleting snapshot: ${SNAPSHOTID}"
  ${VCURL} snapshot/destroy -X POST -d "SNAPSHOTID=${SNAPSHOTID}" || echo "Error deleting snapshot: ${SNAPSHOTID}"
fi
