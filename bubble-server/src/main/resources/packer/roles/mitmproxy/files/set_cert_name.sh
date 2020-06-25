#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
MITM_DIR=${1:?no mitm dir specified}
CERT_NAME="${2:?no cert name specified}"
CERT_ORGANIZATION="${3:-Bubble}"
CERT_CN="${4:-$(hostname -d)}"

if [[ ! -d "${MITM_DIR}" ]] ; then
  echo "mitm dir does not exist or is not a directory: ${MITM_DIR}"
  exit 1
fi

OPTIONS_FILE="${MITM_DIR}/mitmproxy/options.py"
if [[ ! -f "${OPTIONS_FILE}" ]] ; then
  echo "options.py not found in mitm dir: ${MITM_DIR}"
  exit 1
fi

if [[ $(cat "${OPTIONS_FILE}" | egrep '^CONF_BASENAME =' | grep "${CERT_NAME}" | wc -l | tr -d ' ') -eq 0 ]] ; then
  temp="$(mktemp /tmp/options.py.XXXXXXX)"
  cat "${OPTIONS_FILE}" \
    | sed -e 's/^CONF_BASENAME\s*=.*/CONF_BASENAME = "'"${CERT_NAME}"'"/' \
    | sed -e 's/^CONF_CERT_ORGANIZATION\s*=.*/CONF_CERT_ORGANIZATION = "'"${CERT_ORGANIZATION}"'"/' \
    | sed -e 's/^CONF_CERT_CN\s*=.*/CONF_CERT_CN = "'"${CERT_CN}"'"/' \
    > "${temp}"
  mv "${temp}" "${OPTIONS_FILE}"
fi
