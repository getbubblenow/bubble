import json
import re
from bubble_api import bubble_matchers, bubble_log, HEADER_BUBBLE_MATCHERS, HEADER_BUBBLE_DEVICE, BUBBLE_URI_PREFIX
from bubble_config import bubble_host, bubble_host_alias
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

        if flow.request.path and flow.request.path.startswith(BUBBLE_URI_PREFIX):
            bubble_log("get_matchers: not filtering special bubble path: "+flow.request.path)
            return None

        remote_addr = str(flow.client_conn.address[0])
        try:
            host = host.decode()
        except (UnicodeDecodeError, AttributeError):
            bubble_log("get_matchers: host "+str(host)+" could not be decoded, type="+str(type(host)))
            return None

        if host == bubble_host or host == bubble_host_alias:
            bubble_log("get_matchers: request is for bubble itself ("+host+"), not matching")
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
        if host_header:
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
