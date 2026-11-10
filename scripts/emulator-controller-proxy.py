"""Expose an ADB-forwarded controller on the development PC's LAN interface.

Usage: python scripts/emulator-controller-proxy.py 192.0.2.10 18082
Requires: adb forward tcp:18080 tcp:<port displayed by PippyPlayer>
"""
import http.client
import http.server
import sys


class ControllerProxy(http.server.BaseHTTPRequestHandler):
    def forward(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        connection = http.client.HTTPConnection("127.0.0.1", 18080)
        connection.request(self.command, self.path, body, dict(self.headers))
        response = connection.getresponse()
        data = response.read()
        self.send_response(response.status)
        self.send_header("Content-Type", response.getheader("Content-Type"))
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
        connection.close()

    do_GET = forward
    do_POST = forward


http.server.ThreadingHTTPServer((sys.argv[1], int(sys.argv[2])), ControllerProxy).serve_forever()
