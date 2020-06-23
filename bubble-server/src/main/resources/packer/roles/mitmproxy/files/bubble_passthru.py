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
import subprocess

REDIS_DNS_PREFIX = 'bubble_dns_'
REDIS_PASSTHRU_PREFIX = 'bubble_passthru_'
REDIS_CLIENT_CERT_STATUS_PREFIX = 'bubble_cert_status_'
REDIS_PASSTHRU_DURATION = 60 * 60  # 1 hour timeout on passthru

REDIS = redis.Redis(host='127.0.0.1', port=6379, db=0)

cert_validation_host = None
local_ips = None


def get_ip_cert_status(client_addr):
    status = REDIS.get(REDIS_CLIENT_CERT_STATUS_PREFIX+client_addr)
    if status is None:
        return None
    enabled = status.decode() == "True"
    return enabled


def set_ip_cert_status(client_addr, enabled):
    REDIS.set(REDIS_CLIENT_CERT_STATUS_PREFIX+client_addr, str(enabled))
    bubble_log('set_ip_cert_status: set '+client_addr+' = '+str(enabled))


def get_local_ips():
    global local_ips
    if local_ips is None:
        local_ips = []
        for ip in subprocess.check_output(['hostname', '-I']).split():
            local_ips.append(ip.decode())
    return local_ips


def get_cert_validation_host():
    global cert_validation_host
    if cert_validation_host is None:
        cert_validation_host = REDIS.get('certValidationHost')
        if cert_validation_host is not None:
            cert_validation_host = cert_validation_host.decode()
    #     bubble_log('get_cert_validation_host: initialized to '+cert_validation_host)
    # bubble_log('get_cert_validation_host: returning '+cert_validation_host)
    return cert_validation_host


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
            if fqdns and get_cert_validation_host() in fqdns:
                # bubble_log('_establish_tls_with_client: TLS success for '+repr(server_address)+', enabling SSL interception for client '+client_address)
                set_ip_cert_status(client_address, True)

        except TlsProtocolException as e:
            cache_key = passthru_cache_prefix(client_address, server_address)
            bubble_log('_establish_tls_with_client: TLS error for '+repr(server_address)+', enabling passthru for client '+client_address+' with cache_key='+cache_key)
            if fqdns and get_cert_validation_host() in fqdns:
                set_ip_cert_status(client_address, False)
            else:
                redis_set(cache_key, json.dumps({'fqdns': fqdns, 'addr': server_address, 'passthru': True}), ex=REDIS_PASSTHRU_DURATION)
            raise e


def check_bubble_passthru(client_addr, addr, fqdns):
    cert_status = get_ip_cert_status(client_addr)

    if cert_status is not None and not cert_status:
        bubble_log('check_bubble_passthru: returning True because cert_status for '+client_addr+' was False')
        return {'fqdns': fqdns, 'addr': addr, 'passthru': True}
    else:
        bubble_log('check_bubble_passthru: request is NOT for cert_validation_host: '+cert_validation_host+", it is for one of fqdn="+repr(fqdns)+", checking bubble_passthru...")

    passthru = bubble_passthru(client_addr, addr, fqdns)
    if passthru is None or passthru:
        bubble_log('check_bubble_passthru: bubble_passthru returned '+repr(passthru)+' for FQDN/addr '+repr(fqdns)+'/'+repr(addr)+', returning True')
        return {'fqdns': fqdns, 'addr': addr, 'passthru': True}
    bubble_log('check_bubble_passthru: bubble_passthru returned False for FQDN/addr '+repr(fqdns)+'/'+repr(addr)+', returning False')
    return {'fqdns': fqdns, 'addr': addr, 'passthru': False}


def should_passthru(client_addr, addr, fqdns):
    # always passthru for local ips
    if addr in get_local_ips():
        # bubble_log('should_passthru: local ip is always passthru: '+addr)
        return {'fqdns': fqdns, 'addr': addr, 'passthru': True}
    else:
        # bubble_log('should_passthru: addr ('+addr+') is not a local ip: '+repr(get_local_ips()))
        pass

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
        client_address = next_layer.client_conn.address[0]
        server_address = next_layer.server_conn.address[0]

        fqdns = fqdns_for_addr(server_address)
        validation_host = get_cert_validation_host()
        if fqdns and validation_host in fqdns:
            bubble_log('next_layer: never passing thru (always getting feedback) for cert_validation_host='+validation_host)
            next_layer.__class__ = TlsFeedback

        else:
            bubble_log('next_layer: checking should_passthru for client_address='+client_address)
            passthru = should_passthru(client_address, server_address, fqdns)
            if passthru is None or passthru['passthru']:
                # bubble_log('next_layer: TLS passthru for ' + repr(next_layer.server_conn.address))
                if passthru is not None and 'fqdns' in passthru:
                    bubble_activity_log(client_address, server_address, 'tls_passthru', passthru['fqdns'])
                next_layer_replacement = RawTCPLayer(next_layer.ctx, ignore=True)
                next_layer.reply.send(next_layer_replacement)
            else:
                # bubble_log('next_layer: NO PASSTHRU (getting feedback) for client_address='+client_address+', server_address='+server_address)
                bubble_activity_log(client_address, server_address, 'tls_intercept', passthru['fqdns'])
                next_layer.__class__ = TlsFeedback
