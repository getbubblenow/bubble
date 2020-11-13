#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Build, run or push a docker container for a Bubble launcher. Intended for developer use.
#
#      bubble.sh [mode] [version]
#
#  mode : build, run or push
#      build - build the docker image
#      run   - run the docker image
#      push  - push to docker hub
#
#  version : version to use, default is read from bubble-server/src/main/resources/META-INF/bubble/bubble.properties
#
# The docker tag used will be getbubble/launcher:version
#

function die {
  echo 1>&2 "${1}"
  exit 1
}

THISDIR="$(cd "$(dirname "${0}")" && pwd)"
BUBBLE_DIR="$(cd "${THISDIR}/.." && pwd)"

MODE=${1:?no mode specified, use build or run}

META_FILE="${BUBBLE_DIR}/bubble-server/src/main/resources/META-INF/bubble/bubble.properties"
VERSION="${2:-$(cat ${META_FILE} | grep bubble.version | awk -F '=' '{print $2}' | awk -F ' ' '{print $NF}' | awk '{$1=$1};1')}"
if [[ -z "${VERSION}" ]] ; then
  die "Error determining version from: ${META_FILE}"
fi
BUBBLE_TAG="getbubble/launcher:${VERSION}"

if [[ "${MODE}" == "build" ]] ; then
  if [[ $(find bubble-server/target -type f -name "bubble-server-*.jar" | wc -l | tr -d ' ') -eq 0 ]] ; then
    die "No bubble jar found in $(pwd)/bubble-server/target"
  fi
  docker build -t ${BUBBLE_TAG} . || die "Error building docker image"

elif [[ "${MODE}" == "run" ]] ; then
  docker run --env-file <(cat "${HOME}/.bubble.env" | sed -e 's/export //' | tr -d '"' | tr -d "'") -p 8090:8090 -t ${BUBBLE_TAG} || die "Error running docker container"

elif [[ "${MODE}" == "push" ]] ; then
  docker push ${BUBBLE_TAG} || die "Error pushing docker image"

else
  die "Invalid mode (expected build or run): ${MODE}"
fi
