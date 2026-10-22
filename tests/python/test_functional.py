import unittest
from urllib.parse import urlencode
from client import Client, accounts, png

class FunctionalTests(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.a, cls.b = accounts('functional')

    def setUp(self):
        self.notes = []
        self.tags = []

    def tearDown(self):
        for c, id in self.notes:
            c.request('DELETE', f'/api/notes/{id}/')
            c.request('POST', f'/api/notes/{id}/hard_delete/')
        for id in self.tags:
            self.a.request('DELETE', f'/api/tags/{id}/')

    def note(self, client=None, **fields):
        client = client or self.a
        r = client.request('POST', '/api/notes/', dict(title='Python test', content='Text', **fields))
        self.assertEqual(r.status, 201, r.text)
        id = r.json()['id']
        self.notes.append((client, id))
        return id

    def test_01_pages(self):
        c = Client()
        self.assertEqual(c.get('/login').status, 200)
        self.assertEqual(c.get('/register').status, 200)
        self.assertEqual(self.a.get('/notes').status, 200)

    def test_02_create_and_read(self):
        id = self.note()
        r = self.a.get(f'/api/notes/{id}/')
        self.assertEqual(r.status, 200)
        self.assertEqual(r.json()['title'], 'Python test')

    def test_03_edit_and_created_date(self):
        id = self.note()
        before = self.a.get(f'/api/notes/{id}/').json()
        r = self.a.request('PATCH', f'/api/notes/{id}/', {'title': 'Edited', 'content': 'New text'})
        self.assertEqual(r.status, 200)
        self.assertEqual(r.json()['created_at'], before['created_at'])
        self.assertEqual(self.a.get(f'/api/notes/{id}/').json()['content'], 'New text')

    def test_04_search_and_pagination(self):
        first = self.note()
        second = self.note()
        r = self.a.get('/api/notes/?' + urlencode({'search': 'Python test', 'limit': 1}))
        self.assertEqual(r.status, 200)
        self.assertEqual(len(r.json()['results']), 1)
        self.assertIsNotNone(r.json()['next'])
        self.assertEqual(self.a.get(r.json()['next']).status, 200)

    def test_05_draft(self):
        id = self.note()
        self.assertEqual(self.a.request('PATCH', f'/api/notes/{id}/', {'status': 'draft'}).status, 200)
        self.assertIn(id, [n['id'] for n in self.a.get('/api/notes/?status=draft').json()['results']])

    def test_06_trash_and_restore(self):
        id = self.note()
        self.assertEqual(self.a.request('DELETE', f'/api/notes/{id}/').status, 204)
        self.assertEqual(self.a.get(f'/api/notes/{id}/').json()['status'], 'trashed')
        self.assertEqual(self.a.request('POST', f'/api/notes/{id}/restore/').json()['status'], 'active')

    def test_07_permanent_delete(self):
        id = self.note()
        self.a.request('DELETE', f'/api/notes/{id}/')
        self.assertEqual(self.a.request('POST', f'/api/notes/{id}/hard_delete/').status, 204)
        self.assertEqual(self.a.get(f'/api/notes/{id}/').status, 404)
        self.notes = []

    def test_08_tags_and_filter(self):
        r = self.a.request('POST', '/api/tags/', {'name': 'PythonTag', 'color': '#785394'})
        self.assertEqual(r.status, 201)
        self.tags.append(r.json()['id'])
        id = self.note(tag_names=['PythonTag'])
        r = self.a.get('/api/notes/?tag=PythonTag')
        self.assertIn(id, [n['id'] for n in r.json()['results']])
        self.assertEqual(self.a.request('PATCH', f'/api/tags/{self.tags[0]}/', {'name': 'RenamedTag'}).status, 200)

    def test_09_tasks(self):
        id = self.note(note_type='todo')
        r = self.a.request('POST', '/api/todos/', {'note': id, 'text': 'Task'})
        self.assertEqual(r.status, 201)
        task = r.json()['id']
        r = self.a.request('PATCH', f'/api/todos/{task}/', {'done': True, 'text': 'Done'})
        self.assertTrue(r.json()['done'])
        self.assertEqual(self.a.request('DELETE', f'/api/todos/{task}/').status, 204)

    def test_10_reorder_and_pin(self):
        first = self.note()
        second = self.note()
        self.assertEqual(self.a.request('POST', '/api/notes/reorder/', {'order': [first, second]}).status, 200)
        self.assertEqual(self.a.get('/api/notes/').json()['results'][0]['id'], first)
        self.a.request('PATCH', f'/api/notes/{second}/', {'is_pinned': True})
        self.assertEqual(self.a.get('/api/notes/').json()['results'][0]['id'], second)

    def test_11_bulk(self):
        one = self.note()
        two = self.note()
        self.assertEqual(self.a.request('POST', '/api/notes/bulk/', {'ids': [one, two], 'action': 'draft'}).status, 200)
        self.assertEqual(self.a.get(f'/api/notes/{one}/').json()['status'], 'draft')

    def test_12_image(self):
        id = self.note()
        r = self.a.multipart('/api/images/', {'note': id}, 'pixel.png', file_data=png())
        self.assertEqual(r.status, 201, r.text)
        image = self.a.get(r.json()['url'])
        self.assertEqual(image.status, 200)
        self.assertEqual(image.headers.get('Content-Type'), 'image/png')

    def test_13_profile(self):
        r = self.a.multipart('/api/profile/save/', {'username': self.a.username, 'email': 'python@example.com', 'bio': 'Python integration test'})
        self.assertEqual(r.status, 200, r.text)
        self.assertEqual(self.a.get('/api/profile/').json()['email'], 'python@example.com')

    def test_14_password_and_session_revoke(self):
        other = Client()
        self.assertEqual(other.login(self.a.username).status, 302)
        r = self.a.request('POST', '/api/profile/change_password/', {'old_password': 'TestPass123!', 'new_password1': 'ChangedPass123!', 'new_password2': 'ChangedPass123!'})
        self.assertEqual(r.status, 200, r.text)
        self.assertEqual(other.get('/api/profile/').status, 401)
        self.assertEqual(self.a.get('/api/profile/').status, 200)
        r = self.a.request('POST', '/api/profile/change_password/', {'old_password': 'ChangedPass123!', 'new_password1': 'TestPass123!', 'new_password2': 'TestPass123!'})
        self.assertEqual(r.status, 200)

    def test_15_logout(self):
        c = Client()
        self.assertEqual(c.login(self.b.username).status, 302)
        self.assertEqual(c.request('POST', '/logout').status, 302)
        self.assertEqual(c.get('/api/notes/').status, 401)
if __name__ == '__main__':
    unittest.main()
