"""
Naven-Modern Verification Server

Validates username/password credentials and returns a token on success.

Usage:
    python verify_server.py [--port PORT] [--user USERNAME] [--pass PASSWORD] [--token TOKEN]

Environment variables:
    NVERIFY_PORT     - Server port (default: 8080)
    NVERIFY_USER     - Expected username (default: "admin")
    NVERIFY_PASS     - Expected password (default: "admin")
    NVERIFY_TOKEN    - Token returned on success (default: "enter_your_uuid")
"""

import argparse
import hashlib
import json
import os
import secrets
import sys
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime


class VerifyHandler(BaseHTTPRequestHandler):
    expected_user = "admin"
    expected_pass = "admin"
    return_token = "enter_your_uuid"
    log_file = None

    def log_message(self, format, *args):
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        msg = f"[{timestamp}] {self.address_string()} - {format % args}"
        print(msg)
        if VerifyHandler.log_file:
            try:
                with open(VerifyHandler.log_file, "a") as f:
                    f.write(msg + "\n")
            except Exception:
                pass

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_POST(self):
        if self.path != "/verify":
            self._send_json(404, {"error": "Not found"})
            return

        content_length = int(self.headers.get("Content-Length", 0))
        if content_length == 0:
            self._send_json(400, {"error": "Empty body"})
            return

        body = self.rfile.read(content_length)
        try:
            data = json.loads(body.decode("utf-8"))
        except json.JSONDecodeError:
            self._send_json(400, {"error": "Invalid JSON"})
            return

        username = data.get("username", "")
        password = data.get("password", "")
        user_hash = hashlib.sha256(username.encode()).hexdigest()[:16]

        if username == self.expected_user and password == self.expected_pass:
            self.log_message("LOGIN SUCCESS user_hash=%s", user_hash)
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(self.return_token.encode())
        else:
            self.log_message("LOGIN FAILED user_hash=%s", user_hash)
            self._send_json(401, {"error": "Invalid credentials"})

    def do_GET(self):
        if self.path == "/health":
            self._send_json(200, {"status": "ok"})
        else:
            self.send_response(404)
            self.end_headers()

    def _send_json(self, code, obj):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(obj).encode())


def main():
    parser = argparse.ArgumentParser(description="Naven-Modern Verification Server")
    parser.add_argument("--port", type=int, default=None, help="Server port")
    parser.add_argument("--user", type=str, default=None, help="Expected username")
    parser.add_argument("--pass", dest="password", type=str, default=None, help="Expected password")
    parser.add_argument("--token", type=str, default=None, help="Token returned on success")
    parser.add_argument("--log", type=str, default=None, help="Log file path")
    args = parser.parse_args()

    port = args.port or int(os.environ.get("NVERIFY_PORT", "8080"))
    user = args.user or os.environ.get("NVERIFY_USER", "admin")
    password = args.password or os.environ.get("NVERIFY_PASS", "admin")
    token = args.token or os.environ.get("NVERIFY_TOKEN", "enter_your_uuid")

    VerifyHandler.expected_user = user
    VerifyHandler.expected_pass = password
    VerifyHandler.return_token = token
    VerifyHandler.log_file = args.log

    server = HTTPServer(("0.0.0.0", port), VerifyHandler)

    print(f"Naven Verification Server")
    print(f"  Port:     {port}")
    print(f"  Username: {user}")
    print(f"  Password: {'*' * len(password)}")
    print(f"  Token:    {token[:8]}...")
    print(f"")
    print(f"  POST /verify  {{username, password}} -> token")
    print(f"  GET  /health  -> {{status: ok}}")
    print(f"")
    print(f"Waiting for connections...")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.shutdown()


if __name__ == "__main__":
    main()
