#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
DOMAIN=${1:?no domain provided}

THISDIR=$(cd $(dirname ${0}) && pwd)
GDCURL=${THISDIR}/gdcurl

${GDCURL} ${DOMAIN}/records
