#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
LABEL=${1:?no label provided}
THISDIR=$(cd $(dirname ${0}) && pwd)
VCURL=${THISDIR}/vcurl

SUBID=$(${THISDIR}/vultr_subid_for_instance.sh ${LABEL})
if [[ -z "${SUBID}" ]] ; then
  echo "No instance found with label ${LABEL}"
  exit 1
fi

echo "Deleting instance: ${SUBID}"
${VCURL} server/destroy -X POST -d "SUBID=${SUBID}" || echo "Error deleting instance: ${SUBID}"
