#!/usr/bin/python3
#
# Replay a stream, as if mitmproxy were sending filter requests via the filter/apply API
#
# Usage:
#
#  breplay_stream.py path_prefix
#
#      path_prefix  : some file prefix
#
# This will list all files matching the prefix, sort them, and play them back.
# These files should come in triplets: a .url file, a .headers.json file, and a .data file/
#
# To capture requests for later playback:
#   * Set debug_stream_fqdn and debug_stream_uri in mitmproxy/bubble_config.py and restart mitmproxy servers
#   * Request a matching using a device whose traffic will be routed through the mitmproxy
#   * Capture files will be written to /tmp/bubble_stream_[request-id]_chunkXXXX.[url, headers.json, data]
#
import glob
import json
import requests
import sys

HEADER_FILTER_PASSTHRU = 'X-Bubble-Passthru'


def log (message):
    print(message, file=sys.stderr, flush=True)


def replay_stream (prefix, out):
    url_files = glob.glob(prefix+'*.url')
    if url_files is None or len(url_files) == 0:
        log('No files found matching prefix: '+prefix)
        return

    url_files.sort()
    for u in url_files:
        chunk_file = replace_suffix(u, '.data')
        headers_file = replace_suffix(u, '.headers.json')
        with open(u, mode='r') as f:
            url = f.read()
        with open(headers_file, mode='r') as f:
            headers = json.load(f)
        with open(chunk_file, mode='rb') as f:
            chunk = f.read()
        log('sending '+str(len(chunk))+' bytes to '+url)
        try:
            response_data = replay_request(url, headers, chunk)
        except Exception as e:
            log('error sending filter request: '+repr(e))
            raise e
        log('received '+str(len(response_data))+' bytes')
        if len(response_data) > 0:
            out.write(response_data)


def replace_suffix(f, suffix):
    return f[0:f.rfind('.')] + suffix


def replay_request(url, headers, chunk):
    response = requests.post(url, data=chunk, headers=headers)
    if not response.ok:
        log('replay_request: Error fetching ' + url + ', HTTP status ' + str(response.status_code))
        return b''

    elif HEADER_FILTER_PASSTHRU in response.headers:
        log('replay_request: server returned X-Bubble-Passthru, not filtering subsequent requests')
        return chunk

    return response.content


if __name__ == "__main__":
    with open('/tmp/replay_response', mode='wb') as out:
        replay_stream(sys.argv[1], out)
        out.close()
