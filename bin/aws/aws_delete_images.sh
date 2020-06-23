#!/bin/bash

function die {
  echo 1>&2 "${1}"
  exit 1
}

IMAGE_FILTER=${1:-'packer_bubble_*'}
THISDIR=$(cd $(dirname ${0}) && pwd)

for region in $(${THISDIR}/aws_list_regions.sh) ; do
  ${THISDIR}/aws_set_region.sh ${region} || die "Error setting aws region ${region}"
  echo 1>&2 "Deleting images matching: ${IMAGE_FILTER} in region ${region}"
  IMAGE_IDS=$(aws ec2 describe-images --filters "Name=name,Values=${IMAGE_FILTER}" | jq -r '.Images[].ImageId')
  if [[ -z "${IMAGE_IDS}" ]] ; then
    echo 1>&2 "No images matching ${IMAGE_FILTER} found in region ${region}"
    continue
  fi
  for image in ${IMAGE_IDS} ; do
    echo 1>&2 "Deleting image: ${image} in region ${region}"
    aws ec2 deregister-image --image-id ${image} || echo 1>&2 "Error deleting image ${image} in aws region ${region}"
  done
done
