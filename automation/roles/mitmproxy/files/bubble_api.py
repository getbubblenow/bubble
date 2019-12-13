from bubble_config import bubble_network, bubble_port
from mitmproxy import ctx
import requests

HEADER_BUBBLE_MATCHERS='X-Bubble-Matchers'

# todo: cache responses by remote_addr+host for a limited time (1 minute?)
def bubble_matchers (remote_addr, flow, host):
    headers = {'X-Forwarded-For': remote_addr}
    try:
        data = {
            'fqdn': host,
            'uri': flow.request.path,
            'userAgent': flow.request.headers['User-Agent'],
            'remoteAddr': flow.client_conn.address[0]
        }
        response = requests.post('http://127.0.0.1:'+bubble_port+'/api/filter/matchers', headers=headers, data=data)
        if response.ok:
            return response.json()
        ctx.log.warn('bubble_matchers returned '+response.status_code+', returning empty matchers array')
    except Exception as e:
        ctx.log.warn('bubble_matchers API call failed: '+repr(e))
    return []
