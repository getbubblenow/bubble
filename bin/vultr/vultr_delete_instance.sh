#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
SUBID=${1:?no SUBID provided}
THISDIR=$(cd $(dirname ${0}) && pwd)
VCURL=${THISDIR}/vcurl

echo "Deleting instance: ${SUBID}"
${VCURL} server/destroy -X POST -d "SUBID=${SUBID}" || echo "Error deleting instance: ${SUBID}"
