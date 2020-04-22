#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
#
# Performs first-time setup after a fresh git clone.
# Installs utility libraries.
#
# Before from running this script, if you want to run the Bubble Server, install dependencies
# with the first_time_ubuntu.sh script. If you're running something other than Ubuntu 18.04,
# please add a first_time_<your-OS>.sh in this directory.
#
# If you're going to run the Bubble Server, you will also need
# Create environment files: ~/.bubble.env and ~/.bubble-test.env (one can be a symlink to the other)
#
# ~/.bubble.env is the environment used by the BubbleServer started by run.sh
# ~/.bubble-test.env is the environment used by the BubbleServer that runs during the integration tests
#

function die {
  if [[ -z "${SCRIPT}" ]] ; then
    echo 1>&2 "${1}"
  else
    echo 1>&2 "${SCRIPT}: ${1}"
  fi
  exit 1
}

BASE=$(cd $(dirname $0)/.. && pwd)
cd ${BASE}

git submodule init || die "Error in git submodule init"
git submodule update || die "Error in git submodule update"

pushd utils/cobbzilla-parent
mvn install || die "Error installing cobbzilla-parent"
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
  pushd ${repo} && git checkout master && mvn -DskipTests=true -Dcheckstyle.skip=true clean install && popd || die "Error installing ${repo}"
done
popd

INSTALL_WEB=web mvn -DskipTests=true -Dcheckstyle.skip=true clean package || die "Error building bubble jar"
