#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Creates a Docker image that runs the Bubble Launcher. You shouldn't have to use this file directly.
#
# For Linux and Mac OS, you can try the easier way, using a prebuilt image from DockerHub:
#
#   /bin/bash -c "$(curl -sL https://git.bubblev.org/bubblev/bubble/raw/branch/master/docker/launcher.sh)"
#
# See docs/docker-launcher.md for more information
#
FROM phusion/baseimage:focal-1.0.0alpha1-amd64
MAINTAINER jonathan@getbubblenow.com
LABEL maintainer="jonathan@getbubblenow.com"
LABEL license="https://getbubblenow.com/license"

# Install packages
RUN apt update -y
RUN DEBIAN_FRONTEND=noninteractive apt upgrade -y --no-install-recommends
RUN DEBIAN_FRONTEND=noninteractive apt install openjdk-11-jre-headless postgresql redis-server jq python3 python3-pip curl unzip -y --no-install-recommends
RUN DEBIAN_FRONTEND=noninteractive apt install xtail -y --no-install-recommends
RUN pip3 install setuptools psycopg2-binary

#################
### Redis
#################
# Ensure redis runs in foreground
RUN bash -c "sed -i -e 's/daemonize yes/daemonize no/g' /etc/redis/redis.conf"

# Setup redis service
RUN mkdir /etc/service/redis
COPY docker/run_redis.sh /etc/service/redis/run

#################
### PostgreSQL
#################
# trust local postgresql users
RUN bash -c "sed -i -e 's/  md5/  trust/g' $(find /etc/postgresql -mindepth 1 -maxdepth 1 -type d | sort | tail -1)/main/pg_hba.conf"

# Create "root" postgres user and bubble database
RUN bash -c "service postgresql start && sleep 5s && service postgresql status && \
  su - postgres bash -c 'createuser -h 127.0.0.1 -U postgres --createdb --createrole --superuser root' && \
  su - postgres bash -c 'createuser -h 127.0.0.1 -U postgres --createdb bubble'"

# Setup PostgreSQL service
RUN mkdir /etc/service/postgresql
COPY docker/run_postgresql.sh /etc/service/postgresql/run

#################
### Bubble
#################
# Install packer
RUN mkdir /bubble
COPY bin/install_packer.sh /usr/local/bin/
RUN /usr/local/bin/install_packer.sh

# Install API jar and env file
COPY bubble-server/target/bubble-server-*.jar /bubble/bubble.jar
COPY docker/bubble.env /bubble/bubble.env

# Setup Bubble service
RUN mkdir /etc/service/bubble
COPY docker/run_bubble.sh /etc/service/bubble/run

#################
### Main stuff
#################
# Expose bubble port
EXPOSE 8090

# Phusion baseimage runs the services created above
CMD ["/sbin/my_init"]
