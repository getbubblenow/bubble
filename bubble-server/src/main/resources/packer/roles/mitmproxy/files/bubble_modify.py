#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
import asyncio
import base64
import json
import re
import urllib
import traceback

from mitmproxy.net.http import Headers

from bubble_config import bubble_port, debug_capture_fqdn, debug_stream_fqdn, debug_stream_uri, bubble_log_response
from bubble_api import CTX_BUBBLE_MATCHERS, CTX_BUBBLE_ABORT, CTX_BUBBLE_LOCATION, CTX_BUBBLE_FLEX, \
    status_reason, update_host_and_port, get_flow_ctx, add_flow_ctx, bubble_async, async_client, cleanup_async, \
    is_bubble_special_path, is_bubble_health_check, health_check_response, special_bubble_response, \
    CTX_BUBBLE_REQUEST_ID, CTX_CONTENT_LENGTH, CTX_CONTENT_LENGTH_SENT, CTX_BUBBLE_FILTERED, \
    HEADER_CONTENT_TYPE, HEADER_CONTENT_ENCODING, HEADER_LOCATION, HEADER_CONTENT_LENGTH, \
    HEADER_USER_AGENT, HEADER_FILTER_PASSTHRU, HEADER_CONTENT_SECURITY_POLICY, REDIS, redis_set, response_header_modify
from bubble_flex import process_flex

import logging
from logging import INFO, DEBUG, WARNING, ERROR, CRITICAL

bubble_log = logging.getLogger(__name__)

log_debug = bubble_log.isEnabledFor(DEBUG)
log_info = bubble_log.isEnabledFor(INFO)
log_warning = bubble_log.isEnabledFor(WARNING)
log_error = bubble_log.isEnabledFor(ERROR)

BUFFER_SIZE = 4096
CONTENT_TYPE_BINARY = 'application/octet-stream'
STANDARD_FILTER_HEADERS = {HEADER_CONTENT_TYPE: CONTENT_TYPE_BINARY}

REDIS_FILTER_PASSTHRU_PREFIX = '__chunk_filter_pass__'
REDIS_FILTER_PASSTHRU_DURATION = 600

DEBUG_STREAM_COUNTERS = {}
MIN_FILTER_CHUNK_SIZE = 1024 * 32  # Filter data in 32KB chunks


def add_csp_part(new_csp, part):
    if len(new_csp) > 0:
        new_csp = new_csp + ';'
    return new_csp + part


def ensure_bubble_csp(csp, req_id):
    new_csp = ''
    parts = csp.split(';')
    for part in parts:
        if part.startswith(' img-src ') or part.startswith('img-src '):
            tokens = part.split()
            if "'self'" in tokens:
                new_csp = add_csp_part(new_csp, part)
                continue
            new_csp = add_csp_part(new_csp, tokens[0] + " 'self' " + " ".join(tokens[1:]))

        elif part.startswith(' script-src ') or part.startswith('script-src '):
            tokens = part.split()
            found_unsafe_inline = "'unsafe-inline'" in tokens
            found_nonce = False
            found_sha = False
            for token in tokens:
                if not found_nonce and "'nonce-" in token:
                    found_nonce = True
                if not found_sha and ("'sha256-" in token or "'sha384-" in token or "'sha512-" in token):
                    found_sha = True
                if found_nonce and found_sha:
                    break
            if found_unsafe_inline:
                if not found_sha and not found_nonce:
                    # unsafe-inline is set, and there are no shas or nonces
                    # then we can add ourselves as unsafe inline without any nonce
                    new_csp = add_csp_part(new_csp, part)
                elif found_nonce:
                    # unsafe-inline is set and there is a nonce, we keep the nonce
                    new_csp = add_csp_part(new_csp, part)
                elif found_sha:
                    # unsafe-inline is set and there is no nonce, but at least one sha is present
                    # we must add a nonce for ourselves
                    new_csp = add_csp_part(new_csp, " ".join(tokens) + " 'nonce-"+base64.b64encode(bytes(req_id, 'utf-8')).decode()+"' ")
                else:
                    # unreachable, just for sanity
                    new_csp = add_csp_part(new_csp, part)
            else:
                # unsafe-inline is not set
                if not found_nonce or found_sha:
                    # there is no nonce or a sha is set, add unsafe-inline and our nonce
                    new_csp = add_csp_part(new_csp, tokens[0] + " 'unsafe-inline' 'nonce-"+base64.b64encode(bytes(req_id, 'utf-8')).decode()+"' " + " ".join(tokens[1:]))
                elif found_nonce:
                    # there is a nonce, keep it and add unsafe-inline
                    new_csp = add_csp_part(new_csp, tokens[0] + " 'unsafe-inline' " + " ".join[tokens[1:]])
                elif found_sha:
                    # no nonce but a sha is set, add our nonce and unsafe-inline
                    new_csp = add_csp_part(new_csp, tokens[0] + " 'unsafe-inline' 'nonce-"+base64.b64encode(bytes(req_id, 'utf-8')).decode()+"' " + " ".join(tokens[1:]))
                else:
                    # unreachable, just for sanity
                    new_csp = add_csp_part(new_csp, part)
        else:
            new_csp = add_csp_part(new_csp, part)
    return new_csp


def filter_chunk(loop, flow, chunk, req_id, user_agent, last, content_encoding=None, content_type=None, content_length=None, csp=None, client=None):
    name = 'filter_chunk'
    if debug_capture_fqdn:
        if debug_capture_fqdn in req_id:
            if log_debug:
                bubble_log.debug('filter_chunk: debug_capture_fqdn detected, capturing: '+debug_capture_fqdn)
            f = open('/tmp/bubble_capture_'+req_id, mode='ab', buffering=0)
            f.write(chunk)
            f.close()
            return chunk

    # should we just passthru?
    redis_passthru_key = REDIS_FILTER_PASSTHRU_PREFIX + flow.request.method + '~~~' + user_agent + ':' + flow.request.url
    do_pass = REDIS.get(redis_passthru_key)
    if do_pass:
        if log_debug:
            bubble_log.debug('filter_chunk: req_id='+req_id+': passthru found in redis, returning chunk')
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

    chunk_len = 0
    if log_debug:
        if chunk is not None:
            chunk_len = len(chunk)
    if csp:
        if log_debug:
            bubble_log.debug('filter_chunk: url='+url+' (csp='+csp+') size='+str(chunk_len))
        headers = {
            HEADER_CONTENT_TYPE: CONTENT_TYPE_BINARY,
            HEADER_CONTENT_SECURITY_POLICY: csp
        }
    else:
        if log_debug:
            bubble_log.debug('filter_chunk: url='+url+' (no csp) size='+str(chunk_len))
        headers = STANDARD_FILTER_HEADERS

    if debug_stream_fqdn and debug_stream_uri and debug_stream_fqdn in req_id and flow.request.path == debug_stream_uri:
        if req_id in DEBUG_STREAM_COUNTERS:
            count = DEBUG_STREAM_COUNTERS[req_id] + 1
        else:
            count = 0
        DEBUG_STREAM_COUNTERS[req_id] = count
        if log_debug:
            bubble_log.debug('filter_chunk: debug_stream detected, capturing: '+debug_stream_fqdn)
        f = open('/tmp/bubble_stream_'+req_id+'_chunk'+"{:04d}".format(count)+'.data', mode='wb', buffering=0)
        if chunk is not None:
            f.write(chunk)
        f.close()
        f = open('/tmp/bubble_stream_'+req_id+'_chunk'+"{:04d}".format(count)+'.headers.json', mode='w')
        f.write(json.dumps(headers))
        f.close()
        f = open('/tmp/bubble_stream_'+req_id+'_chunk'+"{:04d}".format(count)+'.url', mode='w')
        f.write(url)
        f.close()

    response = bubble_async(name, url, headers=headers, method='POST', data=chunk, loop=loop, client=client)
    if response is None:
        err_message = 'filter_chunk: Error fetching ' + url + ', response was None'
        if log_error:
            bubble_log.error(err_message)
        return b''

    elif not response.status_code == 200:
        err_message = 'filter_chunk: Error fetching ' + url + ', HTTP status ' + str(response.status_code) + ' content='+repr(response.content)
        if log_error:
            bubble_log.error(err_message)
        return b''

    elif HEADER_FILTER_PASSTHRU in response.headers:
        if log_debug:
            bubble_log.debug('filter_chunk: server returned X-Bubble-Passthru, not filtering subsequent requests')
        redis_set(redis_passthru_key, 'passthru', ex=REDIS_FILTER_PASSTHRU_DURATION)
        return chunk

    if log_debug:
        bubble_log.debug('filter_chunk: returning '+str(len(response.content))+' bytes of filtered content')
    return response.content


def bubble_filter_chunks(flow, chunks, req_id, user_agent, content_encoding, content_type, csp):
    loop = asyncio.new_event_loop()
    if log_debug:
        bubble_log.debug('bubble_filter_chunks: starting with content_type='+content_type)
    first = True
    last = False
    content_length = get_flow_ctx(flow, CTX_CONTENT_LENGTH)
    if log_debug:
        bubble_log.debug('bubble_filter_chunks: found content_length='+str(content_length))
    try:
        buffer = b''
        for chunk in chunks:
            buffer = buffer + chunk
            if not last and len(buffer) < MIN_FILTER_CHUNK_SIZE:
                continue
            chunk_len = len(buffer)
            chunk = buffer
            buffer = b''
            if content_length:
                bytes_sent = get_flow_ctx(flow, CTX_CONTENT_LENGTH_SENT)
                last = chunk_len + bytes_sent >= content_length
                if log_debug:
                    bubble_log.debug('bubble_filter_chunks: content_length = '+str(content_length)+', bytes_sent = '+str(bytes_sent))
                add_flow_ctx(flow, CTX_CONTENT_LENGTH_SENT, bytes_sent + chunk_len)
            else:
                last = False
            if first:
                yield filter_chunk(loop, flow, chunk, req_id, user_agent, last, content_encoding, content_type, content_length, csp)
                first = False
            else:
                yield filter_chunk(loop, flow, chunk, req_id, user_agent, last)
        # send whatever is left in the buffer
        if len(buffer) > 0:
            # bubble_log.debug('bubble_filter_chunks(end): sending remainder buffer of size '+str(len(buffer)))
            if first:
                yield filter_chunk(loop, flow, buffer, req_id, user_agent, last, content_encoding, content_type, content_length, csp)
            else:
                yield filter_chunk(loop, flow, buffer, req_id, user_agent, last)
        if not content_length or not last:
            # bubble_log.debug('bubble_filter_chunks(end): sending last empty chunk')
            yield filter_chunk(loop, flow, None, req_id, user_agent, True)  # get the last bits of data
    except Exception as e:
        if log_error:
            bubble_log.error('bubble_filter_chunks: exception='+repr(e))
        traceback.print_exc()
        yield None
    finally:
        loop.close()


def bubble_modify(flow, req_id, user_agent, content_encoding, content_type, csp):
    if log_debug:
        bubble_log.debug('bubble_modify: modifying req_id='+req_id+' with content_type='+content_type)
    return lambda chunks: bubble_filter_chunks(flow, chunks, req_id,
                                               user_agent, content_encoding, content_type, csp)


class AsyncStreamContext:
    first = True
    buffer = b''


def async_filter_chunk(stream_body_obj, flow, req_id, user_agent, content_encoding, content_type, csp):
    client = async_client()
    loop = asyncio.new_event_loop()
    stream_body_obj.ctx = AsyncStreamContext()
    orig_finalize = stream_body_obj.finalize

    def _finalize():
        bubble_log.info('_finalize: cleaning up for '+req_id+' sent '+str(stream_body_obj.total)+' bytes')
        if orig_finalize is not None:
            orig_finalize()
        cleanup_async('_async_filter_chunk('+req_id+')', loop, client, None)

    stream_body_obj.finalize = _finalize
    stream_body_obj.total = 0

    def _async_filter_chunk(chunk, last):
        if chunk is None:
            bubble_log.info('_async_filter_chunk: filtering None chunk (!!) last='+str(last))
        else:
            bubble_log.info('_async_filter_chunk: filtering chunk of size = '+str(len(chunk))+' last=' + str(last))
            stream_body_obj.ctx.buffer = stream_body_obj.ctx.buffer + chunk
        if not last and len(stream_body_obj.ctx.buffer) < MIN_FILTER_CHUNK_SIZE:
            return None
        chunk = stream_body_obj.ctx.buffer
        stream_body_obj.ctx.buffer = b''
        if stream_body_obj.ctx.first:
            stream_body_obj.ctx.first = False
            new_chunk = filter_chunk(loop, flow, chunk, req_id, user_agent, last,
                                     content_encoding=content_encoding, content_type=content_type, content_length=None,
                                     csp=csp, client=client)
        else:
            new_chunk = filter_chunk(loop, flow, chunk, req_id, user_agent, last, client=client)
        if new_chunk is None or len(chunk) == 0:
            bubble_log.info('_async_filter_chunk: filtered chunk, got back None or zero chunk (means "send more data")')
            return None
        else:
            bubble_log.info('_async_filter_chunk: filtered chunk, got back chunk of size '+str(len(new_chunk)))
        stream_body_obj.total = stream_body_obj.total + len(new_chunk)
        return new_chunk

    return _async_filter_chunk


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
    flex_flow = get_flow_ctx(flow, CTX_BUBBLE_FLEX)
    if flex_flow:
        flex_flow = process_flex(flex_flow)
    else:
        flex_flow = None
    if log_debug and bubble_log_response:
        bubble_log.debug('responseheaders: response headers are: '+repr(flow.response.headers))

    # fixme: when response header modification is enabled, some filtered requests hang when iterating chunks
    # response_header_modify(flow)

    bubble_filter_response(flow, flex_flow)
    pass


def bubble_filter_response(flow, flex_flow):
    # only filter once -- flex routing may have pre-filtered
    if get_flow_ctx(flow, CTX_BUBBLE_FILTERED):
        return
    add_flow_ctx(flow, CTX_BUBBLE_FILTERED, True)
    update_host_and_port(flow)
    path = flow.request.path
    host = flow.request.host
    log_url = str(flow.request.scheme) + '://' + str(host) + str(path)
    client_addr = flow.client_conn.address[0]
    if is_bubble_special_path(path):
        if is_bubble_health_check(path):
            health_check_response(flow)
        else:
            if log_debug:
                bubble_log.debug('bubble_filter_response: sending special bubble response for path: '+path)
            special_bubble_response(flow)

    elif flex_flow and flex_flow.is_error():
        if log_debug:
            bubble_log.debug('bubble_filter_response: flex_flow had error, returning error_html: ' + repr(flex_flow.response_stream))
        flow.response.stream = flex_flow.response_stream

    else:
        abort_code = get_flow_ctx(flow, CTX_BUBBLE_ABORT)
        if abort_code is not None:
            abort_location = get_flow_ctx(flow, CTX_BUBBLE_LOCATION)
            if abort_location is not None:
                if log_info:
                    bubble_log.info('bubble_filter_response: MOD-DECISION: REDIRECT '+log_url+' redirecting request with HTTP status '+str(abort_code)+' to: '+abort_location+', path was: '+path)
                flow.response.headers = Headers()
                flow.response.headers[HEADER_LOCATION] = abort_location
                flow.response.status_code = abort_code
                flow.response.reason = status_reason(abort_code)
                flow.response.stream = lambda chunks: []
            else:
                if HEADER_CONTENT_TYPE in flow.response.headers:
                    content_type = flow.response.headers[HEADER_CONTENT_TYPE]
                else:
                    content_type = None
                if log_info:
                    bubble_log.info('bubble_filter_response: MOD-DECISION: ABORT '+log_url+' aborting request from '+client_addr+' with HTTP status '+str(abort_code)+', path was: '+path)
                flow.response.headers = Headers()
                flow.response.status_code = abort_code
                flow.response.reason = status_reason(abort_code)
                flow.response.stream = lambda chunks: abort_data(content_type)

        elif flow.response.status_code // 100 != 2:
            if log_info:
                bubble_log.info('bubble_filter_response: MOD-DECISION: NOT-OK '+log_url+' response had HTTP status '+str(flow.response.status_code)+', returning as-is: '+path)
            flow.response.headers[HEADER_CONTENT_LENGTH] = '0'
            pass

        elif flow.response.headers is None or len(flow.response.headers) == 0:
            if log_info:
                bubble_log.info('bubble_filter_response: MOD-DECISION: NO-HEADERS '+log_url+' response had HTTP status '+str(flow.response.status_code)+', and NO response headers, returning as-is: '+path)
            pass

        elif HEADER_CONTENT_LENGTH in flow.response.headers and flow.response.headers[HEADER_CONTENT_LENGTH] == "0":
            if log_info:
                bubble_log.info('bubble_filter_response: MOD-DECISION: NO-LENGTH '+log_url+' response had HTTP status '+str(flow.response.status_code)+', and '+HEADER_CONTENT_LENGTH+' was zero, returning as-is: '+path)
            pass

        else:
            req_id = get_flow_ctx(flow, CTX_BUBBLE_REQUEST_ID)
            matchers = get_flow_ctx(flow, CTX_BUBBLE_MATCHERS)
            prefix = 'bubble_filter_response(req_id='+str(req_id)+'): '
            if log_info:
                bubble_log.info('bubble_filter_response: MOD-DECISION: FILTER '+log_url+' with matchers='+repr(matchers))
            if req_id is not None and matchers is not None:
                if log_debug:
                    bubble_log.debug(prefix+' matchers: '+repr(matchers))
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
                                type_regex = m['contentTypeRegex']
                                if type_regex is None:
                                    type_regex = '^text/html.*'
                                if re.match(type_regex, content_type):
                                    any_content_type_matches = True
                                    if log_debug:
                                        bubble_log.debug(prefix+'found at least one matcher for content_type ('+content_type+'), filtering: '+path)
                                    break
                        if not any_content_type_matches:
                            if log_debug:
                                bubble_log.debug(prefix+'no matchers for content_type ('+content_type+'), passing thru: '+path)
                            return

                        if HEADER_CONTENT_ENCODING in flow.response.headers:
                            content_encoding = flow.response.headers[HEADER_CONTENT_ENCODING]
                        else:
                            content_encoding = None

                        if HEADER_CONTENT_SECURITY_POLICY in flow.response.headers:
                            csp = ensure_bubble_csp(flow.response.headers[HEADER_CONTENT_SECURITY_POLICY], req_id)
                            flow.response.headers[HEADER_CONTENT_SECURITY_POLICY] = csp
                        else:
                            csp = None

                        content_length_value = flow.response.headers.pop(HEADER_CONTENT_LENGTH, None)
                        if log_debug:
                            bubble_log.debug(prefix+'content_encoding='+repr(content_encoding) + ', content_type='+repr(content_type))

                        if flex_flow is not None:
                            # flex flows with errors are handled before we get here
                            bubble_log.info(prefix+' filtering async stream, starting with flow.response.stream = '+repr(flow.response.stream))
                            flow.response.stream.filter_chunk = async_filter_chunk(flow.response.stream, flow, req_id, user_agent, content_encoding, content_type, csp)
                        else:
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
                                            if log_error:
                                                bubble_log.error(prefix+'error finding server_conn for path '+path+'. last ctx has no further ctx. type='+str(type(ctx))+' vars='+str(vars(ctx)))
                                            return
                                    if not hasattr(ctx, 'server_conn'):
                                        if log_error:
                                            bubble_log.error(prefix+'error finding server_conn for path '+path+'. ctx type='+str(type(ctx))+' vars='+str(vars(ctx)))
                                        return
                                    content_length = int(content_length_value)
                                    if ctx.server_conn.rfile:
                                        ctx.server_conn.rfile.fake_chunks = content_length
                                    add_flow_ctx(flow, CTX_CONTENT_LENGTH, content_length)
                                    add_flow_ctx(flow, CTX_CONTENT_LENGTH_SENT, 0)

                    else:
                        if log_debug:
                            bubble_log.debug(prefix+'no matchers, passing thru: '+path)
                        pass
                else:
                    if log_warning:
                        bubble_log.warning(prefix+'no '+HEADER_CONTENT_TYPE+' header, passing thru: '+path)
                    pass
            else:
                if log_debug:
                    bubble_log.debug(prefix+'no '+CTX_BUBBLE_MATCHERS+' in ctx, passing thru: '+path)
                pass
