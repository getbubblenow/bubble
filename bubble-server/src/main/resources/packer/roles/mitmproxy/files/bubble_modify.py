#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
import json
import re
import requests
import urllib
import traceback
from mitmproxy.net.http import Headers
from bubble_config import bubble_port, bubble_host_alias, debug_capture_fqdn, debug_stream_fqdn, debug_stream_uri
from bubble_api import CTX_BUBBLE_MATCHERS, CTX_BUBBLE_ABORT, CTX_BUBBLE_LOCATION, BUBBLE_URI_PREFIX, \
    HEADER_HEALTH_CHECK, HEALTH_CHECK_URI, \
    CTX_BUBBLE_REQUEST_ID, CTX_CONTENT_LENGTH, CTX_CONTENT_LENGTH_SENT, bubble_log, get_flow_ctx, add_flow_ctx, \
    HEADER_USER_AGENT, HEADER_FILTER_PASSTHRU, HEADER_CONTENT_SECURITY_POLICY, REDIS, redis_set, parse_host_header

BUFFER_SIZE = 4096
HEADER_CONTENT_TYPE = 'Content-Type'
HEADER_CONTENT_LENGTH = 'Content-Length'
HEADER_CONTENT_ENCODING = 'Content-Encoding'
HEADER_TRANSFER_ENCODING = 'Transfer-Encoding'
HEADER_LOCATION = 'Location'
CONTENT_TYPE_BINARY = 'application/octet-stream'
STANDARD_FILTER_HEADERS = {HEADER_CONTENT_TYPE: CONTENT_TYPE_BINARY}

REDIS_FILTER_PASSTHRU_PREFIX = '__chunk_filter_pass__'
REDIS_FILTER_PASSTHRU_DURATION = 600

DEBUG_STREAM_COUNTERS = {}


def add_csp_part(new_csp, part):
    if len(new_csp) > 0:
        new_csp = new_csp + ';'
    return new_csp + part


def ensure_bubble_script_csp(csp):
    new_csp = ''
    parts = csp.split(';')
    for part in parts:
        if part.startswith(' img-src ') or part.startswith('img-src '):
            tokens = part.split()
            if "'self'" in tokens:
                new_csp = add_csp_part(new_csp, part)
                continue
            new_csp = add_csp_part(new_csp, tokens[0] + " 'self' " + " ".join(tokens[1:]))

        else:
            new_csp = add_csp_part(new_csp, part)
    return new_csp


def filter_chunk(flow, chunk, req_id, user_agent, last, content_encoding=None, content_type=None, content_length=None, csp=None):
    if debug_capture_fqdn:
        if debug_capture_fqdn in req_id:
            bubble_log('filter_chunk: debug_capture_fqdn detected, capturing: '+debug_capture_fqdn)
            f = open('/tmp/bubble_capture_'+req_id, mode='ab', buffering=0)
            f.write(chunk)
            f.close()
            return chunk

    # should we just passthru?
    redis_passthru_key = REDIS_FILTER_PASSTHRU_PREFIX + flow.request.method + '~~~' + user_agent + ':' + flow.request.url
    do_pass = REDIS.get(redis_passthru_key)
    if do_pass:
        bubble_log('filter_chunk: req_id='+req_id+': passthru found in redis, returning chunk')
        REDIS.touch(redis_passthru_key)
        return chunk

    url = 'http://127.0.0.1:' + bubble_port + '/api/filter/apply/' + req_id
    params_added = False
    if chunk and content_type:
        params_added = True
        url = url + '?type=' + urllib.parse.quote_plus(content_type)
        if content_encoding:
            url = url + '&encoding=' + urllib.parse.quote_plus(content_encoding)
        if content_length:
            url = url + '&length=' + str(content_length)
    if last:
        if params_added:
            url = url + '&last=true'
        else:
            url = url + '?last=true'

    if csp:
        # bubble_log('filter_chunk: url='+url+' (csp='+csp+')')
        bubble_log('filter_chunk: url='+url+' (with csp) (last='+str(last)+')')
        filter_headers = {
            HEADER_CONTENT_TYPE: CONTENT_TYPE_BINARY,
            HEADER_CONTENT_SECURITY_POLICY: csp
        }
    else:
        bubble_log('filter_chunk: url='+url+' (no csp) (last='+str(last)+')')
        filter_headers = STANDARD_FILTER_HEADERS

    if debug_stream_fqdn and debug_stream_uri and debug_stream_fqdn in req_id and flow.request.path == debug_stream_uri:
        if req_id in DEBUG_STREAM_COUNTERS:
            count = DEBUG_STREAM_COUNTERS[req_id] + 1
        else:
            count = 0
        DEBUG_STREAM_COUNTERS[req_id] = count
        bubble_log('filter_chunk: debug_stream detected, capturing: '+debug_stream_fqdn)
        f = open('/tmp/bubble_stream_'+req_id+'_chunk'+"{:04d}".format(count)+'.data', mode='wb', buffering=0)
        if chunk is not None:
            f.write(chunk)
        f.close()
        f = open('/tmp/bubble_stream_'+req_id+'_chunk'+"{:04d}".format(count)+'.headers.json', mode='w')
        f.write(json.dumps(filter_headers))
        f.close()
        f = open('/tmp/bubble_stream_'+req_id+'_chunk'+"{:04d}".format(count)+'.url', mode='w')
        f.write(url)
        f.close()

    response = requests.post(url, data=chunk, headers=filter_headers)
    if not response.ok:
        err_message = 'filter_chunk: Error fetching ' + url + ', HTTP status ' + str(response.status_code)
        bubble_log(err_message)
        return b''

    elif HEADER_FILTER_PASSTHRU in response.headers:
        bubble_log('filter_chunk: server returned X-Bubble-Passthru, not filtering subsequent requests')
        redis_set(redis_passthru_key, 'passthru', ex=REDIS_FILTER_PASSTHRU_DURATION)
        return chunk

    return response.content


def bubble_filter_chunks(flow, chunks, req_id, user_agent, content_encoding, content_type, csp):
    """
    chunks is a generator that can be used to iterate over all chunks.
    """
    first = True
    content_length = get_flow_ctx(flow, CTX_CONTENT_LENGTH)
    try:
        for chunk in chunks:
            if content_length:
                bytes_sent = get_flow_ctx(flow, CTX_CONTENT_LENGTH_SENT)
                chunk_len = len(chunk)
                last = chunk_len + bytes_sent >= content_length
                bubble_log('bubble_filter_chunks: content_length = '+str(content_length)+', bytes_sent = '+str(bytes_sent))
                add_flow_ctx(flow, CTX_CONTENT_LENGTH_SENT, bytes_sent + chunk_len)
            else:
                last = False
            if first:
                yield filter_chunk(flow, chunk, req_id, user_agent, last, content_encoding, content_type, content_length, csp)
                first = False
            else:
                yield filter_chunk(flow, chunk, req_id, user_agent, last)
        if not content_length:
            yield filter_chunk(flow, None, req_id, user_agent, True)  # get the last bits of data
    except Exception as e:
        bubble_log('bubble_filter_chunks: exception='+repr(e))
        traceback.print_exc()
        yield None


def bubble_modify(flow, req_id, user_agent, content_encoding, content_type, csp):
    return lambda chunks: bubble_filter_chunks(flow, chunks, req_id, user_agent, content_encoding, content_type, csp)


def send_bubble_response(response):
    for chunk in response.iter_content(8192):
        yield chunk


EMPTY_XML = [b'<?xml version="1.0" encoding="UTF-8"?><html></html>']
EMPTY_JSON = [b'null']
EMPTY_DEFAULT = []


def abort_data(content_type):
    if content_type is None:
        return EMPTY_DEFAULT
    if 'text/html' in content_type or 'application/xml' in content_type:
        return EMPTY_XML
    if 'application/json' in content_type:
        return EMPTY_JSON
    return EMPTY_DEFAULT


def responseheaders(flow):

    path = flow.request.path
    if path and path.startswith(BUBBLE_URI_PREFIX):
        if path.startswith(HEALTH_CHECK_URI):
            bubble_log('responseheaders: special bubble health check request, responding with OK')
            flow.response.headers = Headers()
            flow.response.headers[HEADER_HEALTH_CHECK] = 'OK'
            flow.response.headers[HEADER_CONTENT_LENGTH] = '3'
            flow.response.status_code = 200
            flow.response.stream = lambda chunks: [b'OK\n']
        else:
            uri = 'http://127.0.0.1:' + bubble_port + '/' + path[len(BUBBLE_URI_PREFIX):]
            bubble_log('responseheaders: sending special bubble request to '+uri)
            headers = {
                'Accept' : 'application/json',
                'Content-Type': 'application/json'
            }
            response = None
            if flow.request.method == 'GET':
                response = requests.get(uri, headers=headers, stream=True)
            elif flow.request.method == 'POST':
                bubble_log('responseheaders: special bubble request: POST content is '+str(flow.request.content))
                headers['Content-Length'] = str(len(flow.request.content))
                response = requests.post(uri, data=flow.request.content, headers=headers)
            else:
                bubble_log('responseheaders: special bubble request: method '+flow.request.method+' not supported')
            if response is not None:
                bubble_log('responseheaders: special bubble request: response status = '+str(response.status_code))
                flow.response.headers = Headers()
                for key, value in response.headers.items():
                    flow.response.headers[key] = value
                flow.response.status_code = response.status_code
                flow.response.stream = lambda chunks: send_bubble_response(response)

    else:
        abort_code = get_flow_ctx(flow, CTX_BUBBLE_ABORT)
        if abort_code is not None:
            abort_location = get_flow_ctx(flow, CTX_BUBBLE_LOCATION)
            if abort_location is not None:
                bubble_log('responseheaders: redirecting request with HTTP status '+str(abort_code)+' to: '+abort_location+', path was: '+path)
                flow.response.headers = Headers()
                flow.response.headers[HEADER_LOCATION] = abort_location
                flow.response.status_code = abort_code
                flow.response.stream = lambda chunks: []
            else:
                if HEADER_CONTENT_TYPE in flow.response.headers:
                    content_type = flow.response.headers[HEADER_CONTENT_TYPE]
                else:
                    content_type = None
                bubble_log('responseheaders: aborting request with HTTP status '+str(abort_code)+', path was: '+path)
                flow.response.headers = Headers()
                flow.response.status_code = abort_code
                flow.response.stream = lambda chunks: abort_data(content_type)

        elif flow.response.status_code // 100 != 2:
            bubble_log('responseheaders: response had HTTP status '+str(flow.response.status_code)+', returning as-is: '+path)
            pass

        elif flow.response.headers is None or len(flow.response.headers) == 0:
            bubble_log('responseheaders: response had HTTP status '+str(flow.response.status_code)+', and NO response headers, returning as-is: '+path)
            pass

        elif HEADER_CONTENT_LENGTH in flow.response.headers and flow.response.headers[HEADER_CONTENT_LENGTH] == "0":
            bubble_log('responseheaders: response had HTTP status '+str(flow.response.status_code)+', and '+HEADER_CONTENT_LENGTH+' was zero, returning as-is: '+path)
            pass

        else:
            req_id = get_flow_ctx(flow, CTX_BUBBLE_REQUEST_ID)
            matchers = get_flow_ctx(flow, CTX_BUBBLE_MATCHERS)
            prefix = 'responseheaders(req_id='+str(req_id)+'): '
            if req_id is not None and matchers is not None:
                bubble_log(prefix+' matchers: '+repr(matchers))
                if HEADER_USER_AGENT in flow.request.headers:
                    user_agent = flow.request.headers[HEADER_USER_AGENT]
                else:
                    user_agent = ''
                if HEADER_CONTENT_TYPE in flow.response.headers:
                    content_type = flow.response.headers[HEADER_CONTENT_TYPE]
                    if matchers:
                        any_content_type_matches = False
                        for m in matchers:
                            if 'contentTypeRegex' in m:
                                typeRegex = m['contentTypeRegex']
                                if typeRegex is None:
                                    typeRegex = '^text/html.*'
                                if re.match(typeRegex, content_type):
                                    any_content_type_matches = True
                                    bubble_log(prefix+'found at least one matcher for content_type ('+content_type+'), filtering: '+path)
                                    break
                        if not any_content_type_matches:
                            bubble_log(prefix+'no matchers for content_type ('+content_type+'), passing thru: '+path)
                            return

                        if HEADER_CONTENT_ENCODING in flow.response.headers:
                            content_encoding = flow.response.headers[HEADER_CONTENT_ENCODING]
                        else:
                            content_encoding = None

                        if HEADER_CONTENT_SECURITY_POLICY in flow.response.headers:
                            csp = ensure_bubble_script_csp(flow.response.headers[HEADER_CONTENT_SECURITY_POLICY])
                            flow.response.headers[HEADER_CONTENT_SECURITY_POLICY] = csp
                        else:
                            csp = None

                        content_length_value = flow.response.headers.pop(HEADER_CONTENT_LENGTH, None)
                        # bubble_log(prefix+'content_encoding='+repr(content_encoding) + ', content_type='+repr(content_type))
                        flow.response.stream = bubble_modify(flow, req_id, user_agent, content_encoding, content_type, csp)
                        if content_length_value:
                            flow.response.headers['transfer-encoding'] = 'chunked'
                            # find server_conn to set fake_chunks on
                            if flow.live and flow.live.ctx:
                                ctx = flow.live.ctx
                                while not hasattr(ctx, 'server_conn'):
                                    if hasattr(ctx, 'ctx'):
                                        ctx = ctx.ctx
                                    else:
                                        bubble_log(prefix+'error finding server_conn for path '+path+'. last ctx has no further ctx. type='+str(type(ctx))+' vars='+str(vars(ctx)))
                                        return
                                if not hasattr(ctx, 'server_conn'):
                                    bubble_log(prefix+'error finding server_conn for path '+path+'. ctx type='+str(type(ctx))+' vars='+str(vars(ctx)))
                                    return
                                content_length = int(content_length_value)
                                ctx.server_conn.rfile.fake_chunks = content_length
                                add_flow_ctx(flow, CTX_CONTENT_LENGTH, content_length)
                                add_flow_ctx(flow, CTX_CONTENT_LENGTH_SENT, 0)

                    else:
                        bubble_log(prefix+'no matchers, passing thru: '+path)
                        pass
                else:
                    bubble_log(prefix+'no '+HEADER_CONTENT_TYPE +' header, passing thru: '+path)
                    pass
            else:
                bubble_log(prefix+'no '+CTX_BUBBLE_MATCHERS +' in ctx, passing thru: '+path)
                pass
