#!/bin/bash
PGPASSWORD="$(cat /home/bubble/.BUBBLE_PG_PASSWORD)" psql -U bubble -h 127.0.0.1 bubble "${@}"
