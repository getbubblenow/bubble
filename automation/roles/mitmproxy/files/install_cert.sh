#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
#
CERT="${1:?no cert provided}"
TIMEOUT=${2:-0}

function die {
  echo 1>&2 "${1}"
  exit 1
}

START=$(date +%s)
while [[ ! -f "${CERT}" ]] ; do
  ELAPSED=$(expr $(date +%s) - ${START})
  if [[ ${ELAPSED} -gt ${TIMEOUT} ]] ; then
    break
  fi
  echo "Cert file does not exist, sleeping then rechecking: ${CERT}"
  sleep 5s
done

if [[ ! -f "${CERT}" ]] ; then
  die "Cert file does not exist: ${CERT}"
fi

if [[ "${CERT}" == *.pem || "${CERT}" == *.p12 ]] ; then
  openssl x509 -in "${CERT}" -inform PEM -out "${CERT}.crt" || die "Error converting certificate"
  CERT="${CERT}.crt"
fi

mkdir -p /usr/local/share/ca-certificates || die "Error ensuring CA certs directory exists"
cp "${CERT}" /usr/local/share/ca-certificates || die "Error installing certificate"
update-ca-certificates || die "Error updating CA certificates"
