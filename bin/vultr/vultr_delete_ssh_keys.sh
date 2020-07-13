#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
THISDIR=$(cd $(dirname ${0}) && pwd)
VCURL=${THISDIR}/vcurl
for id in $(${VCURL} sshkey/list | jq -r '.[] | select(.name != "chipper / pipper") | .SSHKEYID') ; do
  echo "Deleting SSH key: ${id}"
  ${VCURL} sshkey/destroy -X POST --data 'SSHKEYID='"${id}"'' || echo "Error deleting SSH key: ${id}"
done
