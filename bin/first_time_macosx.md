#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
function die {
  echo 1>&2 "${1}"
  exit 1
}

# Ensure system is current

# Install homebrew
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install.sh)"

# Install emacs
brew cask install emacs

# Install AdoptOpenJDK 11
install from https://adoptopenjdk.net/index.html?variant=openjdk11&jvmVariant=hotspot

# Install packages
brew install maven
brew install postgresql@10 && brew services start postgresql@10
brew install redis && brew services start redis
brew install jq
brew install python@3.8

# Create DB user 'bubble', with the ability to create databases
createuser -U postgres --createdb bubble || die "Error creating bubble DB user"

# Create bubble database
createdb --encoding=UTF-8 bubble || die "Error creating bubble DB"
