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

from bubble_debug import get_stack
from bubble_config import bubble_port, debug_capture_fqdn, \
    bubble_host, bubble_host_alias, bubble_sage_host, bubble_sage_ip4, bubble_sage_ip6
from mitmproxy import http
from mitmproxy.net.http import headers as nheaders
from mitmproxy.proxy.protocol.async_stream_body import AsyncStreamBody
from mitmproxy.utils import strutils

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

TARPIT_PORT = 8080

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
        bubble_log.error('async_stream('+name+'): error with url='+url+' -- '+repr(e)+' from '+get_stack(e))
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
                        client=None,
                        headers=None,
                        method='GET',
                        data=None,
                        json=None,
                        proxies=None,
                        timeout=5,
                        max_redirects=0):
    if client is not None:
        return await async_response(client, name, url, headers=headers, method=method, data=data, json=json)
    else:
        async with async_client(proxies=proxies, timeout=timeout, max_redirects=max_redirects) as client:
            return await async_response(client, name, url, headers=headers, method=method, data=data, json=json)


def bubble_async(name, url,
                 client=None,
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
                                                     client=client,
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
    response = bubble_async(name, url, headers=headers, method=method, json=json)
    if response and response.status_code == 200:
        return response.json()
    elif response:
        if bubble_log.isEnabledFor(DEBUG):
            bubble_log.debug('bubble_async_request_json('+name+'): received invalid HTTP status: '+repr(response.status_code))
    else:
        if bubble_log.isEnabledFor(DEBUG):
            bubble_log.debug('bubble_async_request_json('+name+'): error, no response')
    return None


def cleanup_async(url, loop, client, response):
    def cleanup():
        errors = False
        if response is not None:
            try:
                loop.run_until_complete(response.aclose())
            except Exception as e:
                bubble_log.error('cleanup_async: error closing response: '+repr(e))
                errors = True
        if client is not None:
            try:
                loop.run_until_complete(client.aclose())
            except Exception as e:
                bubble_log.error('cleanup_async: error closing client: '+repr(e))
                errors = True
        if loop is not None:
            try:
                loop.close()
            except Exception as e:
                bubble_log.error('cleanup_async: error closing loop: '+repr(e))
                errors = True
        if not errors:
            if bubble_log.isEnabledFor(DEBUG):
                bubble_log.debug('cleanup_async: successfully completed: '+url)
        else:
            if bubble_log.isEnabledFor(WARNING):
                bubble_log.warning('cleanup_async: successfully completed (but had errors closing): ' + url)
    return cleanup


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
        return bubble_async_request_json(name, url, headers=headers, method='POST', json=data)

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
    return ip in LOCAL_IPS and fqdns and (bubble_host in fqdns or bubble_host_alias in fqdns)


def is_bubble_special_path(path):
    return path and path.startswith(BUBBLE_URI_PREFIX)


def make_bubble_special_path(path):
    return 'http://127.0.0.1:' + bubble_port + '/' + path[len(BUBBLE_URI_PREFIX):]


def is_bubble_health_check(path):
    return path and path.startswith(HEALTH_CHECK_URI)


def is_sage_request(ip, fqdns):
    return fqdns is not None and (ip == bubble_sage_ip4 or ip == bubble_sage_ip6) and bubble_sage_host in fqdns


def is_not_from_vpn(client_addr):
    ip = IPAddress(client_addr)
    return ip not in VPN_IP4_CIDR and ip not in VPN_IP6_CIDR


def is_flex_domain(client_addr, server_addr, fqdns):
    if fqdns is None or len(fqdns) != 1:
        if bubble_log.isEnabledFor(DEBUG):
            bubble_log.debug('is_flex_domain: no fqdns or multiple fqdns for server_addr '+server_addr+' ('+repr(fqdns)+'), returning False')
        return False
    fqdn = fqdns[0]

    if fqdn == bubble_host or fqdn == bubble_host_alias or (bubble_sage_host is not None and fqdn == bubble_sage_host):
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
    # if bubble_log.isEnabledFor(DEBUG):
    #     bubble_log.debug('is_flex_domain: returning False for: '+fqdn)
    return False


def original_flex_ip(client_addr, fqdns):
    for fqdn in fqdns:
        ip = REDIS.get("flexOriginal~"+client_addr+"~"+fqdn)
        if ip is not None:
            return ip.decode()
    return None


def update_host_and_port(flow):
    if flow.request:
        if flow.client_conn.tls_established:
            flow.request.scheme = "https"
            sni = flow.client_conn.connection.get_servername()
            port = 443
        else:
            flow.request.scheme = "http"
            sni = None
            port = 80

        host_header = flow.request.host_header
        if host_header:
            m = parse_host_header.match(host_header)
            if m:
                host_header = m.group("host").strip("[]")
                if m.group("port"):
                    port = int(m.group("port"))

        host = None
        if sni or host_header:
            host = str(sni or host_header)
            if host.startswith("b'"):
                host = host[2:-1]

        flow.request.host_header = host_header
        if host:
            flow.request.host = host
        else:
            flow.request.host = host_header
        flow.request.port = port

    return flow


def _replace_in_headers(headers: nheaders.Headers, modifiers_dict: dict) -> int:
    """
    Taken from original mitmproxy's Header class implementation with some changes.

    Replaces a regular expression pattern with repl in each "name: value"
    header line.

    Returns:
        The number of replacements made.
    """
    repl_count = 0
    fields = []

    for name, value in headers.fields:

        line = name + b": " + value
        inner_repl_count = 0
        for pattern, replacement in modifiers_dict.items():
            line, n = pattern.subn(replacement, line)
            inner_repl_count += n
            if len(line) == 0:
                # No need to go though other patterns for this line
                break

        if len(line) == 0:
            # Skip (remove) this header line in this case
            break

        if inner_repl_count > 0:
            # only in case when there were some replacements:
            try:
                name, value = line.split(b": ", 1)
            except ValueError:
                # We get a ValueError if the replacement removed the ": "
                # There's not much we can do about this, so we just keep the header as-is.
                pass
            else:
                repl_count += inner_repl_count

        fields.append((name, value))

    headers.fields = tuple(fields)
    return repl_count


def response_header_modify(flow) -> int:
    if flow.response is None:
        return None

    flow = update_host_and_port(flow)
    ctx = {'fqdn': flow.request.host}
    return _header_modify(flow.client_conn.address[0], ctx, flow.response.headers)


def _header_modify(client_addr: str, ctx: dict, headers: nheaders.Headers) -> int:
    modifiers_set = 'responseHeaderModifierLists~' + client_addr + '~UNION'
    modifiers = REDIS.smembers(modifiers_set)

    repl_count = 0
    if modifiers:
        modifiers_dict = {}
        for modifier in modifiers:
            regex, replacement = _extract_modifier_config(modifier, ctx)
            modifiers_dict[regex] = replacement
        repl_count += _replace_in_headers(headers, modifiers_dict)

    if bubble_log.isEnabledFor(DEBUG):
        bubble_log.debug('_header_modify: replacing headers - replacements count: ' + repl_count)

    return repl_count


def _extract_modifier_config(modifier: bytes, ctx: dict) -> tuple:
    modifier_obj = json.loads(modifier)

    regex = _replace_modifier_values(modifier_obj['regex'], ctx)
    replacement = _replace_modifier_values(modifier_obj['replacement'], ctx)

    regex = re.compile(strutils.escaped_str_to_bytes(regex))
    replacement = strutils.escaped_str_to_bytes(replacement)

    return regex, replacement


def _replace_modifier_values(s: str, ctx: dict) -> str:
    # no loop over ctx currently to speed up as there's just 1 variable inside
    s = s.replace('{{fqdn}}', re.escape(ctx['fqdn']))
    return s


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


def tarpit_response(flow, host):
    # if bubble_log.isEnabledFor(DEBUG):
    #     bubble_log.debug('health_check_response: special bubble health check request, responding with OK')
    response_headers = nheaders.Headers()
    response_headers[HEADER_LOCATION] = 'http://'+host+':'+str(TARPIT_PORT)+'/admin/index.php'
    if flow.response is None:
        flow.response = http.HTTPResponse(http_version='HTTP/1.1',
                                          status_code=301,
                                          reason='Moved Permanently',
                                          headers=response_headers,
                                          content=b'')
    else:
        flow.response.headers = nheaders.Headers()
        flow.response.headers = response_headers
        flow.response.status_code = 301
        flow.response.reason = 'Moved Permanently'


def include_request_headers(path):
    return '/followAndApplyRegex' in path


def special_bubble_response(flow):
    name = 'special_bubble_response'
    path = flow.request.path
    if is_bubble_health_check(path):
        health_check_response(flow)
        return

    uri = make_bubble_special_path(path)
    if bubble_log.isEnabledFor(DEBUG):
        bubble_log.debug('special_bubble_response: sending special bubble '+flow.request.method+' to '+uri)
    headers = {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
    }
    if flow.request.method == 'GET':
        loop = asyncio.new_event_loop()
        client = async_client(timeout=30)
        response = async_stream(client, name, uri, headers=headers, loop=loop)

    elif flow.request.method == 'POST':
        if include_request_headers(flow.request.path):
            if bubble_log.isEnabledFor(DEBUG):
                bubble_log.debug('special_bubble_request: including client headers: '+repr(flow.request.headers))
            # add client request headers
            for name, value in flow.request.headers.items():
                headers['X-Bubble-Client-Header-'+name] = value
            if bubble_log.isEnabledFor(DEBUG):
                bubble_log.debug('special_bubble_request: NOW headers='+repr(headers))

        data = None
        if flow.request.content and flow.request.content:
            headers[HEADER_CONTENT_LENGTH] = str(len(flow.request.content))
            data = flow.request.content

        loop = asyncio.new_event_loop()
        client = async_client(timeout=30)
        response = async_stream(client, name, uri, headers=headers, method='POST', data=data, loop=loop)

    else:
        if bubble_log.isEnabledFor(WARNING):
            bubble_log.warning('special_bubble_response: special bubble request: method '+flow.request.method+' not supported')
        return

    if flow.response is None:
        http_version = response.http_version
        response_headers = collect_response_headers(response)
        flow.response = http.HTTPResponse(http_version=http_version,
                                          status_code=response.status_code,
                                          reason=response.reason_phrase,
                                          headers=response_headers,
                                          content=None)
    if response is not None:
        # if bubble_log.isEnabledFor(DEBUG):
        #     bubble_log.debug('special_bubble_response: special bubble request: response status = '+str(response.status_code))
        flow.response.headers = collect_response_headers(response)
        flow.response.status_code = response.status_code
        flow.response.reason = status_reason(response.status_code)
        flow.response.stream = AsyncStreamBody(owner=client, loop=loop, chunks=response.aiter_raw(), finalize=cleanup_async(uri, loop, client, response))


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
