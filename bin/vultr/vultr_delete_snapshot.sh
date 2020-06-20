#!/bin/bash

SNAPSHOTID=${1:?no SNAPSHOTID provided}
THISDIR=$(cd $(dirname ${0}) && pwd)
VCURL=${THISDIR}/vcurl

echo "Deleting snapshot: ${SNAPSHOTID}"
${VCURL} snapshot/destroy -X POST -d "SNAPSHOTID=${SNAPSHOTID}" || echo "Error deleting snapshot: ${SNAPSHOTID}"
