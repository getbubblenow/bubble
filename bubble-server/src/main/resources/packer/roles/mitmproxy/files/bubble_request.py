#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Parts of this are borrowed from dns_spoofing.py in the mitmproxy project. The mitmproxy license is reprinted here:
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

import re
import time
import uuid
from mitmproxy.net.http import headers as nheaders

from bubble_api import bubble_matchers, bubble_activity_log, \
    CTX_BUBBLE_MATCHERS, CTX_BUBBLE_SPECIAL, CTX_BUBBLE_ABORT, CTX_BUBBLE_LOCATION, \
    CTX_BUBBLE_PASSTHRU, CTX_BUBBLE_FLEX, CTX_BUBBLE_REQUEST_ID, add_flow_ctx, parse_host_header, \
    is_bubble_special_path, is_bubble_health_check, health_check_response, tarpit_response,\
    is_bubble_request, is_sage_request, is_not_from_vpn, is_flex_domain
from bubble_config import bubble_host, bubble_host_alias
from bubble_flex import new_flex_flow

import logging
from logging import INFO, DEBUG, WARNING, ERROR, CRITICAL

bubble_log = logging.getLogger(__name__)


class Rerouter:
    @staticmethod
    def get_matchers(flow, host):
        if host is None:
            return None

        if is_bubble_special_path(flow.request.path):
            if bubble_log.isEnabledFor(DEBUG):
                bubble_log.debug("get_matchers: not filtering special bubble path: "+flow.request.path)
            return None

        client_addr = str(flow.client_conn.address[0])
        server_addr = str(flow.server_conn.address[0])
        try:
            host = host.decode()
        except (UnicodeDecodeError, AttributeError):
            try:
                host = str(host)
            except Exception as e:
                if bubble_log.isEnabledFor(WARNING):
                    bubble_log.warning('get_matchers: host '+repr(host)+' could not be decoded, type='+str(type(host))+' e='+repr(e))
                return None

        if host == bubble_host or host == bubble_host_alias:
            if bubble_log.isEnabledFor(INFO):
                bubble_log.info('get_matchers: request is for bubble itself ('+host+'), not matching')
            return None

        req_id = str(host) + '.' + str(uuid.uuid4()) + '.' + str(time.time())
        if bubble_log.isEnabledFor(DEBUG):
            bubble_log.debug("get_matchers: requesting match decision for req_id="+req_id)
        resp = bubble_matchers(req_id, client_addr, server_addr, flow, host)

        if not resp:
            if bubble_log.isEnabledFor(WARNING):
                bubble_log.warning('get_matchers: no response for client_addr/host: '+client_addr+'/'+str(host))
            return None

        matchers = []
        if 'matchers' in resp and resp['matchers'] is not None:
            for m in resp['matchers']:
                if 'urlRegex' in m:
                    if bubble_log.isEnabledFor(DEBUG):
                        bubble_log.debug('get_matchers: checking for match of path='+flow.request.path+' against regex: '+m['urlRegex'])
                else:
                    if bubble_log.isEnabledFor(DEBUG):
                        bubble_log.debug('get_matchers: checking for match of path='+flow.request.path+' -- NO regex, skipping')
                    continue
                if re.match(m['urlRegex'], flow.request.path):
                    if bubble_log.isEnabledFor(DEBUG):
                        bubble_log.debug('get_matchers: rule matched, adding rule: '+m['rule'])
                    matchers.append(m)
                else:
                    if bubble_log.isEnabledFor(DEBUG):
                        bubble_log.debug('get_matchers: rule (regex='+m['urlRegex']+') did NOT match, skipping rule: '+m['rule'])
        else:
            if bubble_log.isEnabledFor(DEBUG):
                bubble_log.debug('get_matchers: no matchers. response='+repr(resp))

        decision = None
        if 'decision' in resp:
            decision = resp['decision']

        matcher_response = {'decision': decision, 'matchers': matchers, 'request_id': req_id}
        if bubble_log.isEnabledFor(INFO):
            bubble_log.info("get_matchers: returning "+repr(matcher_response))
        return matcher_response

    def bubble_handle_request(self, flow):
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
        if host_header:
            m = parse_host_header.match(host_header)
            if m:
                host_header = m.group("host").strip("[]")
                if m.group("port"):
                    port = int(m.group("port"))

        # Determine if this request should be filtered
        host = None
        path = flow.request.path
        if sni or host_header:
            host = str(sni or host_header)
            if host.startswith("b'"):
                host = host[2:-1]
            log_url = flow.request.scheme + '://' + host + path

            # If https, we have already checked that the client/server are legal in bubble_conn_check.py
            # If http, we validate client/server here
            if is_http:
                fqdns = [host]
                if is_bubble_health_check(path):
                    # Health check
                    health_check_response(flow)
                    return None

                elif is_bubble_request(server_addr, fqdns):
                    if bubble_log.isEnabledFor(DEBUG):
                        bubble_log.debug('bubble_handle_request: redirecting to https for LOCAL bubble=' + server_addr +' (bubble_host (' + bubble_host +') in fqdns or bubble_host_alias (' + bubble_host_alias +') in fqdns) for client=' + client_addr +', fqdns=' + repr(fqdns) +', path=' + path)
                    add_flow_ctx(flow, CTX_BUBBLE_ABORT, 301)
                    add_flow_ctx(flow, CTX_BUBBLE_LOCATION, 'https://' + host + path)
                    return None

                elif is_sage_request(server_addr, fqdns):
                    if bubble_log.isEnabledFor(DEBUG):
                        bubble_log.debug('bubble_handle_request: redirecting to https for SAGE server='+server_addr+' for client='+client_addr)
                    add_flow_ctx(flow, CTX_BUBBLE_ABORT, 301)
                    add_flow_ctx(flow, CTX_BUBBLE_LOCATION, 'https://' + host + path)
                    return None

                elif is_not_from_vpn(client_addr):
                    if bubble_log.isEnabledFor(WARNING):
                        bubble_log.warning('bubble_handle_request: sending to tarpit: non-VPN client='+client_addr+', url='+log_url+' host='+host)
                    bubble_activity_log(client_addr, server_addr, 'http_tarpit_non_vpn', fqdns)
                    tarpit_response(flow, host)
                    return None

            if is_bubble_special_path(path):
                add_flow_ctx(flow, CTX_BUBBLE_SPECIAL, True)
            else:
                matcher_response = self.get_matchers(flow, sni or host_header)
                if matcher_response:
                    has_decision = 'decision' in matcher_response and matcher_response['decision'] is not None
                    if has_decision and matcher_response['decision'] == 'pass_thru':
                        if bubble_log.isEnabledFor(DEBUG):
                            bubble_log.debug('bubble_handle_request: passthru response returned, passing thru...')
                        add_flow_ctx(flow, CTX_BUBBLE_PASSTHRU, True)
                        bubble_activity_log(client_addr, server_addr, 'http_passthru', log_url)
                        return host

                    elif has_decision and matcher_response['decision'].startswith('abort_'):
                        if bubble_log.isEnabledFor(DEBUG):
                            bubble_log.debug('bubble_handle_request: found abort code: ' + str(matcher_response['decision']) + ', aborting')
                        if matcher_response['decision'] == 'abort_ok':
                            abort_code = 200
                        elif matcher_response['decision'] == 'abort_not_found':
                            abort_code = 404
                        else:
                            if bubble_log.isEnabledFor(DEBUG):
                                bubble_log.debug('bubble_handle_request: unknown abort code: ' + str(matcher_response['decision']) + ', aborting with 404 Not Found')
                            abort_code = 404
                        flow.request.headers = nheaders.Headers([])
                        flow.request.content = b''
                        add_flow_ctx(flow, CTX_BUBBLE_ABORT, abort_code)
                        bubble_activity_log(client_addr, server_addr, 'http_abort' + str(abort_code), log_url)
                        return None

                    elif has_decision and matcher_response['decision'] == 'no_match':
                        if bubble_log.isEnabledFor(DEBUG):
                            bubble_log.debug('bubble_handle_request: decision was no_match, passing thru...')
                        bubble_activity_log(client_addr, server_addr, 'http_no_match', log_url)
                        return host

                    elif ('matchers' in matcher_response
                          and 'request_id' in matcher_response
                          and len(matcher_response['matchers']) > 0):
                        req_id = matcher_response['request_id']
                        if bubble_log.isEnabledFor(DEBUG):
                            bubble_log.debug("bubble_handle_request: found request_id: " + req_id + ' with matchers: ' + repr(matcher_response['matchers']))
                        add_flow_ctx(flow, CTX_BUBBLE_MATCHERS, matcher_response['matchers'])
                        add_flow_ctx(flow, CTX_BUBBLE_REQUEST_ID, req_id)
                        bubble_activity_log(client_addr, server_addr, 'http_match', log_url)
                    else:
                        if bubble_log.isEnabledFor(DEBUG):
                            bubble_log.debug('bubble_handle_request: no rules returned, passing thru...')
                        bubble_activity_log(client_addr, server_addr, 'http_no_rules', log_url)
                else:
                    if bubble_log.isEnabledFor(DEBUG):
                        bubble_log.debug('bubble_handle_request: no matcher_response returned, passing thru...')
                    # bubble_activity_log(client_addr, server_addr, 'http_no_matcher_response', log_url)

        elif is_http and is_not_from_vpn(client_addr):
            if bubble_log.isEnabledFor(WARNING):
                bubble_log.warning('bubble_handle_request: sending to tarpit: non-VPN client='+client_addr)
            bubble_activity_log(client_addr, server_addr, 'http_tarpit_non_vpn', [server_addr])
            tarpit_response(flow, host)
            return None

        else:
            if bubble_log.isEnabledFor(WARNING):
                bubble_log.warning('bubble_handle_request: no sni/host found, not applying rules to path: ' + path)
            bubble_activity_log(client_addr, server_addr, 'http_no_sni_or_host', [server_addr])

        flow.request.host_header = host_header
        if host:
            flow.request.host = host
        else:
            flow.request.host = host_header
        flow.request.port = port
        return host

    def requestheaders(self, flow):
        host = self.bubble_handle_request(flow)
        path = flow.request.path

        if is_bubble_special_path(path):
            flow.request.force_no_stream = True

        elif host is not None:
            client_addr = flow.client_conn.address[0]
            server_addr = flow.server_conn.address[0]
            if is_flex_domain(client_addr, server_addr, [host]):
                flex_flow = new_flex_flow(client_addr, host, flow)
                add_flow_ctx(flow, CTX_BUBBLE_FLEX, flex_flow)
                if bubble_log.isEnabledFor(DEBUG):
                    bubble_log.debug('request: is_flex_domain('+host+') returned true, setting ctx: '+CTX_BUBBLE_FLEX)


addons = [Rerouter()]
