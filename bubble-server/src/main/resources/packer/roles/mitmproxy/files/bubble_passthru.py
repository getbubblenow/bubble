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
import redis
import json

REDIS_DNS_PREFIX = 'bubble_dns_'
REDIS_PASSTHRU_PREFIX = 'bubble_passthru_'
REDIS_PASSTHRU_DURATION = 60 * 60  # 1 hour timeout on passthru

REDIS = redis.Redis(host='127.0.0.1', port=6379, db=0)


def passthru_cache_prefix(client_addr, server_addr):
    return REDIS_PASSTHRU_PREFIX + client_addr + '_' + server_addr


class TlsFeedback(TlsLayer):
    """
    Monkey-patch _establish_tls_with_client to get feedback if TLS could be established
    successfully on the client connection (which may fail due to cert pinning).
    """
    def _establish_tls_with_client(self):
        client_address = self.client_conn.address[0]
        server_address = self.server_conn.address[0]
        try:
            super(TlsFeedback, self)._establish_tls_with_client()
        except TlsProtocolException as e:
            bubble_log('_establish_tls_with_client: TLS error for '+repr(server_address)+', enabling passthru')
            cache_key = passthru_cache_prefix(client_address, server_address)
            fqdn = fqdn_for_addr(server_address)
            redis_set(cache_key, json.dumps({'fqdn': fqdn, 'addr': server_address, 'passthru': True}), ex=REDIS_PASSTHRU_DURATION)
            raise e


def fqdn_for_addr(addr):
    fqdn = REDIS.get(REDIS_DNS_PREFIX + addr)
    if fqdn is None or len(fqdn) == 0:
        bubble_log('fqdn_for_addr: no FQDN found for addr '+repr(addr)+', checking raw addr')
        fqdn = b''
    return fqdn.decode()


def check_bubble_passthru(remote_addr, addr, fqdn):
    passthru = bubble_passthru(remote_addr, addr, fqdn)
    if passthru is None:
        return None
    if passthru:
        bubble_log('check_bubble_passthru: bubble_passthru returned True for FQDN/addr '+repr(fqdn)+'/'+repr(addr)+', returning True')
        return {'fqdn': fqdn, 'addr': addr, 'passthru': True}
    bubble_log('check_bubble_passthru: bubble_passthru returned False for FQDN/addr '+repr(fqdn)+'/'+repr(addr)+', returning False')
    return {'fqdn': fqdn, 'addr': addr, 'passthru': False}


def should_passthru(remote_addr, addr):
    prefix = 'should_passthru: '+repr(addr)+' '
    bubble_log(prefix+'starting...')
    cache_key = passthru_cache_prefix(remote_addr, addr)
    passthru_json = REDIS.get(cache_key)
    if passthru_json is None or len(passthru_json) == 0:
        bubble_log(prefix+' not in redis or empty, calling check_bubble_passthru...')
        fqdn = fqdn_for_addr(addr)
        if fqdn is None or len(fqdn) == 0:
            fqdn = 'NONE'
        passthru = check_bubble_passthru(remote_addr, addr, fqdn)
        bubble_log(prefix+'check_bubble_passthru returned '+repr(passthru)+", storing in redis...")
        if passthru is not None:
            redis_set(cache_key, json.dumps(passthru), ex=REDIS_PASSTHRU_DURATION)
    else:
        bubble_log('found passthru_json='+str(passthru_json)+', touching key in redis')
        passthru = json.loads(passthru_json)
        REDIS.touch(cache_key)
    bubble_log(prefix+'returning '+repr(passthru))
    return passthru


def next_layer(next_layer):
    if isinstance(next_layer, TlsLayer) and next_layer._client_tls:
        client_address = next_layer.client_conn.address[0]
        server_address = next_layer.server_conn.address[0]
        passthru = should_passthru(client_address, server_address)
        if passthru is None or passthru['passthru']:
            bubble_log('next_layer: TLS passthru for ' + repr(next_layer.server_conn.address))
            if passthru is not None and 'fqdn' in passthru:
                bubble_activity_log(client_address, server_address, 'tls_passthru', passthru['fqdn'])
            next_layer_replacement = RawTCPLayer(next_layer.ctx, ignore=True)
            next_layer.reply.send(next_layer_replacement)
        else:
            bubble_activity_log(client_address, server_address, 'tls_intercept', passthru['fqdn'])
            next_layer.__class__ = TlsFeedback
