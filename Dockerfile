#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
FROM phusion/baseimage:focal-1.0.0alpha1-amd64
MAINTAINER jonathan@getbubblenow.com
LABEL maintainer="jonathan@getbubblenow.com"
LABEL license="https://getbubblenow.com/license"

RUN apt update -y
RUN DEBIAN_FRONTEND=noninteractive apt upgrade -y --no-install-recommends
RUN DEBIAN_FRONTEND=noninteractive apt install openjdk-11-jre-headless postgresql redis-server jq python3 python3-pip curl unzip -y --no-install-recommends
RUN DEBIAN_FRONTEND=noninteractive apt install xtail -y --no-install-recommends
RUN pip3 install setuptools psycopg2-binary

RUN bash -c "sed -i -e 's/daemonize yes/daemonize no/g' /etc/redis/redis.conf"

RUN bash -c "sed -i -e 's/  md5/  trust/g' $(find /etc/postgresql -mindepth 1 -maxdepth 1 -type d | sort | tail -1)/main/pg_hba.conf"
RUN bash -c "service postgresql start && sleep 5s && service postgresql status && \
  su - postgres bash -c 'createuser -h 127.0.0.1 -U postgres --createdb --createrole --superuser root' && \
  su - postgres bash -c 'createuser -h 127.0.0.1 -U postgres --createdb bubble'"

RUN mkdir /etc/service/postgresql
COPY docker/run_postgresql.sh /etc/service/postgresql/run

RUN mkdir /etc/service/redis
COPY docker/run_redis.sh /etc/service/redis/run

RUN mkdir /bubble
COPY bin/install_packer.sh /usr/local/bin/
RUN /usr/local/bin/install_packer.sh

COPY bubble-server/target/bubble-server-*.jar /bubble/bubble.jar
COPY docker/bubble.env /bubble/bubble.env

RUN mkdir /etc/service/bubble
COPY docker/run_bubble.sh /etc/service/bubble/run

EXPOSE 8090

CMD ["/sbin/my_init"]
