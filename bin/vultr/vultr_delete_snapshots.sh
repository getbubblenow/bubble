#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
THISDIR=$(cd $(dirname ${0}) && pwd)
VCURL=${THISDIR}/vcurl

SNAPSHOT_FILTER=${1?no snapshot filter provided}

for snap in $(${VCURL} snapshot/list | jq -r '.[].description' | grep "${SNAPSHOT_FILTER}") ; do
  ${THISDIR}/vultr_delete_snapshot.sh "${snap}"
done
