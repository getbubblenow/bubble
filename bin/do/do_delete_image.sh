#!/bin/bash

IMAGEID=${1:?no IMAGEID provided}
THISDIR=$(cd $(dirname ${0}) && pwd)
DOCURL=${THISDIR}/docurl

if [[ ${IMAGEID} == "-n" ]] ; then
  IMAGE_NAME=${2:?no image name provided}
  echo "Deleting image named: ${IMAGE_NAME}"
  ${0} $(${DOCURL} "images?private=true" | jq -r '.images[] | select(.name=="'${IMAGE_NAME}'") | .id') || echo "Error deleting image named: ${IMAGE_NAME}"

else
  echo "Deleting image: ${IMAGEID}"
  ${DOCURL} images/${IMAGEID} -X DELETE || echo "Error deleting image: ${IMAGEID}"
fi
