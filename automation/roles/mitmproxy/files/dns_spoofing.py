#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
import re
import time
import uuid
from bubble_api import bubble_matchers, bubble_log, bubble_activity_log, CTX_BUBBLE_MATCHERS, BUBBLE_URI_PREFIX, CTX_BUBBLE_ABORT, CTX_BUBBLE_PASSTHRU, CTX_BUBBLE_REQUEST_ID, add_flow_ctx
from bubble_config import bubble_host, bubble_host_alias

# This regex extracts splits the host header into host and port.
# Handles the edge case of IPv6 addresses containing colons.
# https://bugzilla.mozilla.org/show_bug.cgi?id=45891
parse_host_header = re.compile(r"^(?P<host>[^:]+|\[.+\])(?::(?P<port>\d+))?$")


class Rerouter:
    @staticmethod
    def get_matchers(flow, host):
        if host is None:
            return None

        if flow.request.path and flow.request.path.startswith(BUBBLE_URI_PREFIX):
            bubble_log("get_matchers: not filtering special bubble path: "+flow.request.path)
            return None

        remote_addr = str(flow.client_conn.address[0])
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
        resp = bubble_matchers(req_id, remote_addr, flow, host)

        if not resp:
            bubble_log('get_matchers: no response for remote_addr/host: '+remote_addr+'/'+str(host))
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

        matcher_response = { 'decision': decision, 'matchers': matchers, 'request_id': req_id }
        bubble_log("get_matchers: returning "+repr(matcher_response))
        return matcher_response

    def request(self, flow):
        client_address = flow.client_conn.address[0]
        server_address = flow.server_conn.address[0]
        if flow.client_conn.tls_established:
            flow.request.scheme = "https"
            sni = flow.client_conn.connection.get_servername()
            port = 443
        else:
            flow.request.scheme = "http"
            sni = None
            port = 80

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
            matcher_response = self.get_matchers(flow, sni or host_header)
            if matcher_response:
                if 'decision' in matcher_response and matcher_response['decision'] is not None and matcher_response['decision'] == 'passthru':
                    bubble_log('dns_spoofing.request: passthru response returned, passing thru and NOT performing TLS interception...')
                    add_flow_ctx(flow, CTX_BUBBLE_PASSTHRU, True)
                    bubble_activity_log(client_address, server_address, 'http_passthru', log_url)
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
                    add_flow_ctx(flow, CTX_BUBBLE_ABORT, abort_code)
                    bubble_activity_log(client_address, server_address, 'http_abort' + str(abort_code), log_url)
                    return

                elif 'decision' in matcher_response and matcher_response['decision'] is not None and matcher_response['decision'] == 'no_match':
                    bubble_log('dns_spoofing.request: decision was no_match, passing thru...')
                    bubble_activity_log(client_address, server_address, 'http_no_match', log_url)
                    return

                elif ('matchers' in matcher_response
                      and 'request_id' in matcher_response
                      and len(matcher_response['matchers']) > 0):
                    req_id = matcher_response['request_id']
                    bubble_log("dns_spoofing.request: found request_id: " + req_id + ' with matchers: ' + repr(matcher_response['matchers']))
                    add_flow_ctx(flow, CTX_BUBBLE_MATCHERS, matcher_response['matchers'])
                    add_flow_ctx(flow, CTX_BUBBLE_REQUEST_ID, req_id)
                    bubble_activity_log(client_address, server_address, 'http_match', log_url)
                else:
                    bubble_log('dns_spoofing.request: no rules returned, passing thru...')
                    bubble_activity_log(client_address, server_address, 'http_no_rules', log_url)
            else:
                bubble_log('dns_spoofing.request: no matcher_response returned, passing thru...')
                # bubble_activity_log(client_address, server_address, 'http_no_matcher_response', log_url)
        else:
            bubble_log('dns_spoofing.request: no sni/host found, not applying rules to path: ' + flow.request.path)
            bubble_activity_log(client_address, server_address, 'http_no_sni_or_host', 'n/a')

        flow.request.host_header = host_header
        flow.request.host = sni or host_header
        flow.request.port = port


addons = [Rerouter()]
