import requests
import urllib
from bubble_config import bubble_port
from bubble_api import HEADER_BUBBLE_MATCHERS, HEADER_BUBBLE_DEVICE, bubble_log
import json
import uuid
import time

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


def responseheaders(flow):
    if (HEADER_BUBBLE_MATCHERS in flow.request.headers
            and HEADER_BUBBLE_DEVICE in flow.request.headers):
        req_id = str(uuid.uuid4()) + '.' + str(time.time())
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
