# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Ephemeral localhost certificate fixture; no private key leaves the temporary directory."""
from contextlib import contextmanager
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import ssl
import subprocess
import tempfile
import threading


@contextmanager
def tls_server():
    paths = []
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def handle(self):
            try:
                super().handle()
            except (ConnectionResetError, BrokenPipeError):
                pass

        def do_GET(self):
            paths.append(self.path)
            self.send_response(200)
            self.send_header("Connection", "close")
            self.send_header("Content-Length", "7")
            self.end_headers()
            self.wfile.write(b"trusted")

    with tempfile.TemporaryDirectory(prefix="gauja-tls-") as temporary:
        directory = Path(temporary)
        key, cert = directory / "key.pem", directory / "cert.pem"
        subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                        "-subj", "/CN=localhost", "-addext", "subjectAltName=DNS:localhost",
                        "-keyout", str(key), "-out", str(cert)], check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        key.chmod(0o600)
        fingerprint = hashlib.sha256(ssl.PEM_cert_to_DER_cert(cert.read_text())).hexdigest()
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(cert, key)
        with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
            server.socket = context.wrap_socket(server.socket, server_side=True)
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                yield {"GAUJA_TLS_SERVER": f"https://localhost:{server.server_port}", "GAUJA_TLS_FINGERPRINT": fingerprint}, paths
            finally:
                server.shutdown()
                worker.join()
