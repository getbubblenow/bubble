#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Build, run or push a docker container for a Bubble launcher
#
# -- Building a Docker image:
#
#      bubble.sh build [tag-name]
#
#  tag-name : name of the docker tag to apply, default is getbubble/launcher:VERSION
#
# -- Running a Docker image:
#
#      bubble.sh run [tag-name]
#
#  tag-name : name of the docker tag to use, default is getbubble/launcher:VERSION
#
# -- Pushing a Docker image:
#
#      bubble.sh push [tag-name]
#
#  tag-name : name of the docker tag to push, default is getbubble/launcher:VERSION
#

function die {
  echo 1>&2 "${1}"
  exit 1
}

THISDIR="$(cd "$(dirname "${0}")" && pwd)"
cd "${THISDIR}/.." || die "Directory not found: ${THISDIR}/.."

DEFAULT_TAG="getbubble/launcher:0.3"

MODE=${1:?no mode specified, use build or run}
TAG=${2:-${DEFAULT_TAG}}

if [[ "${MODE}" == "build" ]] ; then
  if [[ $(find bubble-server/target -type f -name "bubble-server-*.jar" | wc -l | tr -d ' ') -eq 0 ]] ; then
    die "No bubble jar found in $(pwd)/bubble-server/target"
  fi
  docker build -t ${TAG} . || die "Error building docker image"

elif [[ "${MODE}" == "run" ]] ; then
  docker run --env-file <(cat "${HOME}/.bubble.env" | sed -e 's/export //' | tr -d '"' | tr -d "'") -p 8090:8090 -t ${TAG} || die "Error running docker container"

elif [[ "${MODE}" == "push" ]] ; then
  docker push ${TAG} || die "Error pushing docker image"

else
  die "Invalid mode (expected build or run): ${MODE}"
fi
