import requests
import urllib
import json
from mitmproxy import http
from mitmproxy.net.http import Headers
from bubble_config import bubble_port, bubble_host_alias
from bubble_api import HEADER_BUBBLE_MATCHERS, HEADER_BUBBLE_DEVICE, HEADER_BUBBLE_ABORT, BUBBLE_URI_PREFIX, HEADER_BUBBLE_REQUEST_ID, bubble_log

BUFFER_SIZE = 4096
HEADER_CONTENT_TYPE = 'Content-Type'
HEADER_CONTENT_ENCODING = 'Content-Encoding'
BINARY_DATA_HEADER = {HEADER_CONTENT_TYPE: 'application/octet-stream'}


def filter_chunk(chunk, req_id, content_encoding=None, content_type=None, device=None, matchers=None):
    url = 'http://127.0.0.1:' + bubble_port + '/api/filter/apply/' + req_id
    if device and matchers and content_type and chunk:
        url = (url
               + '?device=' + device
               + '&matchers=' + urllib.parse.quote_plus(matchers)
               + '&contentType=' + urllib.parse.quote_plus(content_type))
        if content_encoding:
            url = url + '&encoding=' + urllib.parse.quote_plus(content_encoding)
    elif not chunk:
        url = url + '?last=true'
    bubble_log("filter_chunk: url="+url)

    response = requests.post(url, data=chunk, headers=BINARY_DATA_HEADER)
    if not response.ok:
        err_message = "filter_chunk: Error fetching " + url + ", HTTP status " + str(response.status_code)
        bubble_log(err_message)
        return b''

    return response.content


def bubble_filter_chunks(chunks, req_id, content_encoding, content_type, device, matchers):
    """
    chunks is a generator that can be used to iterate over all chunks.
    """
    first = True
    for chunk in chunks:
        if first:
            yield filter_chunk(chunk, req_id, content_encoding, content_type, device, matchers)
            first = False
        else:
            yield filter_chunk(chunk, req_id)
    yield filter_chunk(None, req_id)  # get the last bits of data


def bubble_modify(req_id, content_encoding, content_type, device, matchers):
    return lambda chunks: bubble_filter_chunks(chunks, req_id, content_encoding, content_type, device, matchers)

def send_bubble_response(response):
    for chunk in response.iter_content(8192):
        yield chunk


def responseheaders(flow):

    if flow.request.path and flow.request.path.startswith(BUBBLE_URI_PREFIX):
        bubble_log('responseheaders: request path starts with '+BUBBLE_URI_PREFIX+', sending to bubble')
        uri = 'https://' + bubble_host_alias + ':1443/' + flow.request.path[len(BUBBLE_URI_PREFIX):]
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

    elif HEADER_BUBBLE_ABORT in flow.request.headers:
        abort_code = int(flow.request.headers[HEADER_BUBBLE_ABORT])
        bubble_log('responseheaders: aborting request with HTTP status '+abort_code)
        flow.response.headers = Headers()
        flow.response.status_code = abort_code
        flow.response.stream = lambda chunks: None

    elif (HEADER_BUBBLE_MATCHERS in flow.request.headers
            and HEADER_BUBBLE_DEVICE in flow.request.headers):
        req_id = flow.request.headers[HEADER_BUBBLE_REQUEST_ID]
        matchers = flow.request.headers[HEADER_BUBBLE_MATCHERS]
        device = flow.request.headers[HEADER_BUBBLE_DEVICE]
        if HEADER_CONTENT_TYPE in flow.response.headers:
            content_type = flow.response.headers[HEADER_CONTENT_TYPE]
            if matchers:
                if HEADER_CONTENT_ENCODING in flow.response.headers:
                    content_encoding = flow.response.headers[HEADER_CONTENT_ENCODING]
                else:
                    content_encoding = None
                bubble_log("responseheaders: content_encoding="+repr(content_encoding)
                           + ", content_type="+repr(content_type)
                           +", req_id=" + req_id
                           + ", device=" + device
                           + ", matchers: " + repr(json.loads(matchers)))
                flow.response.stream = bubble_modify(req_id,
                                                     content_encoding,
                                                     content_type,
                                                     device,
                                                     matchers)
            else:
                bubble_log("responseheaders: no matchers, passing thru")
                pass
        else:
            bubble_log("responseheaders: no "+HEADER_CONTENT_TYPE +" header, passing thru")
            pass
    else:
        bubble_log("responseheaders: no "+HEADER_BUBBLE_MATCHERS +" header, passing thru")
        pass
