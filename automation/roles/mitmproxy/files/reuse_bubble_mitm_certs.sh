#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
#
function die {
  echo 1>&2 "${1}"
  exit 1
}

CERTS_BACKUP=/home/bubble/mitm_certs
if [[ ! -d ${CERTS_BACKUP} ]] ; then
  echo "No mitm_certs backup found, skipping restore"
  exit 0
fi

MITM_CERTS=/home/mitmproxy/.mitmproxy
if [[ -d ${MITM_CERTS} ]] ; then
  echo "Removing obsolete mitm certs: ${MITM_CERTS}"
  rm -rf ${MITM_CERTS} || die "Error removing obsolete mitm certs"
  if [[ -d ${MITM_CERTS} ]] ; then
    die "Error removing obsolete mitm certs: dir still exists: ${MITM_CERTS}"
  fi
fi

mkdir -p ${MITM_CERTS} || die "Error creating mitm certs dir: ${MITM_CERTS}"
chmod 750 ${MITM_CERTS} || die "Error setting permissions on mitm certs dir: ${MITM_CERTS}"
cp -R ${CERTS_BACKUP}/* ${MITM_CERTS}/ || die "Error restoring mitm certs"
chown -R mitmproxy ${MITM_CERTS} || die "Error changing ownership of ${MITM_CERTS}"
chgrp -R root ${MITM_CERTS} || die "Error changing group ownership of ${MITM_CERTS}"
chmod 440 ${MITM_CERTS}/* || die "Error setting permissions on mitm certs files"
