#!/bin/bash
cd /root/ansible/roles/algo/algo \
&& python3 -m virtualenv --python="$(command -v python3)" .env \
&& source .env/bin/activate \
&& python3 -m pip install -U pip virtualenv \
&& python3 -m pip install -r requirements.txt
