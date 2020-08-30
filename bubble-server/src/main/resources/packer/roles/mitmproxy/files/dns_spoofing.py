#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
import re
import time
import uuid
from mitmproxy.net.http import headers as nheaders

from bubble_api import bubble_matchers, bubble_log, bubble_activity_log, \
    CTX_BUBBLE_MATCHERS, BUBBLE_URI_PREFIX, CTX_BUBBLE_ABORT, CTX_BUBBLE_LOCATION, CTX_BUBBLE_PASSTHRU, CTX_BUBBLE_REQUEST_ID, \
    add_flow_ctx, parse_host_header, is_bubble_request, is_sage_request, is_not_from_vpn
from bubble_config import bubble_host, bubble_host_alias

class Rerouter:
    @staticmethod
    def get_matchers(flow, host):
        if host is None:
            return None

        if flow.request.path and flow.request.path.startswith(BUBBLE_URI_PREFIX):
            bubble_log("get_matchers: not filtering special bubble path: "+flow.request.path)
            return None

        client_addr = str(flow.client_conn.address[0])
        server_addr = str(flow.server_conn.address[0])
        try:
            host = host.decode()
        except (UnicodeDecodeError, AttributeError):
            try:
                host = str(host)
            except Exception as e:
                bubble_log('get_matchers: host '+repr(host)+' could not be decoded, type='+str(type(host))+' e='+repr(e))
                return None

        if host == bubble_host or host == bubble_host_alias:
            bubble_log('get_matchers: request is for bubble itself ('+host+'), not matching')
            return None

        req_id = str(host) + '.' + str(uuid.uuid4()) + '.' + str(time.time())
        bubble_log("get_matchers: requesting match decision for req_id="+req_id)
        resp = bubble_matchers(req_id, client_addr, server_addr, flow, host)

        if not resp:
            bubble_log('get_matchers: no response for client_addr/host: '+client_addr+'/'+str(host))
            return None

        matchers = []
        if 'matchers' in resp and resp['matchers'] is not None:
            for m in resp['matchers']:
                if 'urlRegex' in m:
                    bubble_log('get_matchers: checking for match of path='+flow.request.path+' against regex: '+m['urlRegex'])
                else:
                    bubble_log('get_matchers: checking for match of path='+flow.request.path+' -- NO regex, skipping')
                    continue
                if re.match(m['urlRegex'], flow.request.path):
                    bubble_log('get_matchers: rule matched, adding rule: '+m['rule'])
                    matchers.append(m)
                else:
                    bubble_log('get_matchers: rule (regex='+m['urlRegex']+') did NOT match, skipping rule: '+m['rule'])
        else:
            bubble_log('get_matchers: no matchers. response='+repr(resp))

        decision = None
        if 'decision' in resp:
            decision = resp['decision']

        matcher_response = {'decision': decision, 'matchers': matchers, 'request_id': req_id}
        bubble_log("get_matchers: returning "+repr(matcher_response))
        return matcher_response

    def request(self, flow):
        client_addr = flow.client_conn.address[0]
        server_addr = flow.server_conn.address[0]
        is_http = False
        if flow.client_conn.tls_established:
            flow.request.scheme = "https"
            sni = flow.client_conn.connection.get_servername()
            port = 443
        else:
            flow.request.scheme = "http"
            sni = None
            port = 80
            is_http = True

        # check if https and sni is missing but we have a host header, fill in the sni
        
        host_header = flow.request.host_header
        # bubble_log("dns_spoofing.request: host_header is "+repr(host_header))
        if host_header:
            m = parse_host_header.match(host_header)
            if m:
                host_header = m.group("host").strip("[]")
                if m.group("port"):
                    port = int(m.group("port"))

        # Determine if this request should be filtered
        if sni or host_header:
            host = str(sni or host_header)
            if host.startswith("b'"):
                host = host[2:-1]
            log_url = flow.request.scheme + '://' + host + flow.request.path

            # If https, we have already checked that the client/server are legal in bubble_conn_check.py
            # If http, we validate client/server here
            if is_http:
                fqdns = [host]
                if is_bubble_request(server_addr, fqdns):
                    bubble_log('dns_spoofing.request: redirecting to https for LOCAL bubble='+server_addr+' (bubble_host ('+bubble_host+') in fqdns or bubble_host_alias ('+bubble_host_alias+') in fqdns) for client='+client_addr+', fqdns='+repr(fqdns))
                    add_flow_ctx(flow, CTX_BUBBLE_ABORT, 301)
                    add_flow_ctx(flow, CTX_BUBBLE_LOCATION, 'https://'+host+flow.request.path)
                    return

                elif is_sage_request(server_addr, fqdns):
                    bubble_log('dns_spoofing.request: redirecting to https for SAGE server='+server_addr+' for client='+client_addr)
                    add_flow_ctx(flow, CTX_BUBBLE_ABORT, 301)
                    add_flow_ctx(flow, CTX_BUBBLE_LOCATION, 'https://'+host+flow.request.path)
                    return

                elif is_not_from_vpn(client_addr):
                    # todo: add to fail2ban
                    bubble_log('dns_spoofing.request: returning 404 for non-VPN client='+client_addr+', fqdns='+str(fqdns))
                    bubble_activity_log(client_addr, server_addr, 'http_abort_non_vpn', fqdns)
                    add_flow_ctx(flow, CTX_BUBBLE_ABORT, 404)
                    return

            matcher_response = self.get_matchers(flow, sni or host_header)
            if matcher_response:
                if 'decision' in matcher_response and matcher_response['decision'] is not None and matcher_response['decision'] == 'passthru':
                    bubble_log('dns_spoofing.request: passthru response returned, passing thru and NOT performing TLS interception...')
                    add_flow_ctx(flow, CTX_BUBBLE_PASSTHRU, True)
                    bubble_activity_log(client_addr, server_addr, 'http_passthru', log_url)
                    return

                elif 'decision' in matcher_response and matcher_response['decision'] is not None and matcher_response['decision'].startswith('abort_'):
                    bubble_log('dns_spoofing.request: found abort code: ' + str(matcher_response['decision']) + ', aborting')
                    if matcher_response['decision'] == 'abort_ok':
                        abort_code = 200
                    elif matcher_response['decision'] == 'abort_not_found':
                        abort_code = 404
                    else:
                        bubble_log('dns_spoofing.request: unknown abort code: ' + str(matcher_response['decision']) + ', aborting with 404 Not Found')
                        abort_code = 404
                    flow.request.headers = nheaders.Headers([])
                    flow.request.content = b''
                    add_flow_ctx(flow, CTX_BUBBLE_ABORT, abort_code)
                    bubble_activity_log(client_addr, server_addr, 'http_abort' + str(abort_code), log_url)
                    return

                elif 'decision' in matcher_response and matcher_response['decision'] is not None and matcher_response['decision'] == 'no_match':
                    bubble_log('dns_spoofing.request: decision was no_match, passing thru...')
                    bubble_activity_log(client_addr, server_addr, 'http_no_match', log_url)
                    return

                elif ('matchers' in matcher_response
                      and 'request_id' in matcher_response
                      and len(matcher_response['matchers']) > 0):
                    req_id = matcher_response['request_id']
                    bubble_log("dns_spoofing.request: found request_id: " + req_id + ' with matchers: ' + repr(matcher_response['matchers']))
                    add_flow_ctx(flow, CTX_BUBBLE_MATCHERS, matcher_response['matchers'])
                    add_flow_ctx(flow, CTX_BUBBLE_REQUEST_ID, req_id)
                    bubble_activity_log(client_addr, server_addr, 'http_match', log_url)
                else:
                    bubble_log('dns_spoofing.request: no rules returned, passing thru...')
                    bubble_activity_log(client_addr, server_addr, 'http_no_rules', log_url)
            else:
                bubble_log('dns_spoofing.request: no matcher_response returned, passing thru...')
                # bubble_activity_log(client_addr, server_addr, 'http_no_matcher_response', log_url)

        elif is_http and is_not_from_vpn(client_addr):
            # todo: add to fail2ban
            bubble_log('dns_spoofing.request: returning 404 for non-VPN client='+client_addr+', server_addr='+server_addr)
            bubble_activity_log(client_addr, server_addr, 'http_abort_non_vpn', [server_addr])
            add_flow_ctx(flow, CTX_BUBBLE_ABORT, 404)
            return

        else:
            bubble_log('dns_spoofing.request: no sni/host found, not applying rules to path: ' + flow.request.path)
            bubble_activity_log(client_addr, server_addr, 'http_no_sni_or_host', [server_addr])

        flow.request.host_header = host_header
        flow.request.host = sni or host_header
        flow.request.port = port


addons = [Rerouter()]
