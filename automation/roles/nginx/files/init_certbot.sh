#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
LE_EMAIL="${1}"
SERVER_NAME="${2}"
SERVER_ALIAS="${3}"
if [[ $(find /etc/letsencrypt/accounts -type f -name regr.json | xargs grep -l \"${LE_EMAIL}\" | wc -l | tr -d ' ') -eq 0 ]] ; then
  certbot register --agree-tos -m "${LE_EMAIL}" --non-interactive
fi

if [[ ! -f /etc/letsencrypt/live/${SERVER_NAME}/fullchain.pem || ! -f /etc/letsencrypt/live/${SERVER_ALIAS}/fullchain.pem ]] ; then
  certbot certonly --standalone --non-interactive -d ${SERVER_NAME}
  certbot certonly --standalone --non-interactive -d ${SERVER_ALIAS}
else
  certbot renew --standalone --non-interactive
fi
