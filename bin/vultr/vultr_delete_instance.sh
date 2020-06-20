#!/bin/bash

SUBID=${1:?no SUBID provided}
THISDIR=$(cd $(dirname ${0}) && pwd)
VCURL=${THISDIR}/vcurl

echo "Deleting instance: ${SUBID}"
${VCURL} server/destroy -X POST -d "SUBID=${SUBID}" || echo "Error deleting instance: ${SUBID}"
