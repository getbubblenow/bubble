#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
#

function die {
  echo 1>&2 "${1}"
  exit 1
}

MITM_CERTS=/home/mitmproxy/.mitmproxy
chown -R mitmproxy ${MITM_CERTS} || die "Error setting ownership on ${MITM_CERTS}"
chgrp -R root ${MITM_CERTS} || die "Error setting group on ${MITM_CERTS}"
chmod 750 ${MITM_CERTS} || die "Error setting permissions on ${MITM_CERTS}"
chmod -R 440 ${MITM_CERTS}/* || die "Error setting permissions on ${MITM_CERTS} files"

CERTS_DIR=/home/bubble/cacerts

CERT_BASE="${1:?no cert base provided}"
MITM_BASE_NAME="${CERT_BASE}-ca"

mkdir -p ${CERTS_DIR} || die "Error creating cacerts dir"
cp ${MITM_CERTS}/${MITM_BASE_NAME}-cert.pem ${CERTS_DIR} || die "Error copying pem cert"
cp ${MITM_CERTS}/${MITM_BASE_NAME}-cert.pem.crt ${CERTS_DIR}/${MITM_BASE_NAME}-cert.crt || die "Error copying crt cert"
cp ${MITM_CERTS}/${MITM_BASE_NAME}-cert.p12 ${CERTS_DIR} || die "Error copying p12 cert"
cp ${MITM_CERTS}/${MITM_BASE_NAME}-cert.cer ${CERTS_DIR} || die "Error copying cer cert"
chown -R bubble ${CERTS_DIR} || die "Error setting permissions on cacerts dir"
chmod 755 ${CERTS_DIR} || die "Error setting permissions on ${CERTS_DIR}"
chmod -R 444 ${CERTS_DIR}/* || die "Error setting permissions on ${CERTS_DIR} files"

CERTS_BACKUP=/home/bubble/mitm_certs
mkdir -p ${CERTS_BACKUP} || die "Error creating mitm_certs dir"
chmod 700 ${CERTS_BACKUP} || die "Error setting permissions on mitm_certs dir"
cp ${MITM_CERTS}/* ${CERTS_BACKUP} || die "Error backing up mitm_certs"
chmod -R 400 ${CERTS_BACKUP}/* || die "Error setting permissions on mitm_certs backup"
chown -R bubble ${CERTS_BACKUP} || die "Error settings ownership of mitm_certs dir"
