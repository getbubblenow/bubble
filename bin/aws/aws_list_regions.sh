#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
aws ec2 describe-regions --filters "Name=opt-in-status,Values=opt-in-not-required" | grep RegionName | cut -d\" -f4 | sort
