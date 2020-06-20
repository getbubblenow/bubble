#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
THISDIR=$(cd $(dirname ${0}) && pwd)
for region in $(${THISDIR}/list_regions.sh) ; do
  cat ${THISDIR}/config.template | sed -e "s/__REGION__/${region}" > ~/.aws/config.${region} && echo "created config for region ${region}"
done
