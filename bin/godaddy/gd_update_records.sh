#!/bin/bash
DOMAIN=${1:?no domain provided}
RECORDS_JSON="${2:?no JSON DNS records file provided}"

THISDIR=$(cd $(dirname ${0}) && pwd)
GDCURL=${THISDIR}/gdcurl

${GDCURL} ${DOMAIN}/records "${RECORDS_JSON}" PUT
