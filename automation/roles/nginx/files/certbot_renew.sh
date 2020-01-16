#!/bin/bash

if [[ -d /home/mitmproxy ]] ; then
  service mitmproxy stop
fi
service nginx stop

certbot renew --standalone --non-interactive || echo "Error updating SSL certificates"

if [[ -d /home/mitmproxy ]] ; then
  service mitmproxy restart
fi
service nginx restart
