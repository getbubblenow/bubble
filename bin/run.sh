#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Run Bubble server or CLI commands. A wrapper for starting a JVM to run Bubble programs.
#
# Usage:    run.sh [debug [debug-port]] [command] [args]
#
# All arguments are optional:
#
#   debug       : if the first argument is the literal string 'debug' then immediately after starting,
#                   the Java process will wait for a debugger to attach. Default is not to enable debugging.
#   debug-port  : the port that will be listened on for the debugger. Default port is 5005
#   command     : the CLI command to run, or 'server' to run BUBBLE API server. Default is to run Bubble API server
#   args        : depends on the command. Use '-h' to request help for a command
#
# Environment variables
#
#   BUBBLE_LISTEN_ALL : if set to true and running a Bubble server, listen on all addresses (bind to 0.0.0.0)
#                         if not set, server will only listen on 127.0.0.1
#   BUBBLE_ENV        : env file to load, used when performing handlebars substitutions on entities marked
#                         with `"_subst": true` JSON attribute. Default is ~/.bubble.env
#   BUBBLE_JVM_OPTS   : Java options. Defaults to either "-Xmx512m -Xms512m" when no command is set, else "-Xmx64m -Xms2m"
#   BUBBLE_JAR        : location of bubble uberjar. Default is to assume there is exactly one bubble-server*.jar file in a
#                         directory named "target" that is in the same directory as this script
#
# Environment variables for API commands
#
#   BUBBLE_API      : which API to use. Default is local (http://127.0.0.1:PORT, where PORT is found in .bubble.env)
#   BUBBLE_USER     : account to use. Default is root@local.local
#   BUBBLE_PASS     : password for account. Default is password
#
#
SCRIPT="${0}"
SCRIPT_DIR="$(cd "$(dirname "${SCRIPT}")" && pwd)"
. "${SCRIPT_DIR}"/bubble_common

# fail on any command error
set -e

function print_auth_env_info () {
  if [[ -z "${BUBBLE_QUIET_AUTH_CACHE}" || "${BUBBLE_QUIET_AUTH_CACHE}" != "true" ]] ; then
    echo 1>&2 "Using default user ${BUBBLE_USER} from ${DEFAULT_USER_LINK}
- Set env var BUBBLE_DISABLE_AUTH_CACHE=true to disable this behavior
- Set env var BUBBLE_QUIET_AUTH_CACHE=true to hide this warning
"
  fi
}

function read_default_user () {
  if [[ -z "${BUBBLE_DISABLE_AUTH_CACHE}" || "${BUBBLE_DISABLE_AUTH_CACHE}" == "false" ]] ; then
    BUBBLE_AUTH="${HOME}/.bubble_auth"
    if [[ -n "${BUBBLE_API}" && -d "${BUBBLE_AUTH}" ]] ; then
      API_HOST="$(echo -n "${BUBBLE_API}" | awk -F '/' '{print $3}')"
      AUTH_DIR="${BUBBLE_AUTH}/${API_HOST}"
      DEFAULT_USER_LINK="${AUTH_DIR}/default"
      if [[ -L "${DEFAULT_USER_LINK}" ]] ; then
        print_auth_env_info
        basename "$(readlink "${DEFAULT_USER_LINK}")"
      fi
    fi
  fi
}

function read_cached_pass () {
  if [[ -z "${BUBBLE_DISABLE_AUTH_CACHE}" || "${BUBBLE_DISABLE_AUTH_CACHE}" == "false" ]] ; then
    BUBBLE_AUTH="${HOME}/.bubble_auth"
    if [[ -n "${BUBBLE_API}" && -d "${BUBBLE_AUTH}" ]] ; then
      API_HOST="$(echo -n "${BUBBLE_API}" | awk -F '/' '{print $3}')"
      AUTH_DIR="${BUBBLE_AUTH}/${API_HOST}"
      PASS_FILE="${AUTH_DIR}/${BUBBLE_USER}"
      if [[ -n "${BUBBLE_USER}" && -f "${PASS_FILE}" ]] ; then
        print_auth_env_info
        cat "${PASS_FILE}" | awk '{$1=$1}1'
      fi
    fi
  fi
}

BASE="$(cd "$(dirname "${0}")" && pwd)"
if [[ $(basename "${BASE}") != "bubble-server" && -d "${BASE}/bubble-server" ]] ; then
  BASE="${BASE}/bubble-server"
fi
if [[ $(basename "${BASE}") == "bin" && -d "${BASE}/../bubble-server" ]] ; then
  BASE="$(cd "${BASE}"/../bubble-server && pwd)"
fi

# save explicitly set key, if we have one
SAVED_DB_KEY=""
if [[ -n "${BUBBLE_DB_ENCRYPTION_KEY}" ]] ; then
  SAVED_DB_KEY="${BUBBLE_DB_ENCRYPTION_KEY}"
fi

if [[ -z "${BUBBLE_ENV}" ]] ; then
  BUBBLE_ENV="${HOME}/.bubble.env"
  if [[ ! -f "${BUBBLE_ENV}" ]] ; then
    BUBBLE_ENV="/home/bubble/api/bubble.env"
  fi
fi
if [[ -f ${BUBBLE_ENV} ]] ; then
  if [[ -z "${BUBBLE_QUIET}" || ${BUBBLE_QUIET} != 1 ]] ; then
    echo 1>&2 "Loading env: ${BUBBLE_ENV}"
  fi
  . ${BUBBLE_ENV}
fi

if [[ -n "${SAVED_DB_KEY}" ]] ; then
  export BUBBLE_DB_ENCRYPTION_KEY="${SAVED_DB_KEY}"
fi

debug="${1}"
if [[ "x${debug}" == "xdebug" ]] ; then
  shift
  ARG_LEN=$(echo -n "${1}" | wc -c)
  ARG_NUMERIC_LEN=$(echo -n "${1}" | tr -dc [:digit:] | wc -c)  # strip all non-digits
  if [[ -n "${ARG_NUMERIC_LEN}" && ${ARG_LEN} -eq ${ARG_NUMERIC_LEN} ]] ; then
    # Second arg is the debug port
    DEBUG_PORT="${1}"
    shift || :
  fi
  if [[ -z "${DEBUG_PORT}" ]] ; then
    DEBUG_PORT=5005
  fi
  debug="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=${DEBUG_PORT}"
else
  debug=""
fi

command="${1}"
server=0
DEFAULT_JVM_OPTS=""
if [[ -z "${command}" ]] ; then
  server=1
  CLASS=bubble.server.BubbleServer
  DEFAULT_JVM_OPTS="-Xmx512m -Xms512m"

else
  CLASS=bubble.main.BubbleMain
  DEFAULT_JVM_OPTS="-Xmx64m -Xms2m"
  shift
fi

if [[ -z "${BUBBLE_JAR}" ]] ; then
  die "API jar file not found"
fi

if [[ -z "${BUBBLE_JVM_OPTS}" ]] ; then
  BUBBLE_JVM_OPTS="${DEFAULT_JVM_OPTS}"
fi
BUBBLE_JVM_OPTS="${BUBBLE_JVM_OPTS} -Djava.net.preferIPv4Stack=true"

# Choose appropriate log config
if [[ ${server} -eq 1 ]] ; then
  LOG_CONFIG="-Dlogback.configurationFile=logback.xml"
  if [[ -f ${BUBBLE_ENV} ]] ; then
    command="${BUBBLE_ENV}"
  fi
else
  LOG_CONFIG="-Dlogback.configurationFile=logback-client.xml"
fi

if [[ -z "${BUBBLE_ADDITIONAL_CLASSPATH}" ]] ; then
  BUBBLE_CP="${BUBBLE_JAR}"
else
  BUBBLE_CP="${BUBBLE_JAR}:${BUBBLE_ADDITIONAL_CLASSPATH}"
fi

# Default user if none set
if [[ -z "${BUBBLE_USER}" ]] ; then
  if [[ -n "${BUBBLE_API}" ]] ; then
    BUBBLE_USER=$(read_default_user)
  fi
  if [[ -z "${BUBBLE_USER}" ]] ; then
    if [[ -n "${REQUIRE_BUBBLE_USER}" ]] ; then
      die "No BUBBLE_USER env var defined"
    fi
    echo 1>&2 "*** Warning: BUBBLE_USER env var was not defined, using default user (probable authentication failure)"
    BUBBLE_USER=root@local.local
  fi
fi

# Default password if none set
if [[ -z "${BUBBLE_PASS}" ]] ; then
  if [[ -n "${BUBBLE_API}" ]] ; then
    BUBBLE_PASS=$(read_cached_pass)
  fi
  if [[ -z "${BUBBLE_PASS}" ]] ; then
    if [[ -n "${REQUIRE_BUBBLE_PASS}" ]] ; then
      die "No BUBBLE_PASS env var defined"
    fi
    echo 1>&2 "*** Warning: BUBBLE_PASS env var was not defined, using default password (probable authentication failure)"
    BUBBLE_PASS=password
  fi
fi

LISTEN_ALL=""
if [[ -n "${BUBBLE_LISTEN_ALL}" && "${BUBBLE_LISTEN_ALL}" == "true" ]] ; then
  LISTEN_ALL="-Dbubble.listenAll=true"
fi

# Run!
BUBBLE_JAR="${BUBBLE_JAR}" java ${LOG_CONFIG} ${BUBBLE_JVM_OPTS} \
  -Xlog:class+load=info:/tmp/bubble_classes_$(date +%s).txt \
  ${debug} -server -cp "${BUBBLE_CP}" ${LISTEN_ALL} ${CLASS} ${command} "${@}"
