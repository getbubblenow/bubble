import requests
import traceback
import sys
from bubble_config import bubble_network, bubble_port

HEADER_USER_AGENT = 'User-Agent'
HEADER_REFERER = 'Referer'

HEADER_BUBBLE_MATCHERS='X-Bubble-Matchers'
HEADER_BUBBLE_DEVICE='X-Bubble-Device'
HEADER_BUBBLE_ABORT='X-Bubble-Abort'
HEADER_BUBBLE_REQUEST_ID='X-Bubble-RequestId'
BUBBLE_URI_PREFIX='/__bubble/'

def bubble_log (message):
    print(message, file=sys.stderr)


def bubble_matchers (req_id, remote_addr, flow, host):
    headers = {
        'X-Forwarded-For': remote_addr,
        'Accept' : 'application/json',
        'Content-Type': 'application/json'
    }
    if HEADER_USER_AGENT not in flow.request.headers:
        bubble_log('bubble_matchers: no User-Agent header, setting to UNKNOWN')
        user_agent = 'UNKNOWN'
    else:
        user_agent = flow.request.headers[HEADER_USER_AGENT]

    if HEADER_REFERER not in flow.request.headers:
        bubble_log('bubble_matchers: no Referer header, setting to NONE')
        referer = 'NONE'
    else:
        try:
            referer = flow.request.headers[HEADER_REFERER].encode().decode()
        except Exception as e:
            bubble_log('bubble_matchers: error parsing Referer header: '+repr(e))
            referer = 'NONE'

    try:
        data = {
            'requestId': req_id,
            'fqdn': host,
            'uri': flow.request.path,
            'userAgent': user_agent,
            'referer': referer,
            'remoteAddr': remote_addr
        }
        response = requests.post('http://127.0.0.1:'+bubble_port+'/api/filter/matchers/'+req_id, headers=headers, json=data)
        if response.ok:
            return response.json()
        bubble_log('bubble_matchers response not OK, returning empty matchers array: '+str(response.status_code)+' / '+repr(response.text))
    except Exception as e:
        bubble_log('bubble_matchers API call failed: '+repr(e))
        traceback.print_exc()
    return None
