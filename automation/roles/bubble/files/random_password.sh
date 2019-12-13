#!/bin/bash

file=${1:?no file provided}
owner=${2:?no owner provided}
group=${3:?no group provided}

if [[ ! -f ${file} ]] ; then
  touch ${file} && chmod 660 ${file} && chown ${owner} ${file} && chgrp ${group} ${file} && uuid | tr -d '\n' > ${file}
fi
