#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Perform one-time setup of a new Mac OS X system.
#
# NOTE: you must manually install AdoptOpenJDK 11 from https://adoptopenjdk.net/index.html?variant=openjdk11&jvmVariant=hotspot
#
# It is safe to run this multiple times, it is idempotent.
#
function die {
  echo 1>&2 "${1}"
  exit 1
}

function db_user_exists {
  username="${1}"
  num_users="$(echo "select count(*) from pg_user where usename='${username}'" | psql -qt template1 | egrep -v '^$')"
  if [[ -z "${num_users}" || ${num_users} -eq 0 ]] ; then
    echo "0"
  else
    echo "1"
  fi
}

# Install packer
BUBBLE_BIN="$(cd "$(dirname "${0}")" && pwd)"
"${BUBBLE_BIN}/install_packer.sh" || die "Error installing packer"

if [[ -z "$(which brew)" ]] ; then
  # Install homebrew
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install.sh)"
fi

# Install emacs
brew cask install emacs

# Install AdoptOpenJDK 11
echo ; echo '
----------------------------------------------------------------------------------------------------------------
>>> Manual installation of Java is required
>>> Please install AdoptOpenJDK 11 from https://adoptopenjdk.net/index.html?variant=openjdk11&jvmVariant=hotspot
----------------------------------------------------------------------------------------------------------------
'

# Install packages
#brew install maven
#brew install postgresql@10 && brew services start postgresql@10
#brew install redis && brew services start redis
#brew install jq
#brew install python@3.8
#brew install npm
#brew install webpack
#sudo pip3 install setuptools psycopg2-binary

# Add python paths to script rc
export LDFLAGS="-L/usr/local/opt/python@3.8/lib"
export PATH="/usr/local/opt/python@3.8/bin:$PATH"

CURRENT_USER="$(whoami)"

# Create DB user 'postgres' as super admin
if [[ $(db_user_exists 'postgres') == "1" ]] ; then
  echo "PostgreSQL user 'postgres' already exists, not creating"
else
  echo "Creating PostgreSQL user: postgres"
  createuser --createdb --superuser --createrole postgres || die "Error creating postgres DB user"
fi

# Create DB user for current user as super admin
if [[ $(db_user_exists "${CURRENT_USER}") == "1" ]] ; then
  echo "PostgreSQL user ${CURRENT_USER} already exists, not creating"
else
  echo "Creating PostgreSQL user: ${CURRENT_USER}"
  createuser --createdb --superuser --createrole postgres || die "Error creating ${CURRENT_USER} DB user"
fi

# Create DB user 'bubble', with the ability to create databases
if [[ $(db_user_exists 'bubble') == "1" ]] ; then
  echo "PostgreSQL user bubble already exists, not creating"
else
  echo "Creating PostgreSQL user: bubble"
  createuser --createdb bubble || die "Error creating bubble DB user"
fi
