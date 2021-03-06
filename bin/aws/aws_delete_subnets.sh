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
  echo "Deleting subnets in region ${region}"
  ${THISDIR}/aws_set_region.sh ${region} || die "Error setting aws region ${region}"
  for subnet in $(aws ec2 describe-subnets --filters "Name=default-for-az,Values=false" | grep SubnetId | cut -d\" -f4) ; do
    echo "Deleting subnet ${subnet} in region ${region}"
    aws ec2 delete-subnet --subnet-id ${subnet} || echo "WARNING: Error deleting subnet ${subnet} in region ${region}"
  done
done
