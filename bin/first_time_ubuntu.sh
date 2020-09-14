#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
function die {
  echo 1>&2 "${1}"
  exit 1
}

function db_user_exists {
  username="${1}"
  num_users="$(echo "select count(*) from pg_user where usename='${username}'" | su - postgres psql -qt | egrep -v '^$')"
  if [[ -z "${num_users}" || ${num_users} -eq 0 ]] ; then
    echo "0"
  else
    echo "1"
  fi
}

# Ensure system is current
sudo apt update -y || die "Error running apt update"
sudo apt upgrade -y || die "Error running apt upgrade"

# Install packages
sudo apt install openjdk-11-jdk maven postgresql redis-server jq python3 python3-pip npm webpack curl unzip -y || die "Error installing apt packages"
sudo pip3 install setuptools psycopg2-binary || die "Error installing pip packages"

# Install packer
BUBBLE_BIN="$(cd "$(dirname "${0}")" && pwd)"
"${BUBBLE_BIN}/install_packer.sh" || die "Error installing packer"

# Create DB user for current user, as superuser
CURRENT_USER="$(whoami)"
if [[ $(db_user_exists ${CURRENT_USER}) ]] ; then
  echo "PostgreSQL user ${CURRENT_USER} already exists, not creating"
else
  sudo su - postgres bash -c 'createuser -U postgres --createdb --createrole --superuser '"${CURRENT_USER}"'' || die "Error creating ${CURRENT_USER} DB user"
fi

PG_HBA=$(find /etc/postgresql -mindepth 1 -maxdepth 1 -type d | sort | tail -1)/main/pg_hba.conf
sudo cat ${PG_HBA} | sed -e 's/  peer/  trust/g' | sed -e 's/  md5/  trust/g' > /tmp/pg_hba.conf || die "Error filtering ${PG_HBA}"
sudo bash -c "cat /tmp/pg_hba.conf > ${PG_HBA}" || die "Error rewriting ${PG_HBA}"
sudo service postgresql restart || die "Error restarting pgsql"

# Create DB user 'bubble', with the ability to create databases
createuser --createdb bubble || die "Error creating bubble DB user"
