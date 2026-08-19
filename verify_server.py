"""
Naven-Modern Verification Server

A simple HTTP server that handles client token verification.
The client sends a token, and the server responds with the expected UUID string
if the token is valid.

Usage:
    python verify_server.py [--port PORT] [--token TOKEN] [--uuid UUID]

Environment variables:
    NVERIFY_PORT  - Server port (default: 8080)
    NVERIFY_TOKEN - Expected token for authentication (default: "admin")
    NVERIFY_UUID  - UUID string returned on successful verification (default: "enter_your_uuid")
"""

import argparse
import hashlib
import json
import os
import sys
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime


class VerifyHandler(BaseHTTPRequestHandler):
    """HTTP request handler for token verification."""

    expected_token = "admin"
    expected_uuid = "enter_your_uuid"
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
            self.send_response(404)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"error": "Not found"}).encode())
            return

        content_length = int(self.headers.get("Content-Length", 0))
        if content_length == 0:
            self.send_response(400)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"error": "Empty body"}).encode())
            return

        body = self.rfile.read(content_length)
        try:
            data = json.loads(body.decode("utf-8"))
        except json.JSONDecodeError:
            self.send_response(400)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"error": "Invalid JSON"}).encode())
            return

        token = data.get("token", "")
        token_hash = hashlib.sha256(token.encode()).hexdigest()[:16]

        if token == self.expected_token:
            self.log_message("VERIFY SUCCESS token_hash=%s", token_hash)
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(self.expected_uuid.encode())
        else:
            self.log_message("VERIFY FAILED token_hash=%s", token_hash)
            self.send_response(403)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"INVALID")

    def do_GET(self):
        if self.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ok"}).encode())
        else:
            self.send_response(404)
            self.end_headers()


def main():
    parser = argparse.ArgumentParser(description="Naven-Modern Verification Server")
    parser.add_argument("--port", type=int, default=None, help="Server port")
    parser.add_argument("--token", type=str, default=None, help="Expected authentication token")
    parser.add_argument("--uuid", type=str, default=None, help="UUID string returned on success")
    parser.add_argument("--log", type=str, default=None, help="Log file path")
    args = parser.parse_args()

    port = args.port or int(os.environ.get("NVERIFY_PORT", "8080"))
    token = args.token or os.environ.get("NVERIFY_TOKEN", "admin")
    uuid = args.uuid or os.environ.get("NVERIFY_UUID", "enter_your_uuid")

    VerifyHandler.expected_token = token
    VerifyHandler.expected_uuid = uuid
    VerifyHandler.log_file = args.log

    server = HTTPServer(("0.0.0.0", port), VerifyHandler)

    print(f"Naven Verification Server")
    print(f"  Port:  {port}")
    print(f"  Token: {token[:2]}{'*' * (len(token) - 2)}")
    print(f"  UUID:  {uuid[:8]}...")
    print(f"  POST /verify - Verify token")
    print(f"  GET  /health - Health check")
    print(f"")
    print(f"Waiting for connections...")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.shutdown()


if __name__ == "__main__":
    main()
