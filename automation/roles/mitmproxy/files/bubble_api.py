import requests
import traceback
import sys
from bubble_config import bubble_network, bubble_port

HEADER_USER_AGENT = 'User-Agent'
HEADER_REFERER = 'Referer'

HEADER_BUBBLE_MATCHERS='X-Bubble-Matchers'
HEADER_BUBBLE_DEVICE='X-Bubble-Device'
BUBBLE_URI_PREFIX='/__bubble/'

def bubble_log (message):
    print(message, file=sys.stderr)

# todo: cache responses by remote_addr+host for a limited time (1 minute?)
def bubble_matchers (remote_addr, flow, host):
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
        referer = flow.request.headers[HEADER_REFERER]

    try:
        data = {
            'fqdn': host,
            'uri': flow.request.path,
            'userAgent': user_agent,
            'referer': referer,
            'remoteAddr': remote_addr
        }
        response = requests.post('http://127.0.0.1:'+bubble_port+'/api/filter/matchers', headers=headers, json=data)
        if response.ok:
            return response.json()
        bubble_log('bubble_matchers response not OK, returning empty matchers array: '+str(response.status_code)+' / '+repr(response.text))
    except Exception as e:
        bubble_log('bubble_matchers API call failed: '+repr(e))
        traceback.print_exc()
    return None
