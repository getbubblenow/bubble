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

from bubble_api import bubble_log, bubble_passthru, bubble_activity_log, redis_set
from bubble_config import bubble_sage_host, bubble_sage_ip4, bubble_sage_ip6
import redis
import json
import subprocess

REDIS_DNS_PREFIX = 'bubble_dns_'
REDIS_PASSTHRU_PREFIX = 'bubble_passthru_'
REDIS_KEY_DEVICE_SECURITY_LEVEL_PREFIX = 'bubble_device_security_level_'  # defined in StandardDeviceIdService
REDIS_PASSTHRU_DURATION = 60 * 60  # 1 hour timeout on passthru

REDIS = redis.Redis(host='127.0.0.1', port=6379, db=0)

FORCE_PASSTHRU = {'passthru': True}

local_ips = None


def get_device_security_level(client_addr):
    level = REDIS.get(REDIS_KEY_DEVICE_SECURITY_LEVEL_PREFIX+client_addr)
    if level is None:
        return 'maximum'
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


def passthru_cache_prefix(client_addr, server_addr):
    return REDIS_PASSTHRU_PREFIX + client_addr + '_' + server_addr


def fqdns_for_addr(addr):
    prefix = REDIS_DNS_PREFIX + addr
    keys = REDIS.keys(prefix + '_*')
    if keys is None or len(keys) == 0:
        bubble_log('fqdns_for_addr: no FQDN found for addr '+repr(addr)+', checking raw addr')
        return ''
    fqdns = []
    for k in keys:
        fqdn = k.decode()[len(prefix)+1:]
        fqdns.append(fqdn)
    return fqdns


class TlsFeedback(TlsLayer):
    """
    Monkey-patch _establish_tls_with_client to get feedback if TLS could be established
    successfully on the client connection (which may fail due to cert pinning).
    """
    def _establish_tls_with_client(self):
        client_address = self.client_conn.address[0]
        server_address = self.server_conn.address[0]
        fqdns = fqdns_for_addr(server_address)
        try:
            super(TlsFeedback, self)._establish_tls_with_client()

        except TlsProtocolException as e:
            cache_key = passthru_cache_prefix(client_address, server_address)
            bubble_log('_establish_tls_with_client: TLS error for '+repr(server_address)+', enabling passthru for client '+client_address+' with cache_key='+cache_key)
            redis_set(cache_key, json.dumps({'fqdns': fqdns, 'addr': server_address, 'passthru': True}), ex=REDIS_PASSTHRU_DURATION)
            raise e


def check_bubble_passthru(client_addr, addr, fqdns):
    passthru = bubble_passthru(client_addr, addr, fqdns)
    if passthru is None or passthru:
        bubble_log('check_bubble_passthru: bubble_passthru returned '+repr(passthru)+' for FQDN/addr '+repr(fqdns)+'/'+repr(addr)+', returning True')
        return {'fqdns': fqdns, 'addr': addr, 'passthru': True}
    bubble_log('check_bubble_passthru: bubble_passthru returned False for FQDN/addr '+repr(fqdns)+'/'+repr(addr)+', returning False')
    return {'fqdns': fqdns, 'addr': addr, 'passthru': False}


def should_passthru(client_addr, addr, fqdns):
    cache_key = passthru_cache_prefix(client_addr, addr)
    prefix = 'should_passthru: ip='+repr(addr)+' (fqdns='+repr(fqdns)+') cache_key='+cache_key+': '

    passthru_json = REDIS.get(cache_key)
    if passthru_json is None or len(passthru_json) == 0:
        bubble_log(prefix+'not in redis or empty, calling check_bubble_passthru against fqdns='+repr(fqdns))
        passthru = check_bubble_passthru(client_addr, addr, fqdns)
        bubble_log(prefix+'check_bubble_passthru('+repr(fqdns)+') returned '+repr(passthru)+", storing in redis...")
        redis_set(cache_key, json.dumps(passthru), ex=REDIS_PASSTHRU_DURATION)

    else:
        bubble_log(prefix+'found passthru_json='+str(passthru_json)+', touching key in redis')
        passthru = json.loads(passthru_json)
        REDIS.touch(cache_key)
    bubble_log(prefix+'returning '+repr(passthru))
    return passthru


def next_layer(next_layer):
    if isinstance(next_layer, TlsLayer) and next_layer._client_tls:
        client_addr = next_layer.client_conn.address[0]
        server_addr = next_layer.server_conn.address[0]

        fqdns = fqdns_for_addr(server_addr)
        no_fqdns = fqdns is None or len(fqdns) == 0
        security_level = get_device_security_level(client_addr)
        if server_addr in get_local_ips():
            bubble_log('next_layer: enabling passthru for LOCAL server='+server_addr+' regardless of security_level='+security_level+' for client='+client_addr)
            passthru = FORCE_PASSTHRU

        elif is_sage_request(server_addr, fqdns):
            bubble_log('next_layer: enabling passthru for SAGE server='+server_addr+' regardless of security_level='+security_level+' for client='+client_addr)
            passthru = FORCE_PASSTHRU

        elif security_level == 'disabled' or security_level == 'basic':
            bubble_log('next_layer: enabling passthru for server='+server_addr+' because security_level='+security_level+' for client='+client_addr)
            passthru = FORCE_PASSTHRU

        elif security_level == 'standard' and no_fqdns:
            bubble_log('next_layer: enabling passthru for server='+server_addr+' because no FQDN found and security_level='+security_level+' for client='+client_addr)
            passthru = FORCE_PASSTHRU

        elif security_level == 'maximum' and no_fqdns:
            bubble_log('next_layer: disabling passthru (no TlsFeedback) for server='+server_addr+' because no FQDN found and security_level='+security_level+' for client='+client_addr)
            return

        else:
            bubble_log('next_layer: checking should_passthru for server='+server_addr+', client='+client_addr+' with security_level='+security_level)
            passthru = should_passthru(client_addr, server_addr, fqdns)

        if passthru is None or passthru['passthru']:
            bubble_log('next_layer: enabling passthru for ' + repr(next_layer.server_conn.address))
            bubble_activity_log(client_addr, server_addr, 'tls_passthru', fqdns)
            next_layer_replacement = RawTCPLayer(next_layer.ctx, ignore=True)
            next_layer.reply.send(next_layer_replacement)
        else:
            bubble_log('next_layer: disabling passthru (with TlsFeedback) for client_addr='+client_addr+', server_addr='+server_addr)
            bubble_activity_log(client_addr, server_addr, 'tls_intercept', fqdns)
            next_layer.__class__ = TlsFeedback
