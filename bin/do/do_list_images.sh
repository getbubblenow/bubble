#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
THISDIR=$(cd $(dirname ${0}) && pwd)
DOCURL=${THISDIR}/docurl

DO_OUTPUT=${1}

if [[ -z "${DO_OUTPUT}" ]] ; then
  ${DOCURL} "images?private=true" | jq .
else
  ${DOCURL} "images?private=true" | jq -r .images[].${DO_OUTPUT}
fi
