#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Run bubble launcher in a docker container. Works on Linux or Mac OS.
#
#   /bin/bash -c "$(curl -sL https://git.bubblev.org/bubblev/bubble/raw/branch/master/docker/launcher.sh)"
#
# This command will:
#   - install docker if no "docker" command found
#   - pull bubble launcher docker image
#   - run bubble launcher docker image
#
# Upon successful startup, the bubble launcher will be listening on port 8090
#
# Open http://127.0.0.1:8090/ in a web browser to continue with activation.
#

function die {
  echo 1>&2 "${1}"
  exit 1
}

function get_bubble_tag() {
  BUBBLE_RELEASE_URL="https://jenkins.bubblev.org/public/releases/bubble/latest.txt"
  VERSION="$(curl -s ${BUBBLE_RELEASE_URL}  | awk -F '_' '{print $2}' | awk -F '.' '{print $1"."$2"."$3}')"
  if [[ -z "${VERSION}" ]] ; then
    die "Error determining version from URL: ${BUBBLE_RELEASE_URL}"
  fi
  echo -n "getbubble/launcher:${VERSION}"
}

function setup_docker_debian() {
  # Ensure apt is up to date
  sudo apt update -y

  # Remove obsolete packages
  sudo apt-get remove docker docker-engine docker.io containerd runc

  # Ensure apt can install packages over https
  sudo apt install -y apt-transport-https ca-certificates curl gnupg-agent software-properties-common

  # Install docker GPG key
  curl -fsSL https://download.docker.com/linux/debian/gpg | sudo apt-key add -

  # Add docker repo
  sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/debian $(lsb_release -cs) stable"

  # Refresh apt after adding repo
  sudo apt update -y

  # Install docker
  sudo apt install -y docker-ce docker-ce-cli containerd.io
}

function setup_docker_ubuntu() {
  # Ensure apt is up to date
  sudo apt update -y

  # Remove obsolete packages
  sudo apt-get remove docker docker-engine docker.io containerd runc

  # Ensure apt can install packages over https
  sudo apt install -y apt-transport-https ca-certificates curl gnupg-agent software-properties-common

  # Install docker GPG key
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -

  # Add docker repo
  sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"

  # Refresh apt after adding repo
  sudo apt update -y

  # Install docker
  sudo apt install -y docker-ce
}

function setup_docker_centos_dnf() {
  # Update dnf
  sudo dnf update -y --nobest

  # Add docker repo
  sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo

  # Refresh dnf after adding repo
  sudo dnf update -y --nobest

  # Install docker
  sudo dnf install -y docker-ce --best --allowerasing

  # Start docker
  sudo systemctl start docker
}

function setup_docker_centos_yum() {
  # Remove obsolete docker packages
  sudo yum remove docker docker-client docker-client-latest docker-common docker-latest docker-latest-logrotate docker-logrotate docker-engine

  # Add docker repo
  sudo yum install -y yum-utils
  sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo

  # Install docker
  sudo yum install docker-ce docker-ce-cli containerd.io

  # Start docker
  sudo systemctl start docker
}

function setup_docker_fedora_dnf() {
  # Update dnf
  sudo dnf update -y --nobest

  # Install docker
  sudo dnf install -y docker-ce

  # Start docker
  sudo systemctl start docker
}

function setup_docker_linux() {
  DISTRO="$(cat /etc/os-release | grep "^NAME" | awk -F '=' '{print $2}' | tr -d '"')"
  if [[ $(echo -n ${DISTRO} | grep -c Debian | tr -d ' ') -gt 0 ]] ; then
    setup_docker_debian
  elif [[ $(echo -n ${DISTRO} | grep -c Ubuntu | tr -d ' ') -gt 0 ]] ; then
    setup_docker_ubuntu
  elif [[ $(echo -n ${DISTRO} | grep -c CentOS | tr -d ' ') -gt 0 ]] ; then
    if [[ ! -z "$(which dnf)" ]] ; then
      setup_docker_centos_dnf
    elif [[ ! -z "$(which yum)" ]] ; then
      setup_docker_centos_yum
    else
      die "Neither dnf nor yum found, cannot install on CentOS.
Please install docker manually from https://docker.io/"
    fi
  elif [[ $(echo -n ${DISTRO} | grep -c Fedora | tr -d ' ') -gt 0 ]] ; then
    setup_docker_fedora_dnf
  else
    die "Automatic docker installation for ${DISTRO} is not yet supported
Please install docker manually from https://docker.io/"
  fi
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
    echo "Installing docker via sudo ..."
    if [[ $(whoami) != "root" ]] ; then
      echo "Note: you may need to enter your password (for Linux user $(whoami)) to enable sudo commands"
    fi

    if [[ "${PLATFORM}" == "Linux" ]] ; then
      setup_docker_linux

    elif [[ "${PLATFORM}" == "Darwin" ]] ; then
      setup_docker_macosx
      eval "$(docker-machine env default)"
      docker-machine start default

    else
      die "Don't know how to install docker on ${PLATFORM}"
    fi
}

function run_launcher() {
  PLATFORM="$(uname -s)"
  if [[ -z "${PLATFORM}" ]] ; then
    die "'uname -s' returned empty string!"
  fi

  if [[ -z "$(which docker)" ]] ; then
    setup_docker
  fi

  # Determine bubble docker tag
  BUBBLE_TAG=$(get_bubble_tag)

  # Pull bubble docker image
  docker pull ${BUBBLE_TAG} || die "Error pulling docker image: ${BUBBLE_TAG}"

  # Run bubble docker image
  docker run -p 8090:8090 -t ${BUBBLE_TAG}
}

run_launcher
