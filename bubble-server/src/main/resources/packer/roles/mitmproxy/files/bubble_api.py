#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
import asyncio
import json
import logging
import re
import subprocess
import time
import traceback
import uuid
from http import HTTPStatus
from logging import INFO, DEBUG, WARNING, ERROR

import httpx
import nest_asyncio
import redis
from bubble_vpn4 import wireguard_network_ipv4
from bubble_vpn6 import wireguard_network_ipv6
from netaddr import IPAddress, IPNetwork

from bubble_config import bubble_port, debug_capture_fqdn, \
    bubble_host, bubble_host_alias, bubble_sage_host, bubble_sage_ip4, bubble_sage_ip6
from mitmproxy import http
from mitmproxy.net.http import headers as nheaders

bubble_log = logging.getLogger(__name__)

nest_asyncio.apply()

HEADER_USER_AGENT = 'User-Agent'
HEADER_CONTENT_LENGTH = 'Content-Length'
HEADER_CONTENT_TYPE = 'Content-Type'
HEADER_CONTENT_ENCODING = 'Content-Encoding'
HEADER_TRANSFER_ENCODING = 'Transfer-Encoding'
HEADER_LOCATION = 'Location'
HEADER_CONTENT_SECURITY_POLICY = 'Content-Security-Policy'
HEADER_REFERER = 'Referer'
HEADER_FILTER_PASSTHRU = 'X-Bubble-Passthru'

CTX_BUBBLE_MATCHERS = 'X-Bubble-Matchers'
CTX_BUBBLE_ABORT = 'X-Bubble-Abort'
CTX_BUBBLE_SPECIAL = 'X-Bubble-Special'
CTX_BUBBLE_LOCATION = 'X-Bubble-Location'
CTX_BUBBLE_PASSTHRU = 'X-Bubble-Passthru'
CTX_BUBBLE_REQUEST_ID = 'X-Bubble-RequestId'
CTX_CONTENT_LENGTH = 'X-Bubble-Content-Length'
CTX_CONTENT_LENGTH_SENT = 'X-Bubble-Content-Length-Sent'
CTX_BUBBLE_FILTERED = 'X-Bubble-Filtered'
CTX_BUBBLE_FLEX = 'X-Bubble-Flex'
BUBBLE_URI_PREFIX = '/__bubble/'

HEADER_HEALTH_CHECK = 'X-Mitm-Health'
HEALTH_CHECK_URI = BUBBLE_URI_PREFIX + '__mitm_health'

REDIS = redis.Redis(host='127.0.0.1', port=6379, db=0)
BUBBLE_ACTIVITY_LOG_PREFIX = 'bubble_activity_log_'
BUBBLE_ACTIVITY_LOG_EXPIRATION = 600

LOCAL_IPS = []
for local_ip in subprocess.check_output(['hostname', '-I']).split():
    LOCAL_IPS.append(local_ip.decode())


VPN_IP4_CIDR = IPNetwork(wireguard_network_ipv4)
VPN_IP6_CIDR = IPNetwork(wireguard_network_ipv6)

# This regex extracts splits the host header into host and port.
# Handles the edge case of IPv6 addresses containing colons.
# https://bugzilla.mozilla.org/show_bug.cgi?id=45891
parse_host_header = re.compile(r"^(?P<host>[^:]+|\[.+\])(?::(?P<port>\d+))?$")


def status_reason(status_code):
    return HTTPStatus(status_code).phrase


def redis_set(name, value, ex):
    REDIS.set(name, value, nx=True, ex=ex)
    REDIS.set(name, value, xx=True, ex=ex)


def bubble_activity_log(client_addr, server_addr, event, data):
    key = BUBBLE_ACTIVITY_LOG_PREFIX + str(time.time() * 1000.0) + '_' + str(uuid.uuid4())
    value = json.dumps({
        'source': 'mitmproxy',
        'client_addr': client_addr,
        'server_addr': server_addr,
        'event': event,
        'data': str(data)
    })
    if bubble_log.isEnabledFor(DEBUG):
        bubble_log.debug('bubble_activity_log: setting '+key+' = '+value)
    redis_set(key, value, BUBBLE_ACTIVITY_LOG_EXPIRATION)
    pass


def async_client(proxies=None,
                 timeout=5,
                 max_redirects=0):
    return httpx.AsyncClient(timeout=timeout, max_redirects=max_redirects, proxies=proxies)


async def async_response(client, name, url,
                         headers=None,
                         method='GET',
                         data=None,
                         json=None):
    if bubble_log.isEnabledFor(INFO):
        bubble_log.info('bubble_async_request(' + name + '): starting async: ' + method + ' ' + url)

    response = await client.request(method=method, url=url, headers=headers, json=json, data=data)

    if bubble_log.isEnabledFor(INFO):
        bubble_log.info('bubble_async_request(' + name + '): async request returned HTTP status ' + str(response.status_code))

    if response.status_code != 200:
        if bubble_log.isEnabledFor(ERROR):
            bubble_log.error('bubble_async_request(' + name + '): API call failed ('+url+'): ' + repr(response))

    return response


def async_stream(client, name, url,
                 headers=None,
                 method='GET',
                 data=None,
                 json=None,
                 timeout=5,
                 max_redirects=0,
                 loop=asyncio.get_running_loop()):
    try:
        return loop.run_until_complete(_async_stream(client, name, url,
                                                     headers=headers,
                                                     method=method,
                                                     data=data,
                                                     json=json,
                                                     timeout=timeout,
                                                     max_redirects=max_redirects))
    except Exception as e:
        bubble_log.error('async_stream('+name+'): error with url='+url+' -- '+repr(e))
        raise e


async def _async_stream(client, name, url,
                        headers=None,
                        method='GET',
                        data=None,
                        json=None,
                        timeout=5,
                        max_redirects=0):
    request = client.build_request(method=method, url=url, headers=headers, json=json, data=data)
    return await client.send(request, stream=True, allow_redirects=(max_redirects > 0), timeout=timeout)


async def _bubble_async(name, url,
                        headers=None,
                        method='GET',
                        data=None,
                        json=None,
                        proxies=None,
                        timeout=5,
                        max_redirects=0):
    async with async_client(proxies=proxies, timeout=timeout, max_redirects=max_redirects) as client:
        return await async_response(client, name, url, headers=headers, method=method, data=data, json=json)


def bubble_async(name, url,
                 headers=None,
                 method='GET',
                 data=None,
                 json=None,
                 proxies=None,
                 timeout=5,
                 max_redirects=0,
                 loop=asyncio.get_running_loop()):
    try:
        return loop.run_until_complete(_bubble_async(name, url,
                                                     headers=headers,
                                                     method=method,
                                                     data=data,
                                                     json=json,
                                                     proxies=proxies,
                                                     timeout=timeout,
                                                     max_redirects=max_redirects))
    except Exception as e:
        bubble_log.error('bubble_async('+name+'): error: '+repr(e))


def bubble_async_request_json(name, url, headers, method='GET', json=None):
    response = bubble_async(name, url, headers, method=method, json=json)
    if response and response.status_code == 200:
        return response.json()
    else:
        if bubble_log.isEnabledFor(DEBUG):
            bubble_log.debug('bubble_async_request_json('+name+'): received invalid HTTP status: '+str(response.status_code))
    return None


def bubble_conn_check(client_addr, server_addr, fqdns, security_level):
    if debug_capture_fqdn and fqdns:
        for f in debug_capture_fqdn:
            if f in fqdns:
                if bubble_log.isEnabledFor(DEBUG):
                    bubble_log.debug('bubble_conn_check: debug_capture_fqdn detected, returning noop: '+f)
                return 'noop'

    name = 'bubble_conn_check'
    url = 'http://127.0.0.1:'+bubble_port+'/api/filter/check'
    headers = {
        'X-Forwarded-For': client_addr,
        'Accept': 'application/json',
        'Content-Type': 'application/json'
    }
    data = {
        'serverAddr': str(server_addr),
        'fqdns': fqdns,
        'clientAddr': client_addr
    }
    try:
        return bubble_async_request_json(name, url, headers, method='POST', json=data)

    except Exception as e:
        if bubble_log.isEnabledFor(ERROR):
            bubble_log.error('bubble_conn_check: API call failed: '+repr(e))
        traceback.print_exc()
        if security_level is not None and security_level['level'] == 'maximum':
            return False
        return None


def bubble_get_flex_router(client_addr, host):
    name = 'bubble_get_flex_router'
    url = 'http://127.0.0.1:' + bubble_port + '/api/filter/flexRouters/' + host
    headers = {
        'X-Forwarded-For': client_addr,
        'Accept': 'application/json'
    }
    try:
        return bubble_async_request_json(name, url, headers)

    except Exception as e:
        if bubble_log.isEnabledFor(ERROR):
            bubble_log.error('bubble_get_flex_routes: API call failed with exception: '+repr(e))
        traceback.print_exc()
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
        if bubble_log.isEnabledFor(INFO):
            bubble_log.info('bubble_matchers: debug_capture_fqdn detected, returning DEBUG_MATCHER: '+host)
        return DEBUG_MATCHER

    name = 'bubble_matchers'
    url = 'http://127.0.0.1:'+bubble_port+'/api/filter/matchers/'+req_id
    headers = {
        'X-Forwarded-For': client_addr,
        'Accept': 'application/json',
        'Content-Type': 'application/json'
    }
    if HEADER_USER_AGENT not in flow.request.headers:
        if bubble_log.isEnabledFor(WARNING):
            bubble_log.warning('bubble_matchers: no User-Agent header, setting to UNKNOWN')
        user_agent = 'UNKNOWN'
    else:
        user_agent = flow.request.headers[HEADER_USER_AGENT]

    if HEADER_REFERER not in flow.request.headers:
        if bubble_log.isEnabledFor(DEBUG):
            bubble_log.debug('bubble_matchers: no Referer header, setting to NONE')
        referer = 'NONE'
    else:
        try:
            referer = flow.request.headers[HEADER_REFERER].encode().decode()
        except Exception as e:
            if bubble_log.isEnabledFor(WARNING):
                bubble_log.warning('bubble_matchers: error parsing Referer header: '+repr(e))
            referer = 'NONE'

    data = {
        'requestId': req_id,
        'fqdn': host,
        'uri': flow.request.path,
        'userAgent': user_agent,
        'referer': referer,
        'clientAddr': client_addr,
        'serverAddr': server_addr
    }

    try:
        response = bubble_async(name, url, headers=headers, method='POST', json=data)
        if response.status_code == 200:
            return response.json()
        elif response.status_code == 403:
            if bubble_log.isEnabledFor(DEBUG):
                bubble_log.debug('bubble_matchers: response was FORBIDDEN, returning block: '+str(response.status_code)+' / '+repr(response.text))
            return BLOCK_MATCHER
        if bubble_log.isEnabledFor(WARNING):
            bubble_log.warning('bubble_matchers: response not OK, returning empty matchers array: '+str(response.status_code)+' / '+repr(response.text))
    except Exception as e:
        if bubble_log.isEnabledFor(ERROR):
            bubble_log.error('bubble_matchers: API call failed: '+repr(e))
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


def is_bubble_request(ip, fqdns):
    # return ip in LOCAL_IPS
    return ip in LOCAL_IPS and (bubble_host in fqdns or bubble_host_alias in fqdns)


def is_bubble_special_path(path):
    return path and path.startswith(BUBBLE_URI_PREFIX)


def make_bubble_special_path(path):
    return 'http://127.0.0.1:' + bubble_port + '/' + path[len(BUBBLE_URI_PREFIX):]


def is_bubble_health_check(path):
    return path and path.startswith(HEALTH_CHECK_URI)


def is_sage_request(ip, fqdns):
    return (ip == bubble_sage_ip4 or ip == bubble_sage_ip6) and bubble_sage_host in fqdns


def is_not_from_vpn(client_addr):
    ip = IPAddress(client_addr)
    return ip not in VPN_IP4_CIDR and ip not in VPN_IP6_CIDR


def is_flex_domain(client_addr, fqdn):
    if fqdn == bubble_host or fqdn == bubble_host_alias or fqdn == bubble_sage_host:
        if bubble_log.isEnabledFor(DEBUG):
            bubble_log.debug('is_flex_domain: (early) returning False for: '+fqdn)
        return False
    check_fqdn = fqdn

    exclusion_set = 'flexExcludeLists~' + client_addr + '~UNION'
    excluded = REDIS.sismember(exclusion_set, fqdn)
    if excluded:
        if bubble_log.isEnabledFor(DEBUG):
            bubble_log.debug('is_flex_domain: returning False for excluded flex domain: ' + fqdn + ' (check=' + check_fqdn + ')')
        return False

    flex_set = 'flexLists~' + client_addr + '~UNION'
    while '.' in check_fqdn:
        found = REDIS.sismember(flex_set, check_fqdn)
        if found:
            if bubble_log.isEnabledFor(DEBUG):
                bubble_log.debug('is_flex_domain: returning True for: '+fqdn+' (check='+check_fqdn+')')
            return True
        check_fqdn = check_fqdn[check_fqdn.index('.')+1:]
    if bubble_log.isEnabledFor(DEBUG):
        bubble_log.debug('is_flex_domain: (early) returning False for: '+fqdn)
    return False


def original_flex_ip(client_addr, fqdns):
    for fqdn in fqdns:
        ip = REDIS.get("flexOriginal~"+client_addr+"~"+fqdn)
        if ip is not None:
            return ip.decode()
    return None


def health_check_response(flow):
    # if bubble_log.isEnabledFor(DEBUG):
    #     bubble_log.debug('health_check_response: special bubble health check request, responding with OK')
    response_headers = nheaders.Headers()
    response_headers[HEADER_HEALTH_CHECK] = 'OK'
    response_headers[HEADER_CONTENT_LENGTH] = '3'
    if flow.response is None:
        flow.response = http.HTTPResponse(http_version='HTTP/1.1',
                                          status_code=200,
                                          reason='OK',
                                          headers=response_headers,
                                          content=b'OK\n')
    else:
        flow.response.headers = nheaders.Headers()
        flow.response.headers = response_headers
        flow.response.status_code = 200
        flow.response.reason = 'OK'
        flow.response.stream = lambda chunks: [b'OK\n']


def special_bubble_response(flow):
    name = 'special_bubble_response'
    path = flow.request.path
    if is_bubble_health_check(path):
        health_check_response(flow)
        return
    uri = make_bubble_special_path(path)
    if bubble_log.isEnabledFor(DEBUG):
        bubble_log.debug('special_bubble_response: sending special bubble request to '+uri)
    headers = {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
    }
    if flow.request.method == 'GET':
        response = bubble_async(name, uri, headers=headers)

    elif flow.request.method == 'POST':
        if bubble_log.isEnabledFor(DEBUG):
            bubble_log.debug('special_bubble_response: special bubble request: POST content is '+str(flow.request.content))
        if flow.request.content:
            headers['Content-Length'] = str(len(flow.request.content))
        response = bubble_async(name, uri, json=flow.request.content, headers=headers)

    else:
        if bubble_log.isEnabledFor(WARNING):
            bubble_log.warning('special_bubble_response: special bubble request: method '+flow.request.method+' not supported')
        return

    if flow.response is None:
        http_version = response.http_version
        response_headers = collect_response_headers(response)
        flow.response = http.HTTPResponse(http_version=http_version,
                                          status_code=response.status_code,
                                          reason=response.reason,
                                          headers=response_headers,
                                          content=None)
    if response is not None:
        # if bubble_log.isEnabledFor(DEBUG):
        #     bubble_log.debug('special_bubble_response: special bubble request: response status = '+str(response.status_code))
        flow.response.headers = collect_response_headers(response)
        flow.response.status_code = response.status_code
        flow.response.reason = status_reason(response.status_code)
        flow.response.stream = lambda chunks: send_bubble_response(response)


def send_bubble_response(response):
    for chunk in response.iter_content(8192):
        yield chunk


def collect_response_headers(response, omit=None):
    response_headers = nheaders.Headers()
    for name in response.headers:
        if omit and name in omit:
            continue
        response_headers[name] = response.headers[name]
    return response_headers
