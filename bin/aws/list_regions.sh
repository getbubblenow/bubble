#!/bin/bash

aws ec2 describe-regions --filters "Name=opt-in-status,Values=opt-in-not-required" | grep RegionName | cut -d\" -f4 | sort
