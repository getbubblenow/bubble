#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
#

if [[ -d /home/mitmproxy ]] ; then
  service mitmproxy stop
fi
service nginx stop

certbot renew --standalone --non-interactive || echo "Error updating SSL certificates"

if [[ -d /home/mitmproxy ]] ; then
  service mitmproxy restart
fi
service nginx restart
