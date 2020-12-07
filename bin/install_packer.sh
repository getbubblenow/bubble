#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Installs Packer and the Packer Vultr plugin.
#
# It is safe to run this command multiple times, it is idempotent.
#
function die {
  echo 1>&2 "${1}"
  exit 1
}

PLATFORM="$(uname -s)"

CURL=""
if [[ -z "$(which curl)" ]] ; then
  if [[ -f /usr/bin/curl ]] ; then
    CURL=/usr/bin/curl
  fi
else
  CURL="$(which curl)"
fi

# Install packer
if [[ ! -f ${HOME}/packer/packer ]] ; then
  PACKER_VERSION=1.6.5
  if [[ "${PLATFORM}" == "Darwin" ]] ; then
    PACKER_FILE=packer_${PACKER_VERSION}_darwin_amd64.zip
  elif [[ "${PLATFORM}" == "Linux" ]] ; then
    PACKER_FILE=packer_${PACKER_VERSION}_linux_amd64.zip
  else
    die "Add packer support to script ${0} for platform ${PLATFORM}"
  fi
  PACKER_URL=https://releases.hashicorp.com/packer/${PACKER_VERSION}/${PACKER_FILE}
  mkdir -p ${HOME}/packer && cd ${HOME}/packer && "${CURL}" -L ${PACKER_URL} -o ${PACKER_FILE} && unzip ${PACKER_FILE} || die "Error installing packer"
  echo "Packer successfully installed"
else
  echo "Packer already installed"
fi

# Install packer Vultr plugin
if [[ ! -f ${HOME}/.packer.d/plugins/packer-builder-vultr ]] ; then
  PACKER_VULTR_VERSION=1.0.15
  if [[ "${PLATFORM}" == "Darwin" ]] ; then
    PACKER_VULTR_FILE=packer-builder-vultr_${PACKER_VULTR_VERSION}_macOs_64-bit.tar.gz
  elif [[ "${PLATFORM}" == "Linux" ]] ; then
    PACKER_VULTR_FILE=packer-builder-vultr_${PACKER_VULTR_VERSION}_linux_64-bit.tar.gz
  else
    die "Add packer vultr support to script ${0} for platform ${PLATFORM}"
  fi
  PACKER_VULTR_URL=https://github.com/vultr/packer-builder-vultr/releases/download/v${PACKER_VULTR_VERSION}/${PACKER_VULTR_FILE}
  mkdir -p ${HOME}/.packer.d/plugins && cd ${HOME}/.packer.d/plugins && "${CURL}" -L ${PACKER_VULTR_URL} -o ${PACKER_VULTR_FILE} && tar xzf ${PACKER_VULTR_FILE}  || die "Error installing packer vultr plugin"
  echo "Packer Vultr plugin successfully installed"
else
  echo "Packer vultr plugin already installed"
fi
