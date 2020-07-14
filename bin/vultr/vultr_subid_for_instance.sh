#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
THISDIR=$(cd $(dirname ${0}) && pwd)
VCURL=${THISDIR}/vcurl
LABEL=${1?no label provided}

${VCURL} server/list?label=${LABEL} | jq -r '.[].SUBID'
