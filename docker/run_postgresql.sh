#!/bin/sh
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
# adapted from https://stackoverflow.com/q/11092358
#
# This script is run by Supervisor to start PostgreSQL in foreground mode.
#
# WARNING: If you have more than one PostgreSQL version installed, there could be problems.
# This script assumes you have only one version of PostgreSQL installed.
#
if [ -d /var/run/postgresql ]; then
  chmod 2775 /var/run/postgresql
else
  install -d -m 2775 -o postgres -g postgres /var/run/postgresql
fi

exec su postgres -c "$(find $(find /usr/lib/postgresql -type d -name bin | head -1) -type f -name postgres | head -1) \
  -D $(find /usr/lib/postgresql -type d -name main | head -1) \
  -c config_file=$(find /etc/postgresql -type d -name main | head -1)/postgresql.conf"
