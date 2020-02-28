#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
#
#
# Update repository from master, including submodules, and rebuild bubble jar file
#
# Usage:
#
#     git_update_bubble.sh [fast]
#
#  fast : if the first argument is 'fast', then don't perform "clean" builds for submodules, just repackage/reinstall them
#
function die {
  if [[ -z "${SCRIPT}" ]] ; then
    echo 1>&2 "${1}"
  else
    echo 1>&2 "${SCRIPT}: ${1}"
  fi
  exit 1
}

FAST=${1}
if [[ ! -z "${FAST}" && "${FAST}" == "fast" ]] ; then
  FAST=1
else
  FAST=0
fi

BASE=$(cd $(dirname $0)/.. && pwd)
cd ${BASE}

git fetch || die "Error calling git fetch"
git pull origin master || die "Error calling git pull origin master"
git submodule update || die "Error in git submodule update"

pushd utils/cobbzilla-parent
git fetch && git checkout master && git pull origin master && mvn install || die "Error updating/installing cobbzilla-parent"
popd

UTIL_REPOS="
cobbzilla-parent
cobbzilla-utils
restex
templated-mail-sender
cobbzilla-wizard
abp-parser
"
pushd utils
for repo in ${UTIL_REPOS} ; do
  if [[ ${FAST} -eq 1 ]] ; then
    pushd ${repo} && git fetch && git checkout master && git pull origin master && mvn -DskipTests=true -Dcheckstyle.skip=true install && popd || die "Error installing ${repo}"
  else
    pushd ${repo} && git fetch && git checkout master && git pull origin master && mvn -DskipTests=true -Dcheckstyle.skip=true clean install && popd || die "Error installing ${repo}"
  fi
done
popd

if [[ ${FAST} -eq 1 ]] ; then
  mvn -DskipTests=true -Dcheckstyle.skip=true clean package || die "Error building bubble jar"
else
  INSTALL_WEB=web mvn -DskipTests=true -Dcheckstyle.skip=true clean package || die "Error building bubble jar"
fi
