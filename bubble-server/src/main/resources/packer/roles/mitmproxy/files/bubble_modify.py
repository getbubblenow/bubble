#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
import re
import requests
import urllib
import traceback
from mitmproxy.net.http import Headers
from bubble_config import bubble_port, bubble_host_alias, debug_capture_fqdn
from bubble_api import CTX_BUBBLE_MATCHERS, CTX_BUBBLE_ABORT, BUBBLE_URI_PREFIX, \
    CTX_BUBBLE_REQUEST_ID, CTX_CONTENT_LENGTH, CTX_CONTENT_LENGTH_SENT, bubble_log, get_flow_ctx, add_flow_ctx, \
    HEADER_FILTER_PASSTHRU, HEADER_CONTENT_SECURITY_POLICY, REDIS, redis_set, parse_host_header

BUFFER_SIZE = 4096
HEADER_CONTENT_TYPE = 'Content-Type'
HEADER_CONTENT_LENGTH = 'Content-Length'
HEADER_CONTENT_ENCODING = 'Content-Encoding'
HEADER_TRANSFER_ENCODING = 'Transfer-Encoding'
CONTENT_TYPE_BINARY = 'application/octet-stream'
STANDARD_FILTER_HEADERS = {HEADER_CONTENT_TYPE: CONTENT_TYPE_BINARY}

REDIS_FILTER_PASSTHRU_PREFIX = '__chunk_filter_pass__'
REDIS_FILTER_PASSTHRU_DURATION = 600

def filter_chunk(flow, chunk, req_id, last, content_encoding=None, content_type=None, content_length=None, csp=None):
    if debug_capture_fqdn:
        host = None
        if flow.client_conn.tls_established:
            sni = flow.client_conn.connection.get_servername()
            if sni:
                host = str(sni)
        else:
            host_header = flow.request.host_header
            if host_header:
                m = parse_host_header.match(host_header)
                if m:
                    host = str(m.group("host").strip("[]"))
        if host:
            if host.startswith("b'"):
                host = host[2:-1]
            if host in debug_capture_fqdn:
                bubble_log('filter_chunk: debug_capture_fqdn detected, capturing: '+host)
                f = open('/tmp/bubble_capture_'+req_id, mode='ab', buffering=0)
                f.write(chunk)
                f.close()
                return chunk

    # should we just passthru?
    redis_passthru_key = REDIS_FILTER_PASSTHRU_PREFIX + flow.request.method + ':' + flow.request.url
    do_pass = REDIS.get(redis_passthru_key)
    if do_pass:
        # bubble_log('filter_chunk: req_id='+req_id+': passthru found in redis, returning chunk')
        REDIS.touch(redis_passthru_key)
        return chunk

    url = 'http://127.0.0.1:' + bubble_port + '/api/filter/apply/' + req_id
    params_added = False
    if chunk and content_type:
        params_added = True
        url = (url
               + '?type=' + urllib.parse.quote_plus(content_type))
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
        bubble_log('filter_chunk: url='+url+' (with csp)')
        filter_headers = {
            HEADER_CONTENT_TYPE: CONTENT_TYPE_BINARY,
            HEADER_CONTENT_SECURITY_POLICY: csp
        }
    else:
        bubble_log('filter_chunk: url='+url+' (no csp)')
        filter_headers = STANDARD_FILTER_HEADERS

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


def bubble_filter_chunks(flow, chunks, req_id, content_encoding, content_type, csp):
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
                yield filter_chunk(flow, chunk, req_id, last, content_encoding, content_type, content_length, csp)
                first = False
            else:
                yield filter_chunk(flow, chunk, req_id, last)
        if not content_length:
            yield filter_chunk(flow, None, req_id, True)  # get the last bits of data
    except Exception as e:
        bubble_log('bubble_filter_chunks: exception='+repr(e))
        traceback.print_exc()
        yield None


def bubble_modify(flow, req_id, content_encoding, content_type, csp):
    return lambda chunks: bubble_filter_chunks(flow, chunks, req_id, content_encoding, content_type, csp)


def send_bubble_response(response):
    for chunk in response.iter_content(8192):
        yield chunk


def responseheaders(flow):

    if flow.request.path and flow.request.path.startswith(BUBBLE_URI_PREFIX):
        uri = 'http://127.0.0.1:' + bubble_port + '/' + flow.request.path[len(BUBBLE_URI_PREFIX):]
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
            bubble_log('responseheaders: aborting request with HTTP status '+str(abort_code))
            flow.response.headers = Headers()
            flow.response.status_code = abort_code
            flow.response.stream = lambda chunks: []

        else:
            req_id = get_flow_ctx(flow, CTX_BUBBLE_REQUEST_ID)
            matchers = get_flow_ctx(flow, CTX_BUBBLE_MATCHERS)
            prefix = 'responseheaders(req_id='+str(req_id)+'): '
            if req_id is not None and matchers is not None:
                bubble_log(prefix+' matchers: '+repr(matchers))
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
                                    bubble_log(prefix+'found at least one matcher for content_type ('+content_type+'), filtering')
                                    break
                        if not any_content_type_matches:
                            bubble_log(prefix+'no matchers for content_type ('+content_type+'), passing thru')
                            return

                        if HEADER_CONTENT_ENCODING in flow.response.headers:
                            content_encoding = flow.response.headers[HEADER_CONTENT_ENCODING]
                        else:
                            content_encoding = None

                        if HEADER_CONTENT_SECURITY_POLICY in flow.response.headers:
                            csp = flow.response.headers[HEADER_CONTENT_SECURITY_POLICY]
                        else:
                            csp = None

                        content_length_value = flow.response.headers.pop(HEADER_CONTENT_LENGTH, None)
                        bubble_log(prefix+'content_encoding='+repr(content_encoding) + ', content_type='+repr(content_type))
                        flow.response.stream = bubble_modify(flow, req_id, content_encoding, content_type, csp)
                        if content_length_value:
                            flow.response.headers['transfer-encoding'] = 'chunked'
                            # find server_conn to set fake_chunks on
                            if flow.live and flow.live.ctx:
                                ctx = flow.live.ctx
                                while not hasattr(ctx, 'server_conn'):
                                    if hasattr(ctx, 'ctx'):
                                        ctx = ctx.ctx
                                    else:
                                        bubble_log(prefix+'error finding server_conn. last ctx has no further ctx. type='+str(type(ctx))+' vars='+str(vars(ctx)))
                                        return
                                if not hasattr(ctx, 'server_conn'):
                                    bubble_log(prefix+'error finding server_conn. ctx type='+str(type(ctx))+' vars='+str(vars(ctx)))
                                    return
                                content_length = int(content_length_value)
                                ctx.server_conn.rfile.fake_chunks = content_length
                                add_flow_ctx(flow, CTX_CONTENT_LENGTH, content_length)
                                add_flow_ctx(flow, CTX_CONTENT_LENGTH_SENT, 0)

                    else:
                        bubble_log(prefix+'no matchers, passing thru')
                        pass
                else:
                    bubble_log(prefix+'no '+HEADER_CONTENT_TYPE +' header, passing thru')
                    pass
            else:
                bubble_log(prefix+'no '+CTX_BUBBLE_MATCHERS +' in ctx, passing thru')
                pass
