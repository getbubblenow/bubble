#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
import requests
import traceback
import sys
import os
import time
import uuid
import datetime
import redis
import json
from bubble_config import bubble_network, bubble_port

# Write python PID to file so that mitmdump_monitor.sh can check for excessive memory usage and restart if needed
MITMDUMP_PID_FILE_PATH = '/home/mitmproxy/mitmdump.pid'
MITMDUMP_PID_FILE = open(MITMDUMP_PID_FILE_PATH, "w")
MITMDUMP_PID_FILE.write("%d" % os.getpid())
MITMDUMP_PID_FILE.close()

HEADER_USER_AGENT = 'User-Agent'
HEADER_REFERER = 'Referer'

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
        'data': data
    })
    bubble_log('bubble_activity_log: setting '+key+' = '+value)
    redis_set(key, value, BUBBLE_ACTIVITY_LOG_EXPIRATION)
    pass


def bubble_passthru(remote_addr, addr, fqdn):
    headers = {
        'X-Forwarded-For': remote_addr,
        'Accept' : 'application/json',
        'Content-Type': 'application/json'
    }
    try:
        data = {
            'addr': str(addr),
            'fqdn': str(fqdn),
            'remoteAddr': remote_addr
        }
        response = requests.post('http://127.0.0.1:'+bubble_port+'/api/filter/passthru', headers=headers, json=data)
        return response.ok
    except Exception as e:
        bubble_log('bubble_passthru API call failed: '+repr(e))
        traceback.print_exc()
    return False


def bubble_matchers(req_id, remote_addr, flow, host):
    headers = {
        'X-Forwarded-For': remote_addr,
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
            'remoteAddr': remote_addr
        }
        response = requests.post('http://127.0.0.1:'+bubble_port+'/api/filter/matchers/'+req_id, headers=headers, json=data)
        if response.ok:
            return response.json()
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
