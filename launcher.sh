#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Run bubble launcher in a docker container. Works on Linux or Mac OS.
#
# Intended to be "run from anywhere" like this:
#
#   /bin/bash -c "$(curl -sL https://git.bubblev.org/bubblev/bubble/raw/branch/master/launcher.sh)"
#
# This command will:
#   - install docker if no "docker" command found
#   - pull bubble launcher docker image
#   - run bubble launcher docker image
#
# You'll be asked for an email address to associate with any LetsEncrypt certificates that will be created.
#
# If you want to run this unattended, set the LETSENCRYPT_EMAIL environment variable.
#
# Upon successful startup, the bubble launcher will be listening on port 8090
#
# Open http://127.0.0.1:8090/ in a web browser to continue with activation.
#

function die() {
  echo 1>&2 "

***** ${1}
"
  exit 1
}

function get_bubble_tag() {
  BUBBLE_RELEASE_URL="https://jenkins.bubblev.org/public/releases/bubble/latest.txt"
  VERSION="$(curl -s ${BUBBLE_RELEASE_URL} | awk -F '_' '{print $2}' | awk -F '.' '{print $1"."$2"."$3}')"
  if [[ -z "${VERSION}" ]]; then
    die "Error determining version from URL: ${BUBBLE_RELEASE_URL}"
  fi
  echo -n "getbubble/launcher:${VERSION}"
}

function ensure_docker_group() {
  CALLER="$(whoami)"
  if [[ "${CALLER}" != "root" && "$(sudo id -Gn "${CALLER}" | grep -c docker | tr -d ' ')" -eq 0 ]]; then
    echo "Adding user ${CALLER} to docker group ..."
    sudo usermod -a -G docker "${CALLER}"
  fi
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

  ensure_docker_group
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

  ensure_docker_group
}

function setup_docker_generic_linux() {
  curl -fsSL https://get.docker.com | sudo sh -
}

function setup_docker_linux() {
  DISTRO="$(cat /etc/os-release | grep "^NAME" | awk -F '=' '{print $2}' | tr -d '"')"
  if [[ $(echo -n ${DISTRO} | grep -c Debian | tr -d ' ') -gt 0 ]]; then
    setup_docker_debian
  elif [[ $(echo -n ${DISTRO} | grep -c Ubuntu | tr -d ' ') -gt 0 ]]; then
    setup_docker_ubuntu
  else
    setup_docker_generic_linux
  fi
}

function setup_docker_macosx() {
  if [[ -z "$(which brew)" ]]; then
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
  if [[ $(whoami) != "root" ]]; then
    echo "Note: you may need to enter your password (for Linux user $(whoami)) to enable sudo commands"
  fi

  if [[ "${PLATFORM}" == "Linux" ]]; then
    setup_docker_linux

  elif [[ "${PLATFORM}" == "Darwin" ]]; then
    setup_docker_macosx
    eval "$(docker-machine env default)"
    docker-machine start default

  else
    die "Don't know how to install docker on ${PLATFORM}"
  fi
}

function run_launcher() {
  PLATFORM="$(uname -s)"
  if [[ -z "${PLATFORM}" ]]; then
    die "'uname -s' returned empty string!"
  fi

  if [[ -z "$(which docker)" ]]; then
    setup_docker
    if [[ -z "$(which docker)" ]]; then
      die "Error installing docker
Install docker manually from https://docs.docker.com/engine/install/
Then re-run this script
"
    fi
  fi
  if [[ "${PLATFORM}" == "Linux" ]]; then
    ensure_docker_group
  fi

  # Determine bubble docker tag
  BUBBLE_TAG=$(get_bubble_tag)

  # Determine OS user
  CALLER="$(whoami)"

  # Pull bubble docker image
  if [[ "${CALLER}" == "root" ]] ; then
    docker pull "${BUBBLE_TAG}" || die "Error pulling docker image: ${BUBBLE_TAG}"
  else
    sudo su - "${CALLER}" -c "docker pull ${BUBBLE_TAG}" || die "Error pulling docker image: ${BUBBLE_TAG}"
  fi

  # Determine email for LetsEncrypt certs
  if [[ -z "${LETSENCRYPT_EMAIL}" ]]; then
    echo
    echo -n "Email address for LetsEncrypt certificates: "
    read -r LETSENCRYPT_EMAIL
  fi

  # Run bubble docker image
  if [[ "${CALLER}" == "root" ]] ; then
    docker run \
      -p 8090:8090 \
      -e LETSENCRYPT_EMAIL="${LETSENCRYPT_EMAIL}" \
      -t "${BUBBLE_TAG}"
  else
    sudo su - "${CALLER}" -c "docker run \
      -p 8090:8090 \
      -e LETSENCRYPT_EMAIL=${LETSENCRYPT_EMAIL} \
      -t ${BUBBLE_TAG}"
  fi
}

run_launcher
