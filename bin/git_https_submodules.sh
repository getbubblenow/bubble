#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Update .gitmodules file to use HTTPS URLs instead of SSH URLs for git submodules
#
SCRIPT_DIR="$(cd "$(dirname "${0}")" && pwd)"
GIT_MODS="${SCRIPT_DIR}/../.gitmodules"
GIT_TEMP_FILE="$(mktemp "${GIT_MODS}.XXXXXXX")"
CHANGED_MARKER="${GIT_TEMP_FILE}.use"
cat "${GIT_MODS}" | while read -r line; do
  if [[ $(echo "${line}" | grep -c 'url = ') -gt 0 && $(echo "${line}" | grep -c 'git@git') -gt 0 ]]; then
    REPO="$(echo "${line}" | awk -F ':' '{print $2}')"
    echo "url = https://git.bubblev.org/${REPO}" | tee -a "${GIT_TEMP_FILE}"
    touch "${CHANGED_MARKER}"
  else
    echo "${line}" | tee -a "${GIT_TEMP_FILE}"
  fi
done

if [[ -f "${CHANGED_MARKER}" ]]; then
  cd "${SCRIPT_DIR}/.." && \
    git update-index --assume-unchanged "$(basename "${GIT_MODS}")" && \
    mv "${GIT_TEMP_FILE}" "${GIT_MODS}" || \
    echo "$0: error updating file: ${GIT_MODS}"
fi
rm -f "${CHANGED_MARKER}"
