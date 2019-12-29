#!/bin/bash
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
#   BUBBLE_ENV      : env file to load, used when performing handlebars substitutions on entities marked
#                        with `"_subst": true` JSON attribute. Default is ~/.bubble.env
#   BUBBLE_JVM_OPTS : Java options. Defaults to "-Xmx4096 -Xms4096"
#   BUBBLE_JAR      : location of bubble uberjar. Default is to assume there is exactly one bubble-server*.jar file in a
#                       directory named "target" that is in the same directory as this script
#
# Environment variables for API commands
#
#   BUBBLE_API      : which API to use. Default is local (http://127.0.0.1:PORT, where PORT is found in .bubble.env)
#   BUBBLE_USER     : account to use. Default is root
#   BUBBLE_PASS     : password for account. Default is root
#
#
SCRIPT="${0}"
SCRIPT_DIR=$(cd $(dirname ${SCRIPT}) && pwd)
. ${SCRIPT_DIR}/bubble_common

# fail on any command error
set -e

BASE=$(cd $(dirname $0) && pwd)
if [[ $(basename ${BASE}) != "bubble-server" && -d "${BASE}/bubble-server" ]] ; then
  BASE="${BASE}/bubble-server"
fi
if [[ $(basename ${BASE}) == "bin" && -d "${BASE}/../bubble-server" ]] ; then
  BASE="$(cd ${BASE}/../bubble-server && pwd)"
fi

# save explicitly set key, if we have one
SAVED_DB_KEY=""
if [[ ! -z "${BUBBLE_DB_ENCRYPTION_KEY}" ]] ; then
  SAVED_DB_KEY="${BUBBLE_DB_ENCRYPTION_KEY}"
fi

if [[ -z "${BUBBLE_ENV}" ]] ; then
  BUBBLE_ENV="${HOME}/.bubble.env"
  if [[ ! -f "${BUBBLE_ENV}" ]] ; then
    BUBBLE_ENV="/home/bubble/current/bubble.env"
  fi
fi
if [[ -f ${BUBBLE_ENV} ]] ; then
  if [[ -z "${BUBBLE_QUIET}" || ${BUBBLE_QUIET} != 1 ]] ; then
    echo 1>&2 "Loading env: ${BUBBLE_ENV}"
  fi
  . ${BUBBLE_ENV}
fi

if [[ ! -z "${SAVED_DB_KEY}" ]] ; then
  export BUBBLE_DB_ENCRYPTION_KEY="${SAVED_DB_KEY}"
fi

debug="${1}"
if [[ "x${debug}" == "xdebug" ]] ; then
  shift
  ARG_LEN=$(echo -n "${1}" | wc -c)
  ARG_NUMERIC_LEN=$(echo -n "${1}" | tr -dc [:digit:] | wc -c)  # strip all non-digits
  if [[ ! -z "${ARG_NUMERIC_LEN}" && ${ARG_LEN} -eq ${ARG_NUMERIC_LEN} ]] ; then
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
  DEFAULT_JVM_OPTS="-Xmx256m -Xms64m"
  shift
fi

if [[ -z "${BUBBLE_JAR}" ]] ; then
  die "API jar file not found in ${BASE}/target"
fi

if [[ -z "${BUBBLE_JVM_OPTS}" ]] ; then
  BUBBLE_JVM_OPTS="${DEFAULT_JVM_OPTS}"
fi
BUBBLE_JVM_OPTS="${BUBBLE_JVM_OPTS} -Djava.net.preferIPv4Stack=true"

# Choose appropriate log config
if [[ ${server} -eq 1 ]] ; then
  LOG_CONFIG="-Dlogback.configurationFile=logback.xml"
else
  LOG_CONFIG="-Dlogback.configurationFile=logback-client.xml"
fi

# Run!
BUBBLE_JAR="${BUBBLE_JAR}" java ${LOG_CONFIG} ${BUBBLE_JVM_OPTS} ${debug} -server -cp ${BUBBLE_JAR} ${CLASS} ${command} "${@}"
