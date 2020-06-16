#!/bin/bash

function die {
  echo 1>&2 "${1}"
  exit 1
}

THISDIR=$(cd $(dirname ${0}) && pwd)
for region in $(${THISDIR}/list_regions.sh) ; do
  echo "Deleting subnets in region ${region}"
  ${THISDIR}/set_aws_region.sh ${region} || die "Error setting aws region ${region}"
  for subnet in $(aws ec2 describe-subnets --filters "Name=default-for-az,Values=false" | grep SubnetId | cut -d\" -f4) ; do
    echo "Deleting subnet ${subnet} in region ${region}"
    aws ec2 delete-subnet --subnet-id ${subnet} || echo "WARNING: Error deleting subnet ${subnet} in region ${region}"
  done
done
