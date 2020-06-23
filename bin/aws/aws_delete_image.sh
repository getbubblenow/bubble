#!/bin/bash

function die {
  echo 1>&2 "${1}"
  exit 1
}

IMAGEID=${1:?no IMAGEID provided}
THISDIR=$(cd $(dirname ${0}) && pwd)

for region in $(${THISDIR}/aws_list_regions.sh) ; do
  ${THISDIR}/aws_set_region.sh ${region} || die "Error setting aws region ${region}"
  if [[ ${1} == "-n" ]] ; then
    IMAGE_NAME=${2:?no image name provided}
    echo 1>&2 "Deleting image named: ${IMAGE_NAME} in region ${region}"
    IMAGEID=$(aws ec2 describe-images --filters "Name=name,Values=${IMAGE_NAME}" | jq -r '.Images[].ImageId')
    if [[ -z "${IMAGEID}" ]] ; then
      echo 1>&2 "No images with name=${IMAGE_NAME} found in region ${region}"
      continue
    fi
  fi
  echo 1>&2 "Deleting image ${IMAGEID} in region ${region}"
  for image in ${IMAGEID} ; do
    aws ec2 deregister-image --image-id ${image} || echo 1>&2 "Error deleting image ${image} in aws region ${region}"
  done
done
