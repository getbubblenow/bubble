#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
function die {
  echo 1>&2 "${1}"
  exit 1
}

# Ensure system is current

# Install packer
BUBBLE_BIN="$(cd "$(dirname "${0}")" && pwd)"
"${BUBBLE_BIN}/install_packer.sh" || die "Error installing packer"

# Install homebrew
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install.sh)"

# Install emacs
brew cask install emacs

# Install AdoptOpenJDK 11
echo ">>> Please install AdoptOpenJDK 11 from https://adoptopenjdk.net/index.html?variant=openjdk11&jvmVariant=hotspot"

# Install IntelliJ IDEA
echo "Consider installing IntelliJ IDEA from https://www.jetbrains.com/idea/download/#section=mac"

# Install packages
brew install maven
brew install postgresql@10 && brew services start postgresql@10
brew install redis && brew services start redis
brew install jq
brew install python@3.8
brew install npm
brew install webpack
sudo pip3 install setuptools psycopg2-binary

# Add python paths to script rc
export LDFLAGS="-L/usr/local/opt/python@3.8/lib"
export PATH="/usr/local/opt/python@3.8/bin:$PATH"

# Create DB user 'postgres' as super admin
createuser --createdb --superuser --createrole postgres || die "Error creating postgres DB user"

# Create DB user 'bubble', with the ability to create databases
createuser --createdb bubble || die "Error creating bubble DB user"

# Create bubble database
createdb --encoding=UTF-8 bubble || die "Error creating bubble DB"
