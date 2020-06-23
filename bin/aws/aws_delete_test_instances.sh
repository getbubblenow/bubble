#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
#
# Terminate EC2 test compute cloud instances
#

SCRIPT="${0}"
SCRIPT_DIR=$(cd $(dirname ${SCRIPT}) && pwd)
. ${SCRIPT_DIR}/aws_list_test_instances.sh

if [[ ${INSTANCES_COUNT} -gt 0 ]] ; then

  echo "Terminating ${INSTANCES_COUNT} test instances..."
  aws ec2 terminate-instances --instance-ids "$INSTANCES"
  echo "Now instances are terminated and will be deleted after few hours"

else
  echo "No instances contains tag \"${TEST_TAG}\" to delete"
fi
