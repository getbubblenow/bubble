#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Performs first-time setup after a fresh git clone.
# Checks out submodules and performs initial build.
#
# Before from running this script, if you want to run the Bubble Server, install dependencies
# with the first_time_ubuntu.sh script. If you're running something other than Ubuntu (18.04 or 20.04),
# please add a first_time_<your-OS>.sh in this directory.
#
# If you're going to run the Bubble Server, you will also need
# Create environment files: ~/.bubble.env and ~/.bubble-test.env (one can be a symlink to the other)
#
# ~/.bubble.env is the environment used by the BubbleServer started by run.sh
# ~/.bubble-test.env is the environment used by the BubbleServer that runs during the integration tests
#
# If you prefer to checkout git submodules using SSH instead of HTTPS, set the BUBBLE_SSH_SUBMODULES
# environment variable to 'true'
#
# Environment Variables
#
#   SKIP_BUBBLE_BUILD  : if set to 1, then everything will be built except for the bubble jar
#
#   BUBBLE_SETUP_MODE  : only used if SKIP_BUBBLE_BUILD is not set. values are:
#      debug      - build the bubble jar without including the website
#      web        - build the bubble jar, including the website
#      production - build the bubble jar and the bubble full jar, both including the website
#

function die() {
  if [[ -z "${SCRIPT}" ]] ; then
    echo 1>&2 "${1}"
  else
    echo 1>&2 "${SCRIPT}: ${1}"
  fi
  exit 1
}

BASE="$(cd "$(dirname "${0}")/.." && pwd)"
cd "${BASE}" || die "Error changing to ${BASE} directory"

if [[ -z "${BUBBLE_SSH_SUBMODULES}" || "${BUBBLE_SSH_SUBMODULES}" != "true" ]] ; then
  "${BASE}"/bin/git_https_submodules.sh || die "Error switching to HTTPS git submodules"
fi

git submodule update --init --recursive || die "Error in git submodule update"

pushd utils/cobbzilla-parent || die "Error pushing utils/cobbzilla-parent directory"
mvn -q install || die "Error installing cobbzilla-parent"
popd || die "Error popping back from utils/cobbzilla-parent"

UTIL_REPOS="
cobbzilla-parent
cobbzilla-utils
templated-mail-sender
cobbzilla-wizard
abp-parser
"
pushd utils || die "Error pushing utils directory"
MVN_QUIET="-q -DskipTests=true -Dcheckstyle.skip=true"
for repo in ${UTIL_REPOS}; do
  pushd "${repo}" && mvn ${MVN_QUIET} clean install && popd || die "Error installing ${repo}"
done
popd || die "Error popping back from utils directory"

if [[ -n "${SKIP_BUBBLE_BUILD}" && "${SKIP_BUBBLE_BUILD}" == "1" ]] ; then
  exit 0
fi

if [[ -z "${BUBBLE_SETUP_MODE}" || "${BUBBLE_SETUP_MODE}" == "web" ]] ; then
  INSTALL_WEB=web mvn ${MVN_QUIET} -Pproduction clean package || die "Error building bubble jar"

elif [[ "${BUBBLE_SETUP_MODE}" == "debug" ]] ; then
  DEBUG_BUILD=debug mvn ${MVN_QUIET} -Pproduction clean package || die "Error building bubble jar"

elif [[ "${BUBBLE_SETUP_MODE}" == "production" ]] ; then
  BUBBLE_PRODUCTION=1 mvn ${MVN_QUIET} -Pproduction clean package || die "Error building bubble jar"
  BUBBLE_PRODUCTION=1 mvn ${MVN_QUIET} -Pproduction-full package || die "Error building bubble full jar"

else
  die "env var BUBBLE_SETUP_MODE was invalid: ${BUBBLE_SETUP_MODE}"
fi
