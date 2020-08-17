#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
import requests
import traceback
import re
import sys
import time
import uuid
import datetime
import redis
import json
from bubble_config import bubble_network, bubble_port, debug_capture_fqdn

HEADER_USER_AGENT = 'User-Agent'
HEADER_CONTENT_SECURITY_POLICY = 'Content-Security-Policy'
HEADER_REFERER = 'Referer'
HEADER_FILTER_PASSTHRU = 'X-Bubble-Passthru'

CTX_BUBBLE_MATCHERS='X-Bubble-Matchers'
CTX_BUBBLE_ABORT='X-Bubble-Abort'
CTX_BUBBLE_PASSTHRU='X-Bubble-Passthru'
CTX_BUBBLE_REQUEST_ID='X-Bubble-RequestId'
CTX_CONTENT_LENGTH='X-Bubble-Content-Length'
CTX_CONTENT_LENGTH_SENT='X-Bubble-Content-Length-Sent'
BUBBLE_URI_PREFIX='/__bubble/'

REDIS = redis.Redis(host='127.0.0.1', port=6379, db=0)
BUBBLE_ACTIVITY_LOG_PREFIX = 'bubble_activity_log_'
BUBBLE_ACTIVITY_LOG_EXPIRATION = 600

# This regex extracts splits the host header into host and port.
# Handles the edge case of IPv6 addresses containing colons.
# https://bugzilla.mozilla.org/show_bug.cgi?id=45891
parse_host_header = re.compile(r"^(?P<host>[^:]+|\[.+\])(?::(?P<port>\d+))?$")


def redis_set(name, value, ex):
    REDIS.set(name, value, nx=True, ex=ex)
    REDIS.set(name, value, xx=True, ex=ex)


def bubble_log(message):
    print(str(datetime.datetime.time(datetime.datetime.now()))+': ' + message, file=sys.stderr)


def bubble_activity_log(client_addr, server_addr, event, data):
    key = BUBBLE_ACTIVITY_LOG_PREFIX + str(time.time() * 1000.0) + '_' + str(uuid.uuid4())
    value = json.dumps({
        'source': 'mitmproxy',
        'client_addr': client_addr,
        'server_addr': server_addr,
        'event': event,
        'data': str(data)
    })
    bubble_log('bubble_activity_log: setting '+key+' = '+value)
    redis_set(key, value, BUBBLE_ACTIVITY_LOG_EXPIRATION)
    pass


def bubble_conn_check(remote_addr, addr, fqdns, security_level):
    if debug_capture_fqdn and fqdns:
        for f in debug_capture_fqdn:
            if f in fqdns:
                bubble_log('bubble_conn_check: debug_capture_fqdn detected, returning noop: '+f)
                return 'noop'

    headers = {
        'X-Forwarded-For': remote_addr,
        'Accept' : 'application/json',
        'Content-Type': 'application/json'
    }
    try:
        data = {
            'addr': str(addr),
            'fqdns': fqdns,
            'remoteAddr': remote_addr
        }
        response = requests.post('http://127.0.0.1:'+bubble_port+'/api/filter/check', headers=headers, json=data)
        if response.ok:
            return response.json()
        bubble_log('bubble_conn_check API call failed: '+repr(response))
        return None

    except Exception as e:
        bubble_log('bubble_conn_check API call failed: '+repr(e))
        traceback.print_exc()
        if security_level is not None and security_level['level'] == 'maximum':
            return False
        return None


DEBUG_MATCHER_NAME = 'DebugCaptureMatcher'
DEBUG_MATCHER = {
    'decision': 'match',
    'matchers': [{
        'name': DEBUG_MATCHER_NAME,
        'contentTypeRegex': '.*',
        "urlRegex": ".*",
        'rule': DEBUG_MATCHER_NAME
    }]
}
BLOCK_MATCHER = {
    'decision': 'abort_not_found',
    'matchers': [{
        'name': 'BLOCK_MATCHER',
        'contentTypeRegex': '.*',
        "urlRegex": ".*",
        'rule': 'BLOCK_MATCHER'
    }]
}


def bubble_matchers(req_id, client_addr, server_addr, flow, host):
    if debug_capture_fqdn and host and host in debug_capture_fqdn:
        bubble_log('bubble_matchers: debug_capture_fqdn detected, returning DEBUG_MATCHER: '+host)
        return DEBUG_MATCHER

    headers = {
        'X-Forwarded-For': client_addr,
        'Accept' : 'application/json',
        'Content-Type': 'application/json'
    }
    if HEADER_USER_AGENT not in flow.request.headers:
        bubble_log('bubble_matchers: no User-Agent header, setting to UNKNOWN')
        user_agent = 'UNKNOWN'
    else:
        user_agent = flow.request.headers[HEADER_USER_AGENT]

    if HEADER_REFERER not in flow.request.headers:
        bubble_log('bubble_matchers: no Referer header, setting to NONE')
        referer = 'NONE'
    else:
        try:
            referer = flow.request.headers[HEADER_REFERER].encode().decode()
        except Exception as e:
            bubble_log('bubble_matchers: error parsing Referer header: '+repr(e))
            referer = 'NONE'

    try:
        data = {
            'requestId': req_id,
            'fqdn': host,
            'uri': flow.request.path,
            'userAgent': user_agent,
            'referer': referer,
            'clientAddr': client_addr,
            'serverAddr': server_addr
        }
        response = requests.post('http://127.0.0.1:'+bubble_port+'/api/filter/matchers/'+req_id, headers=headers, json=data)
        if response.ok:
            return response.json()
        elif response.status_code == 403:
            bubble_log('bubble_matchers response was FORBIDDEN, returning block: '+str(response.status_code)+' / '+repr(response.text))
            return BLOCK_MATCHER
        bubble_log('bubble_matchers response not OK, returning empty matchers array: '+str(response.status_code)+' / '+repr(response.text))
    except Exception as e:
        bubble_log('bubble_matchers API call failed: '+repr(e))
        traceback.print_exc()
    return None

def add_flow_ctx(flow, name, value):
    if not hasattr(flow, 'bubble_ctx'):
        flow.bubble_ctx = {}
    flow.bubble_ctx[name] = value

def get_flow_ctx(flow, name):
    if not hasattr(flow, 'bubble_ctx'):
        return None
    if not name in flow.bubble_ctx:
        return None
    return flow.bubble_ctx[name]
