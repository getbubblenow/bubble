#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Run bubble launcher in a docker container. Intended for non-developers to run via curl | bash:
#
# Usage:
#
#    launcher.sh
#
# This command will:
#   - install docker if not found
#   - pull and run bubble docker image
#

function die {
  echo 1>&2 "${1}"
  exit 1
}

BUBBLE_META_URL="https://git.bubblev.org/bubblev/bubble/raw/branch/master/bubble-server/src/main/resources/META-INF/bubble/bubble.properties"
VERSION="$(curl -s ${BUBBLE_META_URL} | grep bubble.version | awk -F '=' '{print $2}' | awk -F ' ' '{print $NF}' | awk '{$1=$1};1')"
if [[ -z "${VERSION}" ]] ; then
  die "Error determining version from URL: ${BUBBLE_META_URL}"
fi
BUBBLE_TAG="getbubble/launcher:${VERSION}"

function setup_docker_linux() {
  # Ensure apt is up to date
  sudo apt update -y

  # Ensure apt can install packages over https
  sudo apt install -y apt-transport-https ca-certificates curl software-properties-common

  # Install Docker GPG key
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -

  # Add Docker apt repo
  sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu focal stable"

  # Refresh apt after adding repo
  sudo apt update -y

  # Install docker
  sudo apt install -y docker-ce
}

function setup_docker_macosx() {
  if [[ -z "$(which brew)" ]] ; then
    die "Homebrew not installed (brew command not found). Install homebrew by running:
    /bin/bash -c \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install.sh)\"
    "
  fi
  brew install docker docker-machine || die "Error installing docker and docker-machine"
  brew cask install virtualbox || die "Error installing virtualbox (check Security Settings)"
  docker-machine create --driver virtualbox default
}

function setup_docker() {
  PLATFORM="$(uname -s)"
  if [[ -z "${PLATFORM}" ]] ; then
    die "'uname -a command' returned empty string!"
  fi

  if [[ -z "$(which docker)" ]] ; then
    echo "docker command not found"
    echo "Installing docker via sudo (you may need to enter your password) ..."
    if [[ "${PLATFORM}" == "Linux" ]] ; then
      setup_docker_linux
    elif [[ "${PLATFORM}" == "Darwin" ]] ; then
      setup_docker_macosx
      eval "$(docker-machine env default)"
    else
      die "Don't know how to install docker on ${PLATFORM}"
    fi
  fi

  # Pull bubble docker image
  docker pull ${BUBBLE_TAG}

  # Run bubble docker image
  docker run -p 8090:8090 -t ${BUBBLE_TAG} || die "Error running docker container"

}

setup_docker
