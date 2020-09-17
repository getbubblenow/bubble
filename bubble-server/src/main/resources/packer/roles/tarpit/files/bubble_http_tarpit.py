#!/usr/bin/python3
#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Adapted from: https://nullprogram.com/blog/2019/03/22/
#
import asyncio
import random
import os
import sys

import logging
from logging import INFO, DEBUG, WARNING, ERROR, CRITICAL

from pathlib import Path

TARPIT_LOG = '/var/log/bubble/http_tarpit.log'
TARPIT_LOG_LEVEL_FILE = '/home/tarpit/http_tarpit_log_level.txt'
TARPIT_LOG_LEVEL_ENV_VAR = 'HTTP_TARPIT_LOG_LEVEL'
DEFAULT_TARPIT_LOG_LEVEL = 'INFO'
TARPIT_LOG_LEVEL = None

TARPIT_PORT_FILE = '/home/tarpit/http_tarpit_port.txt'
TARPIT_PORT_ENV_VAR = 'HTTP_TARPIT_PORT'
DEFAULT_TARPIT_PORT = '8080'

tarpit_log = logging.getLogger(__name__)

try:
    TARPIT_LOG_LEVEL = Path(TARPIT_LOG_LEVEL_FILE).read_text().strip()
except IOError:
    print('error reading log level from '+TARPIT_LOG_LEVEL_FILE+', checking env var '+TARPIT_LOG_LEVEL_ENV_VAR, file=sys.stderr, flush=True)
    TARPIT_LOG_LEVEL = os.getenv(TARPIT_LOG_LEVEL_ENV_VAR, DEFAULT_TARPIT_LOG_LEVEL)

TARPIT_NUMERIC_LOG_LEVEL = getattr(logging, TARPIT_LOG_LEVEL.upper(), None)
if not isinstance(TARPIT_NUMERIC_LOG_LEVEL, int):
    print('Invalid log level: ' + TARPIT_LOG_LEVEL + ' - using default '+DEFAULT_TARPIT_LOG_LEVEL, file=sys.stderr, flush=True)
    TARPIT_NUMERIC_LOG_LEVEL = getattr(logging, DEFAULT_TARPIT_LOG_LEVEL.upper(), None)

try:
    with open(TARPIT_LOG, 'w+') as f:
        logging.basicConfig(format='%(asctime)s - [%(module)s:%(lineno)d] - %(levelname)s: %(message)s', filename=TARPIT_LOG, level=TARPIT_NUMERIC_LOG_LEVEL)
except IOError:
    logging.basicConfig(format='%(asctime)s - [%(module)s:%(lineno)d] - %(levelname)s: %(message)s', stream=sys.stdout, level=TARPIT_NUMERIC_LOG_LEVEL)

tarpit_log = logging.getLogger(__name__)

if tarpit_log.isEnabledFor(INFO):
    tarpit_log.info('tarpit initialized, default log level = '+logging.getLevelName(TARPIT_NUMERIC_LOG_LEVEL))

TARPIT_PORT = 8080
try:
    TARPIT_PORT = int(Path(TARPIT_PORT_FILE).read_text().strip())
except IOError:
    print('error reading port from '+TARPIT_PORT_FILE+', checking env var '+TARPIT_PORT_ENV_VAR, file=sys.stderr, flush=True)
    TARPIT_PORT = int(os.getenv(TARPIT_PORT_ENV_VAR, DEFAULT_TARPIT_PORT))

TRAP_COUNT = 0


async def handler(_reader, writer):
    global TRAP_COUNT
    TRAP_COUNT = TRAP_COUNT + 1
    peer_addr = writer.get_extra_info('socket').getpeername()[0]
    if tarpit_log.isEnabledFor(INFO):
        tarpit_log.info('trapped '+peer_addr+' - trap count: ' + str(TRAP_COUNT))
    writer.write(b'HTTP/1.1 200 OK\r\n')
    try:
        while True:
            header = random.randint(0, 2**32)
            value = random.randint(0, 2**32)
            await asyncio.sleep(3 + (header % 4))
            writer.write(b'X-WOPR-%x: %x\r\n' % (header, value))
            await writer.drain()
    except ConnectionResetError:
        TRAP_COUNT = TRAP_COUNT - 1
        if tarpit_log.isEnabledFor(INFO):
            tarpit_log.info('dropped '+peer_addr+' - trap count: ' + str(TRAP_COUNT))
        pass


async def main():
    if tarpit_log.isEnabledFor(INFO):
        tarpit_log.info('starting HTTP tarpit on port '+str(TARPIT_PORT))
    server = await asyncio.start_server(handler, '0.0.0.0', TARPIT_PORT)
    async with server:
        await server.serve_forever()

asyncio.run(main())
