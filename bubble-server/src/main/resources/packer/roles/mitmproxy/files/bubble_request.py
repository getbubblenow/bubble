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
    is_bubble_request, is_sage_request, is_not_from_vpn, is_flex_domain, update_host_and_port
from bubble_config import bubble_host, bubble_host_alias, bubble_log_request
from bubble_flex import new_flex_flow

import logging
from logging import INFO, DEBUG, WARNING, ERROR, CRITICAL

bubble_log = logging.getLogger(__name__)

log_debug = bubble_log.isEnabledFor(DEBUG)
log_info = bubble_log.isEnabledFor(INFO)
log_warning = bubble_log.isEnabledFor(WARNING)


class Rerouter:
    @staticmethod
    def get_matchers(flow, host):
        if host is None:
            return None

        if is_bubble_special_path(flow.request.path):
            if log_debug:
                bubble_log.debug("not filtering special bubble path: "+flow.request.path)
            return None

        client_addr = str(flow.client_conn.address[0])
        server_addr = str(flow.server_conn.address[0])
        try:
            host = host.decode()
        except (UnicodeDecodeError, AttributeError):
            try:
                host = str(host)
            except Exception as e:
                if log_warning:
                    bubble_log.warning('get_matchers: host '+repr(host)+' could not be decoded, type='+str(type(host))+' e='+repr(e))
                return None

        if host == bubble_host or host == bubble_host_alias:
            if log_info:
                bubble_log.info('get_matchers: request is for bubble itself ('+host+'), not matching')
            return None

        prefix = 'get_matchers('+host+flow.request.path+'): '
        req_id = str(host) + '.' + str(uuid.uuid4()) + '.' + str(time.time())
        if log_debug:
            if bubble_log_request:
                bubble_log.debug(prefix+'requesting match decision for req_id='+req_id+' with request headers: '+repr(flow.request.headers))
            else:
                bubble_log.debug(prefix+'requesting match decision for req_id='+req_id)
        resp = bubble_matchers(req_id, client_addr, server_addr, flow, host)

        if not resp:
            if log_warning:
                bubble_log.warning('get_matchers: no response for client_addr/host: '+client_addr+'/'+str(host))
            return None

        matchers = []
        if 'matchers' in resp and resp['matchers'] is not None:
            for m in resp['matchers']:
                if 'urlRegex' in m:
                    if log_debug:
                        bubble_log.debug('get_matchers: checking for match of path='+flow.request.path+' against regex: '+m['urlRegex'])
                else:
                    if log_debug:
                        bubble_log.debug('get_matchers: checking for match of path='+flow.request.path+' -- NO regex, skipping')
                    continue
                if re.match(m['urlRegex'], flow.request.path):
                    if log_debug:
                        bubble_log.debug('get_matchers: rule matched, adding rule: '+m['rule'])
                    matchers.append(m)
                else:
                    if log_debug:
                        bubble_log.debug('get_matchers: rule (regex='+m['urlRegex']+') did NOT match, skipping rule: '+m['rule'])
        else:
            if log_debug:
                bubble_log.debug('get_matchers: no matchers. response='+repr(resp))

        decision = None
        if 'decision' in resp:
            decision = resp['decision']

        matcher_response = {'decision': decision, 'matchers': matchers, 'request_id': req_id}
        if log_info:
            bubble_log.info(prefix+"returning "+repr(matcher_response))
        return matcher_response

    def bubble_handle_request(self, flow):
        client_addr = flow.client_conn.address[0]
        server_addr = flow.server_conn.address[0]
        flow = update_host_and_port(flow)

        if flow.client_conn.tls_established:
            sni = flow.client_conn.connection.get_servername()
            is_http = False
        else:
            sni = None
            is_http = True

        # Determine if this request should be filtered
        host_header = flow.request.host_header
        host = flow.request.host
        path = flow.request.path
        if sni or host_header:
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
                    if log_debug:
                        bubble_log.debug('bubble_handle_request: redirecting to https for LOCAL bubble=' + server_addr +' (bubble_host (' + bubble_host +') in fqdns or bubble_host_alias (' + bubble_host_alias +') in fqdns) for client=' + client_addr +', fqdns=' + repr(fqdns) +', path=' + path)
                    add_flow_ctx(flow, CTX_BUBBLE_ABORT, 301)
                    add_flow_ctx(flow, CTX_BUBBLE_LOCATION, 'https://' + host + path)
                    return None

                elif is_sage_request(server_addr, fqdns):
                    if log_debug:
                        bubble_log.debug('bubble_handle_request: redirecting to https for SAGE server='+server_addr+' for client='+client_addr)
                    add_flow_ctx(flow, CTX_BUBBLE_ABORT, 301)
                    add_flow_ctx(flow, CTX_BUBBLE_LOCATION, 'https://' + host + path)
                    return None

                elif is_not_from_vpn(client_addr):
                    if log_warning:
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
                        if log_info:
                            bubble_log.info('bubble_handle_request: REQ-DECISION PASSTHRU '+log_url+' passthru response returned, passing thru...')
                        add_flow_ctx(flow, CTX_BUBBLE_PASSTHRU, True)
                        bubble_activity_log(client_addr, server_addr, 'http_passthru', log_url)
                        return host

                    elif has_decision and matcher_response['decision'].startswith('abort_'):
                        decision = str(matcher_response['decision'])
                        if log_debug:
                            bubble_log.debug('bubble_handle_request: found abort code: '+decision+', aborting')
                        if decision == 'abort_ok':
                            abort_code = 200
                        elif decision == 'abort_not_found':
                            abort_code = 404
                        else:
                            if log_debug:
                                bubble_log.debug('bubble_handle_request: unknown abort code: '+decision+', aborting with 404 Not Found')
                            abort_code = 404
                        flow.request.headers = nheaders.Headers([])
                        flow.request.content = b''
                        add_flow_ctx(flow, CTX_BUBBLE_ABORT, abort_code)
                        bubble_activity_log(client_addr, server_addr, 'http_abort' + str(abort_code), log_url)
                        if log_info:
                            bubble_log.info('bubble_handle_request: REQ-DECISION: BLOCK '+log_url+' ('+decision+')')
                        return None

                    elif has_decision and matcher_response['decision'] == 'no_match':
                        if log_info:
                            decision = str(matcher_response['decision'])
                            bubble_log.info('bubble_handle_request: REQ-DECISION: ALLOW '+log_url+' ('+decision+')')
                        bubble_activity_log(client_addr, server_addr, 'http_no_match', log_url)
                        return host

                    elif ('matchers' in matcher_response
                          and 'request_id' in matcher_response
                          and len(matcher_response['matchers']) > 0):
                        req_id = matcher_response['request_id']
                        if log_info:
                            bubble_log.info('bubble_handle_request: REQ-DECISION: FILTER '+log_url+' found request_id: '+req_id+' with matchers: '+repr(matcher_response['matchers']))
                        add_flow_ctx(flow, CTX_BUBBLE_MATCHERS, matcher_response['matchers'])
                        add_flow_ctx(flow, CTX_BUBBLE_REQUEST_ID, req_id)
                        bubble_activity_log(client_addr, server_addr, 'http_match', log_url)
                    else:
                        if log_info:
                            bubble_log.info('bubble_handle_request: REQ-DECISION: ALLOW '+log_url+' no rules returned')
                        bubble_activity_log(client_addr, server_addr, 'http_no_rules', log_url)
                else:
                    if log_info:
                        bubble_log.info('bubble_handle_request: REQ-DECISION: ALLOW '+log_url+'no matcher_response')
                    # bubble_activity_log(client_addr, server_addr, 'http_no_matcher_response', log_url)

        elif is_http and is_not_from_vpn(client_addr):
            if log_warning:
                bubble_log.warning('bubble_handle_request: sending to tarpit: non-VPN client='+client_addr)
            bubble_activity_log(client_addr, server_addr, 'http_tarpit_non_vpn', [server_addr])
            tarpit_response(flow, host)
            return None

        else:
            if log_warning:
                bubble_log.warning('bubble_handle_request: no sni/host found, not applying rules to path: ' + path)
            bubble_activity_log(client_addr, server_addr, 'http_no_sni_or_host', [server_addr])

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
                if log_debug:
                    bubble_log.debug('request: is_flex_domain('+host+') returned true, setting ctx: '+CTX_BUBBLE_FLEX)


addons = [Rerouter()]
