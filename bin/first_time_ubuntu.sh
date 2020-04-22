#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
function die {
  echo 1>&2 "${1}"
  exit 1
}

# Ensure system is current
sudo apt update -y || die "Error running apt update"
sudo apt upgrade -y || die "Error running apt upgrade"

# Install packages
sudo apt install openjdk-11-jdk maven postgresql-10 redis-server jq python3 python3-pip npm webpack -y || die "Error installing apt packages"
sudo pip3 install setuptools psycopg2-binary || die "Error installing pip packages"

# Create DB user for current user, as superuser
CURRENT_USER="$(whoami)"
sudo su - postgres bash -c 'createuser -U postgres --createdb --createrole --superuser '"${CURRENT_USER}"'' || die "Error creating ${CURRENT_USER} DB user"

PG_HBA=/etc/postgresql/10/main/pg_hba.conf
sudo cat ${PG_HBA} | sed -e 's/  peer/  trust/g' | sed -e 's/  md5/  trust/g' > /tmp/pg_hba.conf || die "Error filtering ${PG_HBA}"
sudo bash -c "cat /tmp/pg_hba.conf > ${PG_HBA}" || die "Error rewriting ${PG_HBA}"
sudo service postgresql restart || die "Error restaring pgsql"

# Create DB user 'bubble', with the ability to create databases
createuser --createdb bubble || die "Error creating bubble DB user"

# Create bubble database
createdb --encoding=UTF-8 bubble || die "Error creating bubble DB"
