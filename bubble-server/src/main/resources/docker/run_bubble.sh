#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Run the Bubble API server. This is intended to be run from within a docker container
#
# This script normally exists as /etc/service/bubble/run on a Bubble launcher docker container
#
# See docs/docker-launcher.md for more information
# Full URL for more info: https://git.bubblev.org/bubblev/bubble/src/branch/master/docs/docker-launcher.md
#

if [[ -n "${BUBBLE_SERVER_PORT}" ]] ; then
  echo "export BUBBLE_SERVER_PORT=${BUBBLE_SERVER_PORT}" >> /bubble/bubble.env
fi
if [[ -n "${LETSENCRYPT_EMAIL}" ]] ; then
  echo "export LETSENCRYPT_EMAIL=${LETSENCRYPT_EMAIL}" >> /bubble/bubble.env
fi
if [[ -n "${PUBLIC_BASE_URI}" ]] ; then
  echo "export PUBLIC_BASE_URI=${PUBLIC_BASE_URI}" >> /bubble/bubble.env
fi

exec /usr/bin/java \
  -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true -Dbubble.listenAll=true \
  -XX:+UseG1GC -XX:MaxGCPauseMillis=400 \
  -cp /bubble/bubble.jar \
  bubble.server.BubbleServer \
  /bubble/bubble.env
