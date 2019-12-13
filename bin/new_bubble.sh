#!/bin/bash
#
# Launch a new bubble from a sage node
#
# Usage:   new_bubble.sh config-file
#
#   config-file    : a JSON file with parameters to indicate how the bubble should be created
#                    see models/include/new_bubble.json for all parameters
#
#  Minimally required JSON properties:
#    sageFqdn : fully-qualified domain name (FQDN) of a bubble sage. This must be a valid sage node.
#    network  : network name of the bubble to create. This network must *not* already exist.
#
#  For example:
#      {
#        "sageFqdn": "bubble-sage.example.com",
#        "network": "mynetwork"
#      }
#
# Environment variables
#
#   BUBBLE_API     : which API to use. Default is local (http://127.0.0.1:PORT, where PORT is found in .bubble.env)
#   BUBBLE_USER    : account to use. Default is root
#   BUBBLE_PASS    : password for account. Default is root
#   BUBBLE_ENV     : env file to load. Default is ~/.bubble.env or /home/bubble/current/bubble.env (whichever is found first)
#   DEBUG_PORT     : if set, this is the port number the client will wait for a debugger to attach before starting
#   BUBBLE_INCLUDE : when using the sync-model and run-script commands, this is the directory to find included files
#                    For sync-model and migrate-model, the default is the current directory.
#                    For run-script, the default is a directory named "tests" within the current directory
#   BUBBLE_SCRIPTS : location of run.sh script. Default is to assume it is in the same directory containing this script
#
SCRIPT="${0}"
SCRIPT_DIR=$(cd $(dirname ${SCRIPT}) && pwd)
. ${SCRIPT_DIR}/bubble_common

CONFIG_JSON="${1:?no config json provided}"
shift

cat ${CONFIG_JSON} | exec ${SCRIPT_DIR}/bscript ${SCRIPT_DIR}/../scripts/new_bubble.json ${@} --call-include --vars -
