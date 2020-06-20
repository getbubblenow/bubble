#!/bin/bash

DROPLETID=${1:?no DROPLETID provided}
THISDIR=$(cd $(dirname ${0}) && pwd)
DOCURL=${THISDIR}/docurl

if [[ ${DROPLETID} == "-n" ]] ; then
  DROPLET_NAME=${2:?no droplet name provided}
  echo "Deleting droplet named: ${DROPLET_NAME}"
  ${0} $(${DOCURL} droplets | jq -r '.droplets[] | select(.name=="'${DROPLET_NAME}'") | .id') || echo "Error deleting droplet named: ${DROPLET_NAME}"

else
  echo "Deleting instance: ${DROPLETID}"
  ${DOCURL} droplets/${DROPLETID} -X DELETE || echo "Error deleting instance: ${DROPLETID}"
fi
