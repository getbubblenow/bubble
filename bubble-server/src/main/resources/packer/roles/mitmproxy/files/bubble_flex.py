#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
import asyncio

from mitmproxy.proxy.protocol.async_stream_body import AsyncStreamBody

from mitmproxy import http
from mitmproxy.net.http import headers as nheaders
from mitmproxy.proxy.protocol.request_capture import RequestCapture

from bubble_api import bubble_get_flex_router, collect_response_headers, async_client, async_stream, cleanup_async, \
    HEADER_TRANSFER_ENCODING, HEADER_CONTENT_LENGTH, HEADER_CONTENT_TYPE

import logging
from logging import INFO, DEBUG, WARNING, ERROR, CRITICAL


bubble_log = logging.getLogger(__name__)

FLEX_TIMEOUT = 20


class FlexFlow(RequestCapture):
    flex_host: None
    mitm_flow: None
    router: None
    request_chunks: None
    response: None
    response_stream: None

    def __init__(self, flex_host, mitm_flow, router):
        super().__init__()
        self.flex_host = flex_host
        self.mitm_flow = mitm_flow
        self.router = router
        mitm_flow.request.stream = self
        mitm_flow.response = http.HTTPResponse(http_version='HTTP/1.1',
                                               status_code=523,
                                               reason='FlexFlow Not Initialized',
                                               headers={},
                                               content=None)

    def is_error(self):
        return 'error_html' in self.router and self.router['error_html'] and len(self.router['error_html']) > 0

    def capture(self, chunks):
        self.request_chunks = chunks


def process_no_flex(flex_flow):

    flow = flex_flow.mitm_flow

    response_headers = nheaders.Headers()
    response_headers[HEADER_CONTENT_TYPE] = 'text/html'
    response_headers[HEADER_CONTENT_LENGTH] = str(len(flex_flow.router['error_html']))

    flow.response = http.HTTPResponse(http_version='HTTP/1.1',
                                      status_code=200,
                                      reason='OK',
                                      headers=response_headers,
                                      content=None)
    flex_flow.response_stream = lambda chunks: error_html
    error_html = flex_flow.router['error_html']
    if bubble_log.isEnabledFor(DEBUG):
        bubble_log.debug('process_no_flex: no router found, returning error_html')
    return flex_flow


def new_flex_flow(client_addr, flex_host, flow):
    router = bubble_get_flex_router(client_addr, flex_host)
    if router is None or 'auth' not in router:
        if bubble_log.isEnabledFor(ERROR):
            bubble_log.error('new_flex_flow: no flex router for host: '+flex_host)
        return None

    if bubble_log.isEnabledFor(INFO):
        bubble_log.info('new_flex_flow: found router '+repr(router)+' for flex host: '+flex_host)
    return FlexFlow(flex_host, flow, router)


def process_flex(flex_flow):

    if flex_flow.is_error():
        if bubble_log.isEnabledFor(DEBUG):
            bubble_log.debug('process_flex: no router found, returning default flow')
        return process_no_flex(flex_flow)
    else:
        if bubble_log.isEnabledFor(DEBUG):
            bubble_log.debug('process_flex: using router: '+repr(flex_flow.router))

    flex_host = flex_flow.flex_host
    flow = flex_flow.mitm_flow
    router = flex_flow.router

    # build the request URL
    method = flow.request.method
    scheme = flow.request.scheme
    url = scheme + '://' + flex_host + flow.request.path

    # copy request headers
    # see: https://stackoverflow.com/questions/16789840/python-requests-cant-send-multiple-headers-with-same-key
    request_headers = {}
    for name in flow.request.headers:
        if name in request_headers:
            request_headers[name] = request_headers[name] + "," + flow.request.headers[name]
        else:
            request_headers[name] = flow.request.headers[name]

    # setup proxies
    proxy_url = router['proxyUrl']
    proxies = {"http": proxy_url, "https": proxy_url}

    if bubble_log.isEnabledFor(DEBUG):
        bubble_log.debug('process_flex: sending flex request for '+method+' '+url+' to '+proxy_url)

    loop = asyncio.new_event_loop()
    client = async_client(proxies=proxies, timeout=30)
    try:
        response = async_stream(client, 'process_flex', url,
                                method=method,
                                headers=request_headers,
                                timeout=30,
                                data=async_chunk_iter(flex_flow.request_chunks),
                                loop=loop)
        if bubble_log.isEnabledFor(DEBUG):
            bubble_log.debug('process_flex: response returned HTTP status '+str(response.status_code)+' for '+url)
    except Exception as e:
        if bubble_log.isEnabledFor(ERROR):
            bubble_log.error('process_flex: error sending request to '+url+': '+repr(e))
        # todo: catch TimeoutException, try another flex router; remember the last router that works for this client_addr
        return None

    if response is None:
        return None

    # Status line
    http_version = response.http_version

    # Headers -- copy from requests dict to Headers multimap
    # Remove Content-Length and Content-Encoding, we will rechunk the output
    response_headers = collect_response_headers(response, [HEADER_CONTENT_LENGTH, HEADER_TRANSFER_ENCODING])

    # Construct the real response
    flow.response = http.HTTPResponse(http_version=http_version,
                                      status_code=response.status_code,
                                      reason=response.reason_phrase,
                                      headers=response_headers,
                                      content=None)

    # If Content-Length header did not exist, or did exist and was > 0, then chunk the content
    content_length = None
    if HEADER_CONTENT_LENGTH in response.headers:
        content_length = response.headers[HEADER_CONTENT_LENGTH]
    if response.status_code // 100 != 2:
        response_headers[HEADER_CONTENT_LENGTH] = '0'
        flow.response.stream = lambda chunks: []

    elif content_length is None or int(content_length) > 0:
        response_headers[HEADER_TRANSFER_ENCODING] = 'chunked'
        flow.response.stream = AsyncStreamBody(owner=client, loop=loop, chunks=response.aiter_raw(), finalize=cleanup_async(url, loop, client, response))

    else:
        response_headers[HEADER_CONTENT_LENGTH] = '0'
        flow.response.stream = lambda chunks: []

    # Apply filters
    if bubble_log.isEnabledFor(INFO):
        bubble_log.info('process_flex: successfully requested url '+url+' from flex router, proceeding...')

    flex_flow.response = response
    return flex_flow


async def async_chunk_iter(chunks):
    for chunk in chunks:
        yield chunk
