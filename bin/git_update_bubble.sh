#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
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
if [[ -n "${FAST}" && "${FAST}" == "fast" ]] ; then
  FAST=1
else
  FAST=0
fi

BASE=$(cd $(dirname $0)/.. && pwd)
cd ${BASE}

git fetch || die "Error calling git fetch"
git pull origin master || die "Error calling git pull origin master"
git submodule update --init --recursive || die "Error in git submodule update"

pushd utils/cobbzilla-parent
mvn install || die "Error installing cobbzilla-parent"
popd

UTIL_REPOS="
cobbzilla-parent
cobbzilla-utils
templated-mail-sender
cobbzilla-wizard
abp-parser
"
pushd utils
MVN_QUIET="-q -DskipTests=true -Dcheckstyle.skip=true"
for repo in ${UTIL_REPOS} ; do
  if [[ ${FAST} -eq 1 ]] ; then
    pushd ${repo} && mvn ${MVN_QUIET} install && popd || die "Error installing ${repo}"
  else
    pushd ${repo} && mvn ${MVN_QUIET} clean install && popd || die "Error installing ${repo}"
  fi
done
popd

if [[ ${FAST} -eq 1 ]] ; then
  mvn ${MVN_QUIET} clean package || die "Error building bubble jar"
else
  BUBBLE_PRODUCTION=1 mvn ${MVN_QUIET} -Pproduction clean package || die "Error building bubble jar"
  BUBBLE_PRODUCTION=1 mvn ${MVN_QUIET} -Pproduction-full package || die "Error building bubble full jar"
fi
