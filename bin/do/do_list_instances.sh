#!/bin/bash

THISDIR=$(cd $(dirname ${0}) && pwd)
DOCURL=${THISDIR}/docurl

DO_OUTPUT=${1}

if [[ -z "${DO_OUTPUT}" ]] ; then
  ${DOCURL} droplets | jq .
else
  ${DOCURL} droplets | jq -r .droplets[].${DO_OUTPUT}
fi
