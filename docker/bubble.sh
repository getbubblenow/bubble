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
# If you want to change the "getbubble/launcher" part of the Docker tag, set the BUBBLE_DOCKER_REPO
# environment variable in your shell environment.
#
# When using the 'run' mode, you'll be asked for an email address to associate with any LetsEncrypt
# certificates that will be created.
#
# If you want to run this unattended, set the LETSENCRYPT_EMAIL environment variable
# in your ~/.bubble.env file or in your shell environment.
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
DOCKER_REPO=${BUBBLE_DOCKER_REPO?getbubble/launcher}
BUBBLE_TAG="${DOCKER_REPO}:${VERSION}"

BUBBLE_ENV="${HOME}/.bubble.env"

if [[ "${MODE}" == "build" ]] ; then
  if [[ $(find bubble-server/target -type f -name "bubble-server-*.jar" | wc -l | tr -d ' ') -eq 0 ]] ; then
    die "No bubble jar found in $(pwd)/bubble-server/target"
  fi
  docker build -t ${BUBBLE_TAG} . || die "Error building docker image"

elif [[ "${MODE}" == "run" ]] ; then
  if [[ $(cat "${BUBBLE_ENV}" | grep -v '^#' | grep -c LETSENCRYPT_EMAIL) -eq 0 ]] ; then
    if [[ -z "${LETSENCRYPT_EMAIL}" ]] ; then
      echo ; echo -n "Email address for LetsEncrypt certificates: "
      read -r LETSENCRYPT_EMAIL
    fi
    echo "
export LETSENCRYPT_EMAIL=${LETSENCRYPT_EMAIL}
" >> "${BUBBLE_ENV}"
  fi
  docker run --env-file <(cat "${BUBBLE_ENV}" | sed -e 's/export //' | tr -d '"' | tr -d "'") -p 8090:8090 -t ${BUBBLE_TAG} || die "Error running docker container"

elif [[ "${MODE}" == "push" ]] ; then
  docker push ${BUBBLE_TAG} || die "Error pushing docker image"

else
  die "Invalid mode (expected build or run): ${MODE}"
fi
