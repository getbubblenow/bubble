"""
This inline script modifies a streamed response.
If you do not need streaming, see the modify_response_body example.
Be aware that content replacement isn't trivial:
    - If the transfer encoding isn't chunked, you cannot simply change the content length.
    - If you want to replace all occurrences of "foobar", make sure to catch the cases
      where one chunk ends with [...]foo" and the next starts with "bar[...].
"""
import aiohttp
import urllib
from bubble_config import bubble_port
from bubble_api import HEADER_BUBBLE_MATCHERS


BUFFER_SIZE = 4096


def stream_data(stream):
    yield stream.read_nowait(BUFFER_SIZE)


async def fetch(session, url, chunks):
    async with session.post(url, data=chunks) as response:
        if response.status != 200:
            raise RuntimeError("Error fetching "+url+", HTTP status "+str(response.status))
        return stream_data(response.content)


async def filter_chunks_with_matchers(chunks, matchers):
    rule_string = urllib.parse.quote_plus(matchers)
    url = 'http://127.0.0.1:'+bubble_port+'/api/filter/apply/' + rule_string
    async with aiohttp.ClientSession() as session:
        await fetch(session, url, chunks)


def filter_with_matchers(matchers):
    return lambda chunks: filter_chunks_with_matchers(chunks, matchers)


def responseheaders(flow):
    if HEADER_BUBBLE_MATCHERS in flow.request.headers:
        matchers = flow.request.headers[HEADER_BUBBLE_MATCHERS]
        if matchers:
            flow.response.stream = filter_with_matchers(matchers)
        else:
            pass
    else:
        pass
