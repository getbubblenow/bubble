#
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# Parts of this are borrowed from rawtcp.py in the mitmproxy project. The mitmproxy license is reprinted here:
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
import socket

from OpenSSL import SSL

from mitmproxy.exceptions import MitmproxyException
from mitmproxy import tcp
from mitmproxy import exceptions
from mitmproxy.proxy.protocol import base
from mitmproxy.connections import ServerConnection
from mitmproxy.http import make_connect_request
from mitmproxy.net.http.http1 import assemble_request
from mitmproxy.net.tcp import ssl_read_select

import logging
from logging import INFO, DEBUG, WARNING, ERROR, CRITICAL

bubble_log = logging.getLogger(__name__)


class BubbleFlexPassthruException(MitmproxyException):
    pass


class BubbleFlexPassthruLayer(base.Layer):
    chunk_size = 4096
    proxy_addr = None
    host = None
    port = None

    def __init__(self, ctx, proxy_addr, host, port):
        self.ignore = True
        self.proxy_addr = proxy_addr
        self.server_conn = ServerConnection(proxy_addr)
        self.host = host
        self.port = port
        ctx.server_conn = self.server_conn
        super().__init__(ctx)

    def __call__(self):
        self.connect()
        client = self.client_conn.connection
        server = self.server_conn.connection

        buf = memoryview(bytearray(self.chunk_size))

        # send CONNECT, expect 200 OK
        connect_req = make_connect_request((self.host, self.port))
        server.send(assemble_request(connect_req))
        resp = server.recv(1024).decode()
        if not resp.startswith('HTTP/1.1 200 OK'):
            raise BubbleFlexPassthruException('CONNECT request error: '+resp)

        conns = [client, server]

        # https://github.com/openssl/openssl/issues/6234
        for conn in conns:
            if isinstance(conn, SSL.Connection) and hasattr(SSL._lib, "SSL_clear_mode"):
                SSL._lib.SSL_clear_mode(conn._ssl, SSL._lib.SSL_MODE_AUTO_RETRY)

        try:
            while not self.channel.should_exit.is_set():
                r = ssl_read_select(conns, 10)
                for conn in r:
                    dst = server if conn == client else client
                    try:
                        size = conn.recv_into(buf, self.chunk_size)
                    except (SSL.WantReadError, SSL.WantWriteError):
                        continue
                    if not size:
                        conns.remove(conn)
                        # Shutdown connection to the other peer
                        if isinstance(conn, SSL.Connection):
                            # We can't half-close a connection, so we just close everything here.
                            # Sockets will be cleaned up on a higher level.
                            return
                        else:
                            dst.shutdown(socket.SHUT_WR)

                        if len(conns) == 0:
                            return
                        continue

                    tcp_message = tcp.TCPMessage(dst == server, buf[:size].tobytes())
                    dst.sendall(tcp_message.content)

        except (socket.error, exceptions.TcpException, SSL.Error) as e:
            bubble_log.error('exception: '+repr(e))
