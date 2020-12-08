#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#

exec /usr/bin/java \
  -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true -Dbubble.listenAll=true \
  -XX:+UseG1GC -XX:MaxGCPauseMillis=400 \
  -cp /bubble/bubble.jar \
  bubble.server.BubbleServer \
  /bubble/bubble.env
