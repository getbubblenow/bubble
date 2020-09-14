#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
function die {
  echo 1>&2 "${1}"
  exit 1
}

# Install packer
if [[ ! -f ${HOME}/packer/packer ]] ; then
  PACKER_VERSION=1.5.6
  PACKER_FILE=packer_${PACKER_VERSION}_linux_amd64.zip
  PACKER_URL=https://releases.hashicorp.com/packer/${PACKER_VERSION}/${PACKER_FILE}
  mkdir -p ${HOME}/packer && cd ${HOME}/packer && wget ${PACKER_URL} && unzip ${PACKER_FILE} || die "Error installing packer"
else
  echo "Packer already installed"
fi

# Install packer Vultr plugin
if [[ ! -f ${HOME}/.packer.d/plugins/packer-builder-vultr ]] ; then
  PACKER_VULTR_VERSION=1.0.11
  PACKER_VULTR_FILE=packer-builder-vultr_${PACKER_VULTR_VERSION}_linux_64-bit.tar.gz
  PACKER_VULTR_URL=https://github.com/vultr/packer-builder-vultr/releases/download/v${PACKER_VULTR_VERSION}/${PACKER_VULTR_FILE}
  mkdir -p ${HOME}/.packer.d/plugins && cd ${HOME}/.packer.d/plugins && wget ${PACKER_VULTR_URL} && tar xzf ${PACKER_VULTR_FILE}  || die "Error installing packer vultr plugin"
else
  echo "Packer vultr plugin already installed"
fi
