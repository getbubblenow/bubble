#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
#
from mitmproxy.proxy.protocol import TlsLayer, RawTCPLayer
from bubble_api import bubble_log, bubble_passthru
import redis

REDIS_DNS_PREFIX = 'bubble_dns_'
REDIS_PASSTHRU_PREFIX = 'bubble_passthru_'
REDIS_PASSTHRU_DURATION = 60 * 10

REDIS = redis.Redis(host='127.0.0.1', port=6379, db=0)


def check_bubble_passthru(remote_addr, addr):
    fqdn = REDIS.get(REDIS_DNS_PREFIX + addr)
    if fqdn is None or len(fqdn) == 0:
        bubble_log("check_bubble_passthru: no FQDN found for addr "+repr(addr)+", returning False")
        return False
    fqdn = fqdn.decode()
    if bubble_passthru(remote_addr, fqdn):
        bubble_log("check_bubble_passthru: bubble_passthru returned true for FQDN "+repr(fqdn)+", returning True")
        return True
    bubble_log("check_bubble_passthru: bubble_passthru returned false for FQDN "+repr(fqdn)+", returning False")
    return False


def should_passthru(remote_addr, addr):
    bubble_log("should_passthru: examining addr="+repr(addr))
    cache_key = REDIS_PASSTHRU_PREFIX + addr
    passthru_string = REDIS.get(cache_key)
    if passthru_string is None or len(passthru_string) == 0:
        passthru = check_bubble_passthru(remote_addr, addr)
        REDIS.set(cache_key, str(passthru), nx=True, ex=REDIS_PASSTHRU_DURATION)
        passthru_string = str(passthru)
    return passthru_string == 'True'


def next_layer(next_layer):
    if isinstance(next_layer, TlsLayer) and next_layer._client_tls:
        client_address = next_layer.client_conn.address
        server_address = next_layer.server_conn.address
        if should_passthru(client_address[0], server_address[0]):
            # We don't intercept - reply with a pass-through layer and add a "skipped" entry.
            bubble_log("next_layer: TLS passthru for " + repr(next_layer.server_conn.address))
            next_layer_replacement = RawTCPLayer(next_layer.ctx, ignore=True)
            next_layer.reply.send(next_layer_replacement)
