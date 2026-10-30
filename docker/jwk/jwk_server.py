#!/usr/bin/env python3
"""本地联调 JWK/JWT 服务器：HTTPS 暴露 jwks.json 与 /token 签发接口。

用法: python3 jwk_server.py [port]
默认端口 9443。证书/密钥/公钥位于本目录（cert.pem / key.pem / jwks.json）。

端点:
  GET /jwks            -> 返回 JWK 公钥（供服务校验 JWT 签名）
  GET /token           -> 签发测试 JWT（默认 sub=...0001，可用 ?sub=xxx 指定）
  GET /token?sub=xxx   -> 签发指定账号的 JWT

仅用于本地开发，生产环境由真实 OIDC Provider 提供 JWK 端点。
"""
import base64
import hashlib
import http.server
import json
import os
import ssl
import sys
import time
import urllib.parse

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 9443
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ISSUER = os.environ.get('JWT_ISSUER', 'https://issuer.example.com')
AUDIENCE = os.environ.get('JWT_AUDIENCE', 'elibrary-api')
TOKEN_TTL = int(os.environ.get('JWT_TTL', '3600'))

# 预置测试账号（与 scripts/reset-test-data.sql 一致）
ACCOUNTS = {
    'u1': '00000000-0000-0000-0000-000000000001',
    'u2': '00000000-0000-0000-0000-000000000002',
    'u3': '00000000-0000-0000-0000-000000000003',
    'u4': '00000000-0000-0000-0000-000000000004',
}


def b64u(data):
    if isinstance(data, str):
        data = data.encode()
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode()


def load_private_key():
    from cryptography.hazmat.primitives import serialization
    with open(os.path.join(BASE_DIR, 'key.pem'), 'rb') as f:
        return serialization.load_pem_private_key(f.read(), password=None)


def sign_token(sub):
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.asymmetric import padding
    key = load_private_key()
    now = int(time.time())
    header = {"alg": "RS256", "typ": "JWT", "kid": "test-key"}
    payload = {"sub": sub, "aud": AUDIENCE, "iss": ISSUER,
               "iat": now, "exp": now + TOKEN_TTL}
    signing_input = b64u(json.dumps(header, separators=(',', ':'))) + '.' + \
        b64u(json.dumps(payload, separators=(',', ':')))
    sig = key.sign(signing_input.encode(), padding.PKCS1v15(), hashes.SHA256())
    return signing_input + '.' + b64u(sig)


class JwkHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.rstrip('/')
        if path == '/jwks':
            self._json_file('jwks.json')
        elif path == '/token':
            params = urllib.parse.parse_qs(parsed.query)
            sub = params.get('sub', [None])[0]
            if sub is None:
                sub = ACCOUNTS['u1']
            token = sign_token(sub)
            body = json.dumps({"token": token, "sub": sub,
                               "iss": ISSUER, "aud": AUDIENCE,
                               "expiresIn": TOKEN_TTL}).encode()
            self._respond(200, 'application/json', body)
        else:
            self._respond(404, 'text/plain', b'not found')

    def _json_file(self, name):
        with open(os.path.join(BASE_DIR, name), 'rb') as f:
            self._respond(200, 'application/json', f.read())

    def _respond(self, status, content_type, body):
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        sys.stderr.write('[jwk] %s\n' % (fmt % args))


def main():
    httpd = http.server.HTTPServer(('0.0.0.0', PORT), JwkHandler)
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.load_cert_chain(
        os.path.join(BASE_DIR, 'cert.pem'),
        os.path.join(BASE_DIR, 'key.pem'))
    httpd.socket = ctx.wrap_socket(httpd.socket, server_side=True)
    print(f'JWK server listening on https://0.0.0.0:{PORT}/jwks', flush=True)
    print(f'Token endpoint: https://0.0.0.0:{PORT}/token', flush=True)
    httpd.serve_forever()


if __name__ == '__main__':
    main()
