#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
#
# List test Amazon EC2 instances
#
# Environment variables
#
#   BUBBLE_ENV        : env file to load. Default is ~/.bubble-test.env or /home/bubble/api/bubble-test.env (whichever is found first)
#   AWS_ACCESS_KEY_ID : AWS access key ID
#   AWS_SECRET_KEY    : AWS secret access key

SCRIPT="${0}"
SCRIPT_DIR=$(cd $(dirname ${SCRIPT}) && pwd)

if [[ -z "${BUBBLE_ENV}" ]] ; then
  if [[ -f "${HOME}/.bubble-test.env" ]] ; then
    BUBBLE_ENV="${HOME}/.bubble-test.env"
  elif [[ -f "/home/bubble/api/.bubble-test.env" ]] ; then
    BUBBLE_ENV="/home/bubble/api/.bubble-test.env"
  else
    die "bubble environment file not found"
  fi
fi

if [[ -z "${TEST_TAG}" ]] ; then
    TEST_TAG="$(cat ${BUBBLE_ENV} | egrep -v '\s*#' | grep TEST_TAG_CLOUD | awk -F '=' '{print $NF}' | tr -d "'" | tr -d '"')"
    if [[ -z "${TEST_TAG}" ]] ; then
      die "Error reading TEST_TAG_CLOUD from ${BUBBLE_ENV}"
    fi
fi

INSTANCES=$(aws ec2 describe-instances --filters "Name=tag:test_instance,Values=${TEST_TAG}" --query "Reservations[].Instances[].InstanceId")

((INSTANCES_COUNT=$(echo "${INSTANCES}" | wc -l) - 2))

if [[ ${INSTANCES_COUNT} -gt 0 ]] ; then

  echo "Found ${INSTANCES_COUNT} test instances contain tag ${TEST_TAG}"
  echo "$INSTANCES"

else
  echo "No instances contain tag \"${TEST_TAG}\" found"
fi
