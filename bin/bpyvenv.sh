#!/bin/bash
#
# Set up python venv to run scripts in bin
#
function die {
  echo 1>&2 "$0: ${1}"
  exit 1
}

BUBBLE_HOME="$(cd $(dirname ${0})/.. && pwd)"

cd ${BUBBLE_HOME} || die "Error changing to ${BUBBLE_HOME} dir"

if [[ ! -d "${BUBBLE_HOME}/.venv" ]] ; then
  python3 -m venv ./.venv || die "Error creating venv"
fi
. ${BUBBLE_HOME}/.venv/bin/activate || die "Error activating bubble venv"
python3 -m pip install requests || die "Error installing pip packages"

if [[ ! -z "${1}" ]] ; then
  script=${1}
  shift
  echo python3 "${script}" "${@}"
else
  echo "venv successfully set up"
fi
