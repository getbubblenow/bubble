#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Parts of this are borrowed from tls_passthrough.py in the mitmproxy project. The mitmproxy license is reprinted here:
#
# Copyright (c) 2013, Aldo Cortesi. All rights reserved.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#
from mitmproxy.proxy.protocol import TlsLayer, RawTCPLayer
from mitmproxy.exceptions import TlsProtocolException
from mitmproxy.net import tls as net_tls

from bubble_api import bubble_log, bubble_conn_check, bubble_activity_log, redis_set
from bubble_config import bubble_sage_host, bubble_sage_ip4, bubble_sage_ip6, cert_validation_host
import redis
import json
import subprocess
import traceback

REDIS_DNS_PREFIX = 'bubble_dns_'
REDIS_CONN_CHECK_PREFIX = 'bubble_conn_check_'
REDIS_CHECK_DURATION = 60 * 60  # 1 hour timeout
REDIS_KEY_DEVICE_SECURITY_LEVEL_PREFIX = 'bubble_device_security_level_'  # defined in StandardDeviceIdService

REDIS = redis.Redis(host='127.0.0.1', port=6379, db=0)

FORCE_PASSTHRU = {'passthru': True}
FORCE_BLOCK = {'block': True}

# Matches enums in DeviceSecurityLevel
SEC_MAX = 'maximum'
SEC_STD = 'standard'
SEC_BASIC = 'basic'
SEC_OFF = 'disabled'

local_ips = None


def get_device_security_level(client_addr):
    level = REDIS.get(REDIS_KEY_DEVICE_SECURITY_LEVEL_PREFIX+client_addr)
    if level is None:
        return SEC_MAX
    return level.decode()


def get_local_ips():
    global local_ips
    if local_ips is None:
        local_ips = []
        for ip in subprocess.check_output(['hostname', '-I']).split():
            local_ips.append(ip.decode())
    return local_ips


def is_sage_request(ip, fqdns):
    return ip == bubble_sage_ip4 or ip == bubble_sage_ip6 or bubble_sage_host in fqdns


def conn_check_cache_prefix(client_addr, server_addr):
    return REDIS_CONN_CHECK_PREFIX + client_addr + '_' + server_addr


def fqdns_for_addr(addr):
    prefix = REDIS_DNS_PREFIX + addr
    keys = REDIS.keys(prefix + '_*')
    if keys is None or len(keys) == 0:
        bubble_log('fqdns_for_addr: no FQDN found for addr '+str(addr)+', checking raw addr')
        return ''
    fqdns = []
    for k in keys:
        fqdn = k.decode()[len(prefix)+1:]
        fqdns.append(fqdn)
    return fqdns


class TlsBlock(TlsLayer):
    """
    Monkey-patch __call__ to drop this connection entirely
    """
    def __call__(self):
        bubble_log('TlsBlock: blocking')
        return


class TlsFeedback(TlsLayer):
    """
    Monkey-patch _establish_tls_with_client to get feedback if TLS could be established
    successfully on the client connection (which may fail due to cert pinning).
    """
    def _establish_tls_with_client(self):
        client_address = self.client_conn.address[0]
        server_address = self.server_conn.address[0]
        security_level = get_device_security_level(client_address)
        try:
            super(TlsFeedback, self)._establish_tls_with_client()

        except TlsProtocolException as e:
            tb = traceback.format_exc()
            if 'OpenSSL.SSL.ZeroReturnError' in tb:
                bubble_log('_establish_tls_with_client: TLS error for '+str(server_address)+', ignoring SSL zero return error for client '+client_address)
                return

            elif self.fqdns is not None and len(self.fqdns) > 0:
                for fqdn in self.fqdns:
                    cache_key = conn_check_cache_prefix(client_address, fqdn)
                    if security_level == SEC_MAX:
                        redis_set(cache_key, json.dumps({'fqdns': [fqdn], 'addr': server_address, 'passthru': False, 'block': True, 'reason': 'tls_failure'}), ex=REDIS_CHECK_DURATION)
                        bubble_log('_establish_tls_with_client: TLS error for '+str(server_address)+', enabling block (security_level=maximum) for client '+client_address+' with cache_key='+cache_key+' and fqdn='+fqdn+': '+repr(e))
                    else:
                        redis_set(cache_key, json.dumps({'fqdns': [fqdn], 'addr': server_address, 'passthru': True, 'reason': 'tls_failure'}), ex=REDIS_CHECK_DURATION)
                        bubble_log('_establish_tls_with_client: TLS error for '+str(server_address)+', enabling passthru for client '+client_address+' with cache_key='+cache_key+' and fqdn='+fqdn+': '+repr(e))
            else:
                cache_key = conn_check_cache_prefix(client_address, server_address)
                if security_level == SEC_MAX:
                    redis_set(cache_key, json.dumps({'fqdns': None, 'addr': server_address, 'passthru': False, 'block': True, 'reason': 'tls_failure'}), ex=REDIS_CHECK_DURATION)
                    bubble_log('_establish_tls_with_client: TLS error for '+str(server_address)+', enabling block (security_level=maximum) for client '+client_address+' with cache_key='+cache_key+' and server_address='+server_address+': '+repr(e))
                else:
                    redis_set(cache_key, json.dumps({'fqdns': None, 'addr': server_address, 'passthru': True, 'reason': 'tls_failure'}), ex=REDIS_CHECK_DURATION)
                    bubble_log('_establish_tls_with_client: TLS error for '+str(server_address)+', enabling passthru for client '+client_address+' with cache_key='+cache_key+' and server_address='+server_address+': '+repr(e))
            raise e


def check_bubble_connection(client_addr, server_addr, fqdns, security_level):
    check_response = bubble_conn_check(client_addr, server_addr, fqdns, security_level)
    if check_response is None or check_response == 'error':
        if security_level == SEC_MAX:
            bubble_log('check_bubble_connection: bubble API returned ' + str(check_response) +' for FQDN/addr ' + str(fqdns) +'/' + str(server_addr) + ', security_level=maximum, returning Block')
            return {'fqdns': fqdns, 'addr': server_addr, 'passthru': False, 'block': True, 'reason': 'bubble_error'}
        else:
            bubble_log('check_bubble_connection: bubble API returned ' + str(check_response) +' for FQDN/addr ' + str(fqdns) +'/' + str(server_addr) + ', returning True')
            return {'fqdns': fqdns, 'addr': server_addr, 'passthru': True, 'reason': 'bubble_error'}

    elif check_response == 'passthru':
        bubble_log('check_bubble_connection: bubble API returned ' + str(check_response) +' for FQDN/addr ' + str(fqdns) +'/' + str(server_addr) + ', returning True')
        return {'fqdns': fqdns, 'addr': server_addr, 'passthru': True, 'reason': 'bubble_passthru'}

    elif check_response == 'block':
        bubble_log('check_bubble_connection: bubble API returned ' + str(check_response) +' for FQDN/addr ' + str(fqdns) +'/' + str(server_addr) + ', returning Block')
        return {'fqdns': fqdns, 'addr': server_addr, 'passthru': False, 'block': True, 'reason': 'bubble_block'}

    else:
        bubble_log('check_bubble_connection: bubble API returned ' + str(check_response) +' for FQDN/addr ' + str(fqdns) +'/' + str(server_addr) + ', returning False')
        return {'fqdns': fqdns, 'addr': server_addr, 'passthru': False, 'reason': 'bubble_no_passthru'}


def check_connection(client_addr, server_addr, fqdns, security_level):
    if fqdns and len(fqdns) == 1:
        cache_key = conn_check_cache_prefix(client_addr, fqdns[0])
    else:
        cache_key = conn_check_cache_prefix(client_addr, server_addr)
    prefix = 'check_connection: ip=' + str(server_addr) + ' (fqdns=' + str(fqdns) + ') cache_key=' + cache_key + ': '

    check_json = REDIS.get(cache_key)
    if check_json is None or len(check_json) == 0:
        bubble_log(prefix+'not in redis or empty, calling check_bubble_connection against fqdns='+str(fqdns))
        check_response = check_bubble_connection(client_addr, server_addr, fqdns, security_level)
        bubble_log(prefix+'check_bubble_connection('+str(fqdns)+') returned '+str(check_response)+", storing in redis...")
        redis_set(cache_key, json.dumps(check_response), ex=REDIS_CHECK_DURATION)

    else:
        bubble_log(prefix+'found check_json='+str(check_json)+', touching key in redis')
        check_response = json.loads(check_json)
        REDIS.touch(cache_key)
    bubble_log(prefix+'returning '+str(check_response))
    return check_response


def next_layer(next_layer):
    if isinstance(next_layer, TlsLayer) and next_layer._client_tls:
        client_hello = net_tls.ClientHello.from_file(next_layer.client_conn.rfile)
        client_addr = next_layer.client_conn.address[0]
        server_addr = next_layer.server_conn.address[0]

        if client_hello.sni:
            fqdn = client_hello.sni.decode()
            bubble_log('next_layer: using fqdn in SNI: '+ fqdn)
            fqdns = [ fqdn ]
        else:
            fqdns = fqdns_for_addr(server_addr)
            bubble_log('next_layer: NO fqdn in sni, using fqdns from DNS: '+ str(fqdns))
        next_layer.fqdns = fqdns
        no_fqdns = fqdns is None or len(fqdns) == 0
        security_level = get_device_security_level(client_addr)
        if server_addr in get_local_ips():
            bubble_log('next_layer: enabling passthru for LOCAL server='+server_addr+' regardless of security_level='+security_level+' for client='+client_addr)
            check = FORCE_PASSTHRU

        elif is_sage_request(server_addr, fqdns):
            bubble_log('next_layer: enabling passthru for SAGE server='+server_addr+' regardless of security_level='+security_level+' for client='+client_addr)
            check = FORCE_PASSTHRU

        elif security_level == SEC_OFF or security_level == SEC_BASIC:
            bubble_log('next_layer: enabling passthru for server='+server_addr+' because security_level='+security_level+' for client='+client_addr)
            check = FORCE_PASSTHRU

        elif fqdns is not None and len(fqdns) == 1 and cert_validation_host == fqdns[0]:
            bubble_log('next_layer: NOT enabling passthru for server='+server_addr+' because fqdn is cert_validation_host ('+cert_validation_host+') for client='+client_addr)
            return

        elif security_level == SEC_STD and no_fqdns:
            bubble_log('next_layer: enabling passthru for server='+server_addr+' because no FQDN found and security_level='+security_level+' for client='+client_addr)
            check = FORCE_PASSTHRU

        elif security_level == SEC_MAX and no_fqdns:
            bubble_log('next_layer: disabling passthru (no TlsFeedback) for server='+server_addr+' because no FQDN found and security_level='+security_level+' for client='+client_addr)
            check = FORCE_BLOCK

        else:
            bubble_log('next_layer: calling check_connection for server='+server_addr+', fqdns='+str(fqdns)+', client='+client_addr+' with security_level='+security_level)
            check = check_connection(client_addr, server_addr, fqdns, security_level)

        if check is None or ('passthru' in check and check['passthru']):
            bubble_log('next_layer: enabling passthru for server=' + server_addr+', fqdns='+str(fqdns))
            bubble_activity_log(client_addr, server_addr, 'tls_passthru', fqdns)
            next_layer_replacement = RawTCPLayer(next_layer.ctx, ignore=True)
            next_layer.reply.send(next_layer_replacement)

        elif 'block' in check and check['block']:
            bubble_log('next_layer: enabling block for server=' + server_addr+', fqdns='+str(fqdns))
            bubble_activity_log(client_addr, server_addr, 'conn_block', fqdns)
            next_layer.__class__ = TlsBlock

        else:
            bubble_log('next_layer: disabling passthru (with TlsFeedback) for client_addr='+client_addr+', server_addr='+server_addr+', fqdns='+str(fqdns))
            bubble_activity_log(client_addr, server_addr, 'tls_intercept', fqdns)
            next_layer.__class__ = TlsFeedback
