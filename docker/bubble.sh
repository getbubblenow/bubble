#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Build or run a docker container for a Bubble launcher
#
# -- Building a Docker image:
#
#      bubble.sh build [tag-name]
#
#  tag-name : name of the docker tag to apply, default is bubble/bubble_launcher:X
#
# -- Running a Docker image:
#
#      bubble.sh run [tag-name]
#
#  tag-name : name of the docker tag to use, default is bubble/bubble_launcher:X
#

function die {
  echo 1>&2 "${1}"
  exit 1
}

THISDIR="$(cd "$(dirname "${0}")" && pwd)"
cd "${THISDIR}/.." || die "Directory not found: ${THISDIR}/.."

DEFAULT_TAG="bubble/bubble_launcher:0.2"

MODE=${1:?no mode specified, use build or run}
TAG=${2:-${DEFAULT_TAG}}

if [[ "${MODE}" == "build" ]] ; then
  if [[ $(find bubble-server/target -type f -name "bubble-server-*.jar" | wc -l | tr -d ' ') -eq 0 ]] ; then
    die "No bubble jar found in $(pwd)/bubble-server/target"
  fi
  docker build -t bubble/test:0.1 .

elif [[ "${MODE}" == "run" ]] ; then
  docker run --env-file <(cat "${HOME}/.bubble.env" | sed -e 's/export //' | tr -d '"' | tr -d "'") -p 8090:8090 -t bubble/test:0.1

else
  die "Invalid mode (expected build or run): ${MODE}"
fi

