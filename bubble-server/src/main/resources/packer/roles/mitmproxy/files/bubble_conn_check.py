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

import json
import logging
from logging import INFO, DEBUG, WARNING, ERROR, CRITICAL

import traceback
from bubble_api import bubble_conn_check, bubble_activity_log, REDIS, redis_set, \
    is_bubble_request, is_sage_request, is_not_from_vpn, is_flex_domain, bubble_get_flex_router
from bubble_config import bubble_host, bubble_host_alias, cert_validation_host
from bubble_flex_passthru import BubbleFlexPassthruLayer

bubble_log = logging.getLogger(__name__)

log_debug = bubble_log.isEnabledFor(DEBUG)
log_info = bubble_log.isEnabledFor(INFO)
log_warning = bubble_log.isEnabledFor(WARNING)

REDIS_DNS_PREFIX = 'bubble_dns_'
REDIS_CONN_CHECK_PREFIX = 'bubble_conn_check_'
REDIS_CHECK_DURATION = 60 * 60  # 1 hour timeout
REDIS_KEY_DEVICE_SECURITY_LEVEL_PREFIX = 'bubble_device_security_level_'  # defined in StandardDeviceIdService
REDIS_KEY_DEVICE_SITE_MAX_SECURITY_LEVEL_PREFIX = 'bubble_device_site_max_security_level_'  # defined in StandardDeviceIdService
REDIS_KEY_DEVICE_SHOW_BLOCK_STATS = 'bubble_device_showBlockStats_'

FORCE_PASSTHRU = {'passthru': True}
FORCE_BLOCK = {'block': True}

# Matches enums in DeviceSecurityLevel
SEC_MAX = 'maximum'
SEC_STD = 'standard'
SEC_BASIC = 'basic'
SEC_OFF = 'disabled'


def get_device_security_level(client_addr, fqdns):
    level = REDIS.get(REDIS_KEY_DEVICE_SECURITY_LEVEL_PREFIX+client_addr)
    if level is None:
        return {'level': SEC_MAX}
    level = level.decode()
    if level == SEC_STD:
        if log_debug:
            bubble_log.info('get_device_security_level: checking for max_required_fqdns against fqdns='+repr(fqdns))
        if fqdns:
            max_required_fqdns = REDIS.smembers(REDIS_KEY_DEVICE_SITE_MAX_SECURITY_LEVEL_PREFIX+client_addr)
            if max_required_fqdns is not None:
                if log_debug:
                    bubble_log.info('get_device_security_level: found max_required_fqdns='+repr(max_required_fqdns))
                for max_required in max_required_fqdns:
                    max_required = max_required.decode()
                    for fqdn in fqdns:
                        if max_required == fqdn or (max_required.startswith('*.') and fqdn.endswith(max_required[1:])):
                            if log_info:
                                bubble_log.info('get_device_security_level: returning maximum for fqdn '+fqdn+' based on max_required='+max_required)
                            return {'level': SEC_MAX, 'pinned': True}
    return {'level': level}


def show_block_stats(client_addr, fqdns):
    if fqdns is not None:
        for fqdn in fqdns:
            show = REDIS.get(REDIS_KEY_DEVICE_SHOW_BLOCK_STATS+client_addr+':'+fqdn)
            if show is not None:
                return show.decode() == 'true'
    show = REDIS.get(REDIS_KEY_DEVICE_SHOW_BLOCK_STATS+client_addr)
    if show is None:
        return False
    return show.decode() == 'true'


def conn_check_cache_prefix(client_addr, server_addr):
    return REDIS_CONN_CHECK_PREFIX + client_addr + '_' + server_addr


def fqdns_for_addr(client_addr, server_addr):
    if server_addr is None or client_addr is None or len(client_addr) == 0 or len(server_addr) == 0:
        if log_warning:
            bubble_log.warning('fqdns_for_addr: client_addr ('+repr(client_addr)+') or server_addr ('+repr(server_addr)+') was None or empty')
        return None
    key = REDIS_DNS_PREFIX + server_addr + '~' + client_addr
    values = REDIS.smembers(key)
    if values is None or len(values) == 0:
        if log_debug:
            bubble_log.debug('fqdns_for_addr: no FQDN found for server_addr '+str(server_addr)+' and client_addr '+client_addr)
        return None
    fqdns = []
    for fqdn in values:
        fqdns.append(fqdn.decode())
    return fqdns


class TlsBlock(TlsLayer):
    """
    Monkey-patch __call__ to drop this connection entirely
    """
    def __call__(self):
        if log_info:
            bubble_log.info('TlsBlock: blocking')
        return


class TlsFeedback(TlsLayer):
    fqdns = None
    security_level = None
    """
    Monkey-patch _establish_tls_with_client to get feedback if TLS could be established
    successfully on the client connection (which may fail due to cert pinning).
    """
    def _establish_tls_with_client(self):
        client_address = self.client_conn.address[0]
        server_address = self.server_conn.address[0]
        security_level = self.security_level
        try:
            super(TlsFeedback, self)._establish_tls_with_client()

        except TlsProtocolException as e:
            if self.do_block:
                if log_debug:
                    bubble_log.debug('_establish_tls_with_client: TLS error for '+str(server_address)+'/fqdns='+str(self.fqdns)+' and do_block==True, raising error for client '+client_address)
                raise e

            tb = traceback.format_exc()
            if 'OpenSSL.SSL.ZeroReturnError' in tb:
                if log_warning:
                    bubble_log.warning('_establish_tls_with_client: TLS error for '+str(server_address)+'/fqdns='+str(self.fqdns)+', raising SSL zero return error for client '+client_address)
                raise e

            elif 'SysCallError' in tb:
                if log_warning:
                    bubble_log.warning('_establish_tls_with_client: TLS error for '+str(server_address)+'/fqdns='+str(self.fqdns)+', raising SysCallError for client '+client_address)
                raise e

            elif self.fqdns is not None and len(self.fqdns) > 0:
                for fqdn in self.fqdns:
                    cache_key = conn_check_cache_prefix(client_address, fqdn)
                    if security_level['level'] == SEC_MAX:
                        if 'pinned' in security_level and security_level['pinned']:
                            redis_set(cache_key, json.dumps({'fqdns': [fqdn], 'addr': server_address, 'passthru': False, 'block': False, 'reason': 'tls_failure_pinned'}), ex=REDIS_CHECK_DURATION)
                            if log_debug:
                                bubble_log.debug('_establish_tls_with_client: TLS error for '+str(server_address)+', enabling block (security_level=maximum/pinned) for client '+client_address+' with cache_key='+cache_key+' and fqdn='+fqdn+': '+repr(e))
                        else:
                            redis_set(cache_key, json.dumps({'fqdns': [fqdn], 'addr': server_address, 'passthru': False, 'block': True, 'reason': 'tls_failure'}), ex=REDIS_CHECK_DURATION)
                            if log_debug:
                                bubble_log.debug('_establish_tls_with_client: TLS error for '+str(server_address)+', enabling block (security_level=maximum) for client '+client_address+' with cache_key='+cache_key+' and fqdn='+fqdn+': '+repr(e))
                    else:
                        redis_set(cache_key, json.dumps({'fqdns': [fqdn], 'addr': server_address, 'passthru': True, 'reason': 'tls_failure'}), ex=REDIS_CHECK_DURATION)
                        if log_debug:
                            bubble_log.debug('_establish_tls_with_client: TLS error for '+str(server_address)+', enabling passthru for client '+client_address+' with cache_key='+cache_key+' and fqdn='+fqdn+': '+repr(e))
            else:
                cache_key = conn_check_cache_prefix(client_address, server_address)
                if security_level['level'] == SEC_MAX:
                    redis_set(cache_key, json.dumps({'fqdns': None, 'addr': server_address, 'passthru': False, 'block': True, 'reason': 'tls_failure'}), ex=REDIS_CHECK_DURATION)
                    if log_debug:
                        bubble_log.debug('_establish_tls_with_client: TLS error for '+str(server_address)+', enabling block (security_level=maximum) for client '+client_address+' with cache_key='+cache_key+' and server_address='+server_address+': '+repr(e))
                else:
                    redis_set(cache_key, json.dumps({'fqdns': None, 'addr': server_address, 'passthru': True, 'reason': 'tls_failure'}), ex=REDIS_CHECK_DURATION)
                    if log_debug:
                        bubble_log.debug('_establish_tls_with_client: TLS error for '+str(server_address)+', enabling passthru for client '+client_address+' with cache_key='+cache_key+' and server_address='+server_address+': '+repr(e))
            raise e


def check_bubble_connection(client_addr, server_addr, fqdns, security_level):
    check_response = bubble_conn_check(client_addr, server_addr, fqdns, security_level)
    if check_response is None or check_response == 'error':
        if security_level['level'] == SEC_MAX:
            if log_debug:
                bubble_log.debug('check_bubble_connection: bubble API returned ' + str(check_response) +' for FQDN/addr ' + str(fqdns) +'/' + str(server_addr) + ', security_level=maximum, returning Block')
            return {'fqdns': fqdns, 'addr': server_addr, 'passthru': False, 'block': True, 'reason': 'bubble_error'}
        else:
            if log_debug:
                bubble_log.debug('check_bubble_connection: bubble API returned ' + str(check_response) +' for FQDN/addr ' + str(fqdns) +'/' + str(server_addr) + ', returning True')
            return {'fqdns': fqdns, 'addr': server_addr, 'passthru': True, 'reason': 'bubble_error'}

    elif check_response == 'passthru':
        if log_debug:
            bubble_log.debug('check_bubble_connection: bubble API returned ' + str(check_response) +' for FQDN/addr ' + str(fqdns) +'/' + str(server_addr) + ', returning True')
        return {'fqdns': fqdns, 'addr': server_addr, 'passthru': True, 'reason': 'bubble_passthru'}

    elif check_response == 'block':
        if log_debug:
            bubble_log.debug('check_bubble_connection: bubble API returned ' + str(check_response) +' for FQDN/addr ' + str(fqdns) +'/' + str(server_addr) + ', returning Block')
        return {'fqdns': fqdns, 'addr': server_addr, 'passthru': False, 'block': True, 'reason': 'bubble_block'}

    else:
        if log_debug:
            bubble_log.debug('check_bubble_connection: bubble API returned ' + str(check_response) +' for FQDN/addr ' + str(fqdns) +'/' + str(server_addr) + ', returning False')
        return {'fqdns': fqdns, 'addr': server_addr, 'passthru': False, 'reason': 'bubble_no_passthru'}


def check_connection(client_addr, server_addr, fqdns, security_level):
    if fqdns and len(fqdns) == 1:
        cache_key = conn_check_cache_prefix(client_addr, fqdns[0])
    else:
        cache_key = conn_check_cache_prefix(client_addr, server_addr)
    prefix = 'check_connection: ip=' + str(server_addr) + ' (fqdns=' + str(fqdns) + ') cache_key=' + cache_key + ': '

    check_json = REDIS.get(cache_key)
    if check_json is None or len(check_json) == 0:
        if log_debug:
            bubble_log.debug(prefix+'not in redis or empty, calling check_bubble_connection against fqdns='+str(fqdns))
        check_response = check_bubble_connection(client_addr, server_addr, fqdns, security_level)
        if log_debug:
            bubble_log.debug(prefix+'check_bubble_connection('+str(fqdns)+') returned '+str(check_response)+", storing in redis...")
        redis_set(cache_key, json.dumps(check_response), ex=REDIS_CHECK_DURATION)

    else:
        if log_debug:
            bubble_log.debug(prefix+'found check_json='+str(check_json)+', touching key in redis')
        check_response = json.loads(check_json)
        REDIS.touch(cache_key)
    if log_debug:
        bubble_log.debug(prefix+'returning '+str(check_response))
    return check_response


def passthru_flex_port(client_addr, fqdn):
    router = bubble_get_flex_router(client_addr, fqdn)
    if router is None or 'auth' not in router:
        if log_info:
            bubble_log.info('apply_passthru_flex: no flex router for fqdn(s): '+repr(fqdn))
    elif 'port' in router:
        return router['port']
    else:
        if log_warning:
            bubble_log.warning('apply_passthru_flex: flex router found but has no port ('+repr(router)+') for fqdn(s): '+repr(fqdn))
    return None


def do_passthru(client_addr, server_addr, fqdns, layer):
    flex_port = None
    if is_flex_domain(client_addr, server_addr, fqdns):
        flex_port = passthru_flex_port(client_addr, fqdns[0])
        if flex_port:
            if log_debug:
                bubble_log.debug('do_passthru: applying flex passthru for server=' + server_addr + ', fqdns=' + str(fqdns))
            layer_replacement = BubbleFlexPassthruLayer(layer.ctx, ('127.0.0.1', flex_port), fqdns[0], 443)
            layer.reply.send(layer_replacement)
        else:
            if log_debug:
                bubble_log.debug('do_passthru: detected flex passthru but no flex routers available for server=' + server_addr + ', fqdns=' + str(fqdns))
    if flex_port is None:
        layer_replacement = RawTCPLayer(layer.ctx, ignore=True)
        layer.reply.send(layer_replacement)


def next_layer(layer):
    if isinstance(layer, TlsLayer) and layer._client_tls:
        client_hello = net_tls.ClientHello.from_file(layer.client_conn.rfile)
        client_addr = layer.client_conn.address[0]
        server_addr = layer.server_conn.address[0]
        if log_debug:
            bubble_log.debug('next_layer: STARTING: client='+client_addr+' server='+server_addr)
        if client_hello.sni:
            fqdn = client_hello.sni.decode()
            if log_debug:
                bubble_log.debug('next_layer: using fqdn in SNI: '+fqdn)
            fqdns = [fqdn]
        else:
            fqdns = fqdns_for_addr(client_addr, server_addr)
            if log_debug:
                bubble_log.debug('next_layer: NO fqdn in sni, using fqdns from DNS: '+str(fqdns))
        layer.fqdns = fqdns
        no_fqdns = fqdns is None or len(fqdns) == 0
        security_level = get_device_security_level(client_addr, fqdns)
        layer.security_level = security_level
        layer.do_block = False
        if is_bubble_request(server_addr, fqdns):
            if log_info:
                bubble_log.info('next_layer: enabling passthru for LOCAL bubble='+server_addr+' (bubble_host ('+bubble_host+') in fqdns or bubble_host_alias ('+bubble_host_alias+') in fqdns) regardless of security_level='+repr(security_level)+' for client='+client_addr+', fqdns='+repr(fqdns))
            check = FORCE_PASSTHRU

        elif is_sage_request(server_addr, fqdns):
            if log_info:
                bubble_log.info('next_layer: enabling passthru for SAGE server='+server_addr+' regardless of security_level='+repr(security_level)+' for client='+client_addr)
            check = FORCE_PASSTHRU

        elif is_not_from_vpn(client_addr):
            # todo: add to fail2ban
            if log_warning:
                bubble_log.warning('next_layer: enabling block for non-VPN client='+client_addr+', fqdns='+str(fqdns))
            bubble_activity_log(client_addr, server_addr, 'conn_block_non_vpn', fqdns)
            layer.__class__ = TlsBlock
            return

        elif security_level['level'] == SEC_OFF:
            if log_info:
                bubble_log.info('next_layer: enabling passthru for server='+server_addr+' because security_level='+repr(security_level)+' for client='+client_addr)
            check = FORCE_PASSTHRU

        elif fqdns is not None and len(fqdns) == 1 and cert_validation_host == fqdns[0] and security_level['level'] != SEC_BASIC:
            if log_info:
                bubble_log.info('next_layer: NOT enabling passthru for server='+server_addr+' because fqdn is cert_validation_host ('+cert_validation_host+') for client='+client_addr)
            return

        elif (security_level['level'] == SEC_STD or security_level['level'] == SEC_BASIC) and no_fqdns:
            if log_info:
                bubble_log.info('next_layer: enabling passthru for server='+server_addr+' because no FQDN found and security_level='+repr(security_level)+' for client='+client_addr)
            check = FORCE_PASSTHRU

        elif security_level['level'] == SEC_MAX and no_fqdns:
            if log_info:
                bubble_log.info('next_layer: disabling passthru (no TlsFeedback) for server='+server_addr+' because no FQDN found and security_level='+repr(security_level)+' for client='+client_addr)
            check = FORCE_BLOCK

        else:
            if log_info:
                bubble_log.info('next_layer: calling check_connection for server='+server_addr+', fqdns='+str(fqdns)+', client='+client_addr+' with security_level='+repr(security_level))
            check = check_connection(client_addr, server_addr, fqdns, security_level)

        if check is None or ('passthru' in check and check['passthru']):
            if log_info:
                bubble_log.info('next_layer: CONN-DECISION: PASSTHRU '+str(fqdns)+' for server=' + server_addr)
            bubble_activity_log(client_addr, server_addr, 'tls_passthru', fqdns)
            do_passthru(client_addr, server_addr, fqdns, layer)

        elif 'block' in check and check['block']:
            bubble_activity_log(client_addr, server_addr, 'conn_block', fqdns)
            if show_block_stats(client_addr, fqdns) and security_level['level'] != SEC_BASIC:
                if log_info:
                    bubble_log.info('next_layer: CONN-DECISION: ALLOW '+str(fqdns)+' (would block but security_level='+repr(security_level)+' and show_block_stats=True) for server='+server_addr)
                layer.do_block = True
                layer.__class__ = TlsFeedback
            else:
                if log_info:
                    bubble_log.info('next_layer: CONN-DECISION: BLOCK '+str(fqdns)+' for server='+server_addr)
                layer.__class__ = TlsBlock

        elif security_level['level'] == SEC_BASIC:
            if log_info:
                bubble_log.info('next_layer: CONN-DECISION: PASSTHRU '+str(fqdns)+' (check='+repr(check)+' but security_level='+repr(security_level)+') server='+server_addr)
            bubble_activity_log(client_addr, server_addr, 'tls_passthru', fqdns)
            do_passthru(client_addr, server_addr, fqdns, layer)

        else:
            if log_info:
                bubble_log.info('next_layer: CONN-DECISION: ALLOW '+str(fqdns)+' (with TlsFeedback) for client_addr='+client_addr+', server_addr='+server_addr)
            bubble_activity_log(client_addr, server_addr, 'tls_intercept', fqdns)
            layer.__class__ = TlsFeedback
