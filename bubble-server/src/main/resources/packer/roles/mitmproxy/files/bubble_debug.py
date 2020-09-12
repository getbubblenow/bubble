#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
import logging
from logging import INFO, DEBUG, WARNING, ERROR, CRITICAL

import os
import threading
import traceback
import signal
import sys

from pathlib import Path

BUBBLE_PORT_ENV_VAR = 'BUBBLE_PORT'
BUBBLE_PORT = os.getenv(BUBBLE_PORT_ENV_VAR)
if BUBBLE_PORT is None:
    BUBBLE_PORT = '(no '+BUBBLE_PORT_ENV_VAR+' env var found)'

BUBBLE_LOG = '/var/log/bubble/mitmproxy_bubble.log'
BUBBLE_LOG_LEVEL_FILE = '/home/mitmproxy/bubble_log_level.txt'
BUBBLE_LOG_LEVEL_ENV_VAR = 'BUBBLE_LOG_LEVEL'
DEFAULT_BUBBLE_LOG_LEVEL = 'WARNING'
BUBBLE_LOG_LEVEL = None
try:
    BUBBLE_LOG_LEVEL = Path(BUBBLE_LOG_LEVEL_FILE).read_text().strip()
except IOError:
    print('error reading log level from '+BUBBLE_LOG_LEVEL_FILE+', checking env var '+BUBBLE_LOG_LEVEL_ENV_VAR, file=sys.stderr, flush=True)
    BUBBLE_LOG_LEVEL = os.getenv(BUBBLE_LOG_LEVEL_ENV_VAR, DEFAULT_BUBBLE_LOG_LEVEL)

BUBBLE_NUMERIC_LOG_LEVEL = getattr(logging, BUBBLE_LOG_LEVEL.upper(), None)
if not isinstance(BUBBLE_NUMERIC_LOG_LEVEL, int):
    print('Invalid log level: ' + BUBBLE_LOG_LEVEL + ' - using default '+DEFAULT_BUBBLE_LOG_LEVEL, file=sys.stderr, flush=True)
    BUBBLE_NUMERIC_LOG_LEVEL = getattr(logging, DEFAULT_BUBBLE_LOG_LEVEL.upper(), None)
logging.basicConfig(format='[mitm'+BUBBLE_PORT+'] %(asctime)s - [%(module)s:%(lineno)d] - %(levelname)s: %(message)s', filename=BUBBLE_LOG, level=BUBBLE_NUMERIC_LOG_LEVEL)

bubble_log = logging.getLogger(__name__)


# Allow SIGUSR1 to print stack traces to stderr
def dumpstacks(signal, frame):
    id2name = dict([(th.ident, th.name) for th in threading.enumerate()])
    code = []
    for threadId, stack in sys._current_frames().items():
        code.append("\n# Thread: %s(%d)" % (id2name.get(threadId,""), threadId))
        for filename, lineno, name, line in traceback.extract_stack(stack):
            code.append('File: "%s", line %d, in %s' % (filename, lineno, name))
            if line:
                code.append("  %s" % (line.strip()))
    print("\n------------------------------------- stack traces ------------------------------"+"\n".join(code), file=sys.stderr, flush=True)


signal.signal(signal.SIGUSR1, dumpstacks)

if bubble_log.isEnabledFor(INFO):
    bubble_log.info('debug module initialized, default log level = '+logging.getLevelName(BUBBLE_NUMERIC_LOG_LEVEL))
