#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
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

from bubble_api import bubble_log, bubble_passthru
import redis

REDIS_DNS_PREFIX = 'bubble_dns_'
REDIS_PASSTHRU_PREFIX = 'bubble_passthru_'
REDIS_PASSTHRU_DURATION = 60 * 10

REDIS = redis.Redis(host='127.0.0.1', port=6379, db=0)
TLS_FAILURE_HISTORY = {}


class TlsFeedback(TlsLayer):
    """
    Monkey-patch _establish_tls_with_client to get feedback if TLS could be established
    successfully on the client connection (which may fail due to cert pinning).
    """
    def _establish_tls_with_client(self):
        server_address = self.server_conn.address
        try:
            super(TlsFeedback, self)._establish_tls_with_client()
        except TlsProtocolException as e:
            bubble_log('_establish_tls_with_client: TLS error for '+repr(server_address[0])+', enabling passthru')
            REDIS.delete(REDIS_PASSTHRU_PREFIX+server_address[0])
            TLS_FAILURE_HISTORY[server_address[0]] = True
            raise e


def check_bubble_passthru(remote_addr, addr):
    fqdn = REDIS.get(REDIS_DNS_PREFIX + addr)
    if fqdn is None or len(fqdn) == 0:
        bubble_log('check_bubble_passthru: no FQDN found for addr '+repr(addr)+', checking raw addr')
        fqdn = b''
    fqdn = fqdn.decode()
    if bubble_passthru(remote_addr, addr, fqdn):
        bubble_log('check_bubble_passthru: bubble_passthru returned True for FQDN/addr '+repr(fqdn)+'/'+repr(addr)+', returning True')
        return True
    bubble_log('check_bubble_passthru: bubble_passthru returned False for FQDN/addr '+repr(fqdn)+'/'+repr(addr)+', returning False')
    return False


def should_passthru(remote_addr, addr):
    bubble_log('should_passthru: examining addr='+repr(addr))
    if addr in TLS_FAILURE_HISTORY and TLS_FAILURE_HISTORY[addr]:
        bubble_log('should_passthru: previous failure, returning True')
        return True
    else:
        bubble_log('should_passthru: no failure (failures='+repr(TLS_FAILURE_HISTORY)+'), returning True')
    cache_key = REDIS_PASSTHRU_PREFIX + addr
    passthru_string = REDIS.get(cache_key)
    if passthru_string is None or len(passthru_string) == 0:
        passthru = check_bubble_passthru(remote_addr, addr)
        REDIS.set(cache_key, str(passthru), nx=True, ex=REDIS_PASSTHRU_DURATION)
        passthru_string = str(passthru)
    else:
        bubble_log('should_passthru: found cached value, passthru_string='+str(passthru_string))
        passthru_string = passthru_string.decode()
    bubble_log('should_passthru: returning '+str(passthru_string == 'True'))
    return passthru_string == 'True'


def next_layer(next_layer):
    if isinstance(next_layer, TlsLayer) and next_layer._client_tls:
        client_address = next_layer.client_conn.address
        server_address = next_layer.server_conn.address
        if should_passthru(client_address[0], server_address[0]):
            bubble_log('next_layer: TLS passthru for ' + repr(next_layer.server_conn.address))
            next_layer_replacement = RawTCPLayer(next_layer.ctx, ignore=True)
            next_layer.reply.send(next_layer_replacement)
        else:
            next_layer.__class__ = TlsFeedback
