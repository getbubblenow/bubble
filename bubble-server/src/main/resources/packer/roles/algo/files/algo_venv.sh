#!/bin/bash
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
cd /root/ansible/roles/algo/algo \
&& python3 -m virtualenv --python="$(command -v python3)" .env \
&& source .env/bin/activate \
&& python3 -m pip install -U pip virtualenv \
&& python3 -m pip install -r requirements.txt
