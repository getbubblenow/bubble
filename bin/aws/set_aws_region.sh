#!/bin/bash

function die {
  echo 1>&2 "${1}"
  exit 1
}

region=${1:?no region specified}
if [[ ! -f ~/.aws/config.${region} ]] ; then
  die "Region not found: ${region}"
fi
rm -f ~/.aws/config && ln -s ~/.aws/config.${region} ~/.aws/config
