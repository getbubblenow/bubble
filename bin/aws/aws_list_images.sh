#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
function die {
  echo 1>&2 "${1}"
  exit 1
}

THISDIR=$(cd $(dirname ${0}) && pwd)
for region in $(${THISDIR}/aws_list_regions.sh) ; do
  echo 1>&2 "Listing images in region ${region}"
  ${THISDIR}/aws_set_region.sh ${region} || die "Error setting aws region ${region}"
  aws ec2 describe-images --filters "Name=name,Values=packer_bubble_*"
done
