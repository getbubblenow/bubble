#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://bubblev.com/bubble-license/
#
from mitmproxy.proxy.protocol import TlsLayer, RawTCPLayer
from bubble_api import bubble_log

def should_passthru(next_layer, addr):
    # todo
    return False

def next_layer(next_layer):
    """
    This hook does the actual magic - if the next layer is planned to be a TLS layer,
    we check if we want to enter pass-through mode instead.
    """
    if isinstance(next_layer, TlsLayer) and next_layer._client_tls:
        server_address = next_layer.server_conn.address
        bubble_log("next_layer: examining server_address="+server_address+" with respect to next_layer="+repr(next_layer))
        if should_passthru(next_layer, server_address):
            # We don't intercept - reply with a pass-through layer and add a "skipped" entry.
            bubble_log("next_layer: TLS passthru for " + repr(next_layer.server_conn.address))
            next_layer_replacement = RawTCPLayer(next_layer.ctx, ignore=True)
            next_layer.reply.send(next_layer_replacement)
