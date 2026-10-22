import unittest
from urllib.parse import urlencode
from client import Client, accounts, png, WAF, BASE_URL

class SecurityTests(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.a, cls.b = accounts('security')

    def setUp(self):
        self.notes = []

    def tearDown(self):
        for c, id in self.notes:
            c.request('DELETE', f'/api/notes/{id}/')
            c.request('POST', f'/api/notes/{id}/hard_delete/')

    def note(self, client=None, **fields):
        client = client or self.a
        r = client.request('POST', '/api/notes/', dict(title='Security test', **fields))
        self.assertEqual(r.status, 201, r.text)
        id = r.json()['id']
        self.notes.append((client, id))
        return id

    def test_01_anonymous_api(self):
        self.assertEqual(Client().get('/api/notes/').status, 401)

    def test_02_missing_csrf(self):
        self.assertEqual(self.a.request('POST', '/api/notes/', {'title': 'Denied'}, csrf=False).status, 403)

    def test_03_invalid_csrf(self):
        self.assertEqual(self.a.request('POST', '/api/notes/', {'title': 'Denied'}, {'X-CSRF-TOKEN': 'invalid'}, csrf=False).status, 403)

    def test_04_other_note_read(self):
        id = self.note(self.b)
        self.assertEqual(self.a.get(f'/api/notes/{id}/').status, 404)

    def test_05_other_note_update(self):
        id = self.note(self.b)
        self.assertEqual(self.a.request('PATCH', f'/api/notes/{id}/', {'title': 'Hack'}).status, 404)

    def test_06_other_note_delete(self):
        id = self.note(self.b)
        self.assertEqual(self.a.request('DELETE', f'/api/notes/{id}/').status, 404)

    def test_07_bulk_is_atomic(self):
        own = self.note()
        foreign = self.note(self.b)
        self.assertEqual(self.a.request('POST', '/api/notes/bulk/', {'ids': [own, foreign], 'action': 'trash'}).status, 404)
        self.assertEqual(self.a.get(f'/api/notes/{own}/').json()['status'], 'active')

    def test_08_foreign_task(self):
        id = self.note(self.b, note_type='todo')
        task = self.b.request('POST', '/api/todos/', {'note': id, 'text': 'Private'}).json()['id']
        self.assertEqual(self.a.request('PATCH', f'/api/todos/{task}/', {'done': True}).status, 404)

    def test_09_foreign_image(self):
        id = self.note(self.b)
        r = self.b.multipart('/api/images/', {'note': id}, 'private.png', file_data=png())
        self.assertEqual(r.status, 201, r.text)
        self.assertEqual(self.a.get(r.json()['url']).status, 404)

    def test_10_invalid_image(self):
        id = self.note()
        self.assertIn(self.a.multipart('/api/images/', {'note': id}, 'bad.svg', 'image/svg+xml', b'<svg/>').status, (400, 403))

    def test_11_oversized_image(self):
        id = self.note()
        self.assertIn(self.a.multipart('/api/images/', {'note': id}, 'large.png', file_data=b'x' * (5 * 1024 * 1024 + 1)).status, (400, 403, 413))

    def test_12_invalid_color(self):
        self.assertIn(self.a.request('POST', '/api/tags/', {'name': 'Bad', 'color': 'javascript:alert(1)'}).status, (400, 403))

    def test_13_oversized_title(self):
        self.assertEqual(self.a.request('POST', '/api/notes/', {'title': 'x' * 201}).status, 400)

    def test_14_pagination_limit(self):
        self.assertEqual(self.a.get('/api/notes/?limit=100000').status, 400)

    def test_15_security_headers(self):
        r = Client().get('/login')
        self.assertEqual(r.headers.get('X-Frame-Options'), 'DENY')
        self.assertEqual(r.headers.get('X-Content-Type-Options'), 'nosniff')
        self.assertIn("script-src 'self'", r.headers.get('Content-Security-Policy', ''))
        self.assertIn('camera=()', r.headers.get('Permissions-Policy', ''))
        if BASE_URL.startswith('https://'):
            self.assertIn('max-age=', r.headers.get('Strict-Transport-Security', ''))

    def test_16_sql_is_literal_note_data(self):
        text = "' OR 1=1; DROP TABLE users; --"
        id = self.note(content=text)
        self.assertEqual(self.a.get(f'/api/notes/{id}/').json()['content'], text)
        self.assertEqual(self.a.get('/api/profile/').status, 200)

    @unittest.skipUnless(WAF, 'Requires deployed WAF')
    def test_17_waf_blocks_sql_query(self):
        self.assertEqual(self.a.get('/api/notes/?' + urlencode({'search': '1 UNION SELECT password FROM users--'})).status, 403)

    @unittest.skipUnless(WAF, 'Requires deployed WAF')
    def test_18_waf_blocks_xss_query(self):
        self.assertEqual(Client().get('/login?' + urlencode({'q': '<script>alert(1)</script>'})).status, 403)

    @unittest.skipUnless(WAF, 'Requires deployed WAF')
    def test_19_waf_blocks_path_traversal(self):
        self.assertEqual(Client().get('/login?' + urlencode({'file': '../../../../etc/passwd'})).status, 403)

    @unittest.skipUnless(WAF, 'Requires deployed WAF')
    def test_20_waf_blocks_command_injection(self):
        self.assertEqual(Client().get('/login?' + urlencode({'cmd': ';cat /etc/passwd'})).status, 403)

    def test_21_cookies(self):
        cookies = list(self.a.jar)
        self.assertTrue(cookies)
        session = next((c for c in cookies if c.name == 'JSESSIONID'))
        self.assertTrue(session.has_nonstandard_attr('HttpOnly'))
        self.assertEqual(session.get_nonstandard_attr('SameSite').lower(), 'lax')
        if BASE_URL.startswith('https://'):
            self.assertTrue(session.secure)

    def test_22_bad_password_is_rejected(self):
        c = Client()
        r = c.login(self.a.username, 'WrongPassword123!')
        self.assertEqual(r.status, 302)
        self.assertIn('error', r.headers.get('Location', ''))
        self.assertEqual(c.get('/api/profile/').status, 401)

    def test_23_malformed_json(self):
        self.assertIn(self.a.request('POST', '/api/notes/', '{bad json', {'Content-Type': 'application/json'}).status, (400, 403))

    def test_24_no_sql_error_details(self):
        r = self.a.get('/api/notes/not-a-number/')
        self.assertEqual(r.status, 400)
        self.assertNotIn('jdbc:', r.text.lower())
        self.assertNotIn('exception', r.text.lower())

    def test_25_html_is_literal_note_data(self):
        text = '<script>alert(1)</script>'
        id = self.note(content=text)
        self.assertEqual(self.a.get(f'/api/notes/{id}/').json()['content'], text)

    def test_99_login_throttle(self):
        c = Client()
        c.token()
        statuses = []
        for i in range(10):
            statuses.append(c.request('POST', '/login', {'username': 'throttle_' + self.a.username[-8:], 'password': 'Wrong123!', '_csrf': c.csrf}, {'X-Dragon-Client-IP': '198.51.100.' + str(i + 1)}, form=True).status)
        self.assertIn(429, statuses)
if __name__ == '__main__':
    unittest.main()
