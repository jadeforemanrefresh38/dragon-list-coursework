"""HTTP client for an explicitly configured Dragon List test deployment (Python 3.9+)."""
import http.cookiejar
from html.parser import HTMLParser
import json
import os
from urllib.error import HTTPError
from urllib.parse import urlencode, urlparse
from urllib.request import Request, build_opener, HTTPCookieProcessor, HTTPRedirectHandler
import uuid
import struct
import zlib
import ipaddress
from pathlib import Path
BASE_URL = os.environ.get('DRAGON_BASE_URL', 'http://127.0.0.1:8080').rstrip('/')
TUNNEL = os.environ.get('DRAGON_SSH_TUNNEL') == '1'
WAF = os.environ.get('DRAGON_EXPECT_WAF') == '1'

class NoRedirect(HTTPRedirectHandler):

    def redirect_request(self, *args):
        return None

class TunnelCookies(http.cookiejar.DefaultCookiePolicy):

    def return_ok_secure(self, cookie, request):
        host = urlparse(request.full_url).hostname
        if TUNNEL and host == urlparse(BASE_URL).hostname:
            if host == 'localhost' or ipaddress.ip_address(host).is_private:
                return True
        return super().return_ok_secure(cookie, request)

class Fields(HTMLParser):

    def __init__(self, html):
        super().__init__()
        self.values = {}
        self.feed(html)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'input' and attrs.get('name') == '_csrf':
            self.values['csrf'] = attrs.get('value')
        if tag == 'meta' and attrs.get('name') == '_csrf':
            self.values['csrf'] = attrs.get('content')

class Response:

    def __init__(self, status, headers, body):
        self.status = status
        self.headers = headers
        self.body = body
        self.text = body.decode('utf-8', errors='replace')

    def json(self):
        return json.loads(self.text)

class Client:

    def __init__(self):
        self.jar = http.cookiejar.CookieJar(policy=TunnelCookies())
        self.opener = build_opener(HTTPCookieProcessor(self.jar), NoRedirect())
        self.csrf = None

    def request(self, method, path, data=None, headers=None, csrf=True, form=False):
        headers = dict(headers or {})
        if TUNNEL:
            headers['X-Forwarded-Proto'] = 'https'
        if csrf and self.csrf and (method not in ('GET', 'HEAD', 'OPTIONS')):
            headers['X-CSRF-TOKEN'] = self.csrf
        if isinstance(data, dict):
            if form:
                data = urlencode(data).encode()
                headers['Content-Type'] = 'application/x-www-form-urlencoded'
            else:
                data = json.dumps(data, ensure_ascii=False).encode()
                headers['Content-Type'] = 'application/json'
        if isinstance(data, str):
            data = data.encode()
        req = Request(BASE_URL + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(req, timeout=25) as r:
                return Response(r.status, r.headers, r.read())
        except HTTPError as e:
            return Response(e.code, e.headers, e.read())

    def get(self, path):
        return self.request('GET', path)

    def token(self, path='/login'):
        response = self.get(path)
        self.csrf = Fields(response.text).values.get('csrf')
        return response

    def register(self, username, password='TestPass123!'):
        self.token('/register')
        return self.request('POST', '/register', {'login': username, 'password': password, '_csrf': self.csrf}, form=True)

    def login(self, username, password='TestPass123!'):
        self.token()
        r = self.request('POST', '/login', {'username': username, 'password': password, '_csrf': self.csrf}, form=True)
        if r.status == 302 and r.headers.get('Location', '').endswith('/notes'):
            self.token('/notes')
        return r

    def multipart(self, path, fields, file_name=None, file_type='image/png', file_data=b'', field='image'):
        boundary = 'DragonBoundary' + uuid.uuid4().hex
        chunks = []
        for name, value in fields.items():
            chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
        if file_name:
            chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; filename="{file_name}"\r\nContent-Type: {file_type}\r\n\r\n'.encode() + file_data + b'\r\n')
        chunks.append(f'--{boundary}--\r\n'.encode())
        return self.request('POST', path, b''.join(chunks), {'Content-Type': 'multipart/form-data; boundary=' + boundary})

def png():

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 4294967295)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 1, 1, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(b'\x00@\x80@')) + chunk(b'IEND', b'')

def accounts(prefix):
    run = uuid.uuid4().hex[:9]
    clients = []
    for suffix in ('a', 'b'):
        name = prefix + '_' + run + suffix
        c = Client()
        r = c.register(name)
        if r.status != 302:
            raise AssertionError('Registration failed: ' + str(r.status) + ' ' + r.text[:150])
        manifest = Path('.work/reports/test-accounts.jsonl')
        manifest.parent.mkdir(parents=True, exist_ok=True)
        with manifest.open('a', encoding='utf-8') as file:
            file.write(json.dumps({'base': BASE_URL, 'login': name}) + '\n')
        r = c.login(name)
        if r.status != 302 or not c.csrf:
            raise AssertionError('Login failed: ' + str(r.status))
        c.username = name
        clients.append(c)
    return clients
