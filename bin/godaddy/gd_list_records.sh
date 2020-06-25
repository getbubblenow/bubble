#!/bin/bash

DOMAIN=${1:?no domain provided}

THISDIR=$(cd $(dirname ${0}) && pwd)
GDCURL=${THISDIR}/gdcurl

${GDCURL} ${DOMAIN}/records
