#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
#
PGPASSWORD="$(cat /home/bubble/.BUBBLE_PG_PASSWORD)" psql -U bubble -h 127.0.0.1 bubble "${@}"
