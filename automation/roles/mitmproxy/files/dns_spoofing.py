"""
This script makes it possible to use mitmproxy in scenarios where IP spoofing
has been used to redirect connections to mitmproxy. The way this works is that
we rely on either the TLS Server Name Indication (SNI) or the Host header of the
HTTP request. Of course, this is not foolproof - if an HTTPS connection comes
without SNI, we don't know the actual target and cannot construct a certificate
that looks valid. Similarly, if there's no Host header or a spoofed Host header,
we're out of luck as well. Using transparent mode is the better option most of
the time.

Usage:
    mitmproxy
        -p 443
        -s dns_spoofing.py
        # Used as the target location if neither SNI nor host header are present.
        --mode reverse:http://example.com/
        # To avoid auto rewriting of host header by the reverse proxy target.
        --set keep_host_header
    mitmdump
        -p 80
        --mode reverse:http://localhost:443/

    (Setting up a single proxy instance and using iptables to redirect to it
    works as well)
"""
import json
import re
from bubble_api import bubble_matchers, bubble_log, HEADER_BUBBLE_MATCHERS, HEADER_BUBBLE_DEVICE
from mitmproxy import ctx

# This regex extracts splits the host header into host and port.
# Handles the edge case of IPv6 addresses containing colons.
# https://bugzilla.mozilla.org/show_bug.cgi?id=45891
parse_host_header = re.compile(r"^(?P<host>[^:]+|\[.+\])(?::(?P<port>\d+))?$")


class Rerouter:
    @staticmethod
    def get_matchers(flow, host):
        if host is None:
            return None

        remote_addr = str(flow.client_conn.address[0])
        try:
            host = host.decode()
        except (UnicodeDecodeError, AttributeError):
            bubble_log("get_matchers: host "+str(host)+" could not be decoded, type="+str(type(host)))
            return None

        resp = bubble_matchers(remote_addr, flow, host)
        if (not resp) or (not 'matchers' in resp) or (not 'device' in resp):
            bubble_log("get_matchers: no matchers/device for remote_addr/host: "+remote_addr+'/'+str(host))
            return None
        matcher_ids = []
        for m in resp['matchers']:
            if 'urlRegex' in m:
                bubble_log('get_matchers: checking for match of path='+flow.request.path+' against regex: '+m['urlRegex'])
            else:
                bubble_log('get_matchers: checking for match of path='+flow.request.path+' -- NO regex, skipping')
                continue
            if re.match(m['urlRegex'], flow.request.path):
                bubble_log('get_matchers: rule matched, adding rule: '+m['rule'])
                matcher_ids.append(m['uuid'])

        matcher_response = { 'device': resp['device'], 'matchers': matcher_ids }
        bubble_log("get_matchers: returning "+repr(matcher_response))
        return matcher_response

    def request(self, flow):
        if flow.client_conn.tls_established:
            flow.request.scheme = "https"
            sni = flow.client_conn.connection.get_servername()
            port = 443
        else:
            flow.request.scheme = "http"
            sni = None
            port = 80

        host_header = flow.request.host_header
        bubble_log("dns_spoofing.request: host_header is "+repr(host_header))
        m = parse_host_header.match(host_header)
        if m:
            host_header = m.group("host").strip("[]")
            if m.group("port"):
                port = int(m.group("port"))

        # Determine if this request should be filtered
        if sni or host_header:
            matcher_response = self.get_matchers(flow, sni or host_header)
            if matcher_response and 'matchers' in matcher_response and 'device' in matcher_response:
                bubble_log("dns_spoofing.request: found matchers: " + ' '.join(matcher_response['matchers']))
                flow.request.headers[HEADER_BUBBLE_MATCHERS] = json.dumps(matcher_response['matchers'])
                flow.request.headers[HEADER_BUBBLE_DEVICE] = matcher_response['device']
            else:
                bubble_log('dns_spoofing.request: no rules returned, passing thru...')
        else:
            bubble_log('dns_spoofing.request: no sni/host found, not applying rules to path: ' + flow.request.path)

        flow.request.host_header = host_header
        flow.request.host = sni or host_header
        flow.request.port = port


addons = [Rerouter()]
