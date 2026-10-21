import json
from pathlib import Path
import tempfile
import threading
import unittest
import uuid
from http.server import ThreadingHTTPServer
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from family import Family, handler


CONFIG = {'controller_name': 'Parent', 'contacts': [{'name': 'Trusted person', 'phone': '+15555550123'}]}
TOKEN = 'controller-secret-' + 'x'*32


class FamilyTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory(prefix='haven-test-', dir=Path(__file__).resolve().parent)
        self.now = 10000000.0
        self.family = Family(self.folder.name, TOKEN, CONFIG, clock=lambda: self.now)
        self.alice = self.join('Alice')
        self.bob = self.join('Bob')

    def tearDown(self):
        self.family.close()
        self.folder.cleanup()

    def join(self, name):
        invite = self.family.invite()
        return self.family.join({'code': invite['code'], 'name': name, 'consent': True})

    def location(self, timestamp=None):
        return {'latitude': 12.9, 'longitude': 77.6, 'accuracy': 35, 'timestamp': self.now if timestamp is None else timestamp}

    def note(self, text='I chose to share this call note.'):
        return {'id': str(uuid.uuid4()), 'text': text, 'occurred': self.now, 'consent': True}

    def member(self, who=None):
        member_id = (who or self.alice)['id']
        return next(m for m in self.family.dashboard()['members'] if m['id'] == member_id)

    def test_join_requires_consent_and_starts_paused(self):
        invite = self.family.invite()
        with self.assertRaises(ValueError):
            self.family.join({'code': invite['code'], 'name': 'Charlie', 'consent': False})
        self.assertFalse(self.member()['sharing'])
        self.assertIsNone(self.member()['location'])

    def test_invitation_preview_expiry_and_single_use(self):
        invite = self.family.invite()
        self.assertEqual(self.family.preview(invite['code'])['controller_name'], 'Parent')
        self.family.join({'code': invite['code'], 'name': 'Charlie', 'consent': True})
        with self.assertRaises(ValueError):
            self.family.join({'code': invite['code'], 'name': 'Again', 'consent': True})
        invite = self.family.invite()
        self.now += 601
        with self.assertRaises(ValueError):
            self.family.preview(invite['code'])

    def test_token_is_hashed_in_database(self):
        row = self.family.db.execute('SELECT * FROM members WHERE id=?', (self.alice['id'],)).fetchone()
        self.assertNotIn(self.alice['token'], tuple(row))
        self.assertEqual(self.family.identity(self.alice['token']), self.alice['id'])
        self.assertIsNone(self.family.identity('wrong'))
        self.assertEqual(self.family.identity(TOKEN), 'controller')

    def test_location_needs_active_owner_consent(self):
        with self.assertRaises(ValueError):
            self.family.location(self.alice['id'], self.location())
        self.family.sharing(self.alice['id'], True)
        self.family.location(self.alice['id'], self.location())
        self.family.sharing(self.alice['id'], False)
        self.now += 1
        with self.assertRaises(ValueError):
            self.family.location(self.alice['id'], self.location())
        self.assertEqual(self.member()['status'], 'paused')
        self.assertIsNotNone(self.member()['location'])

    def test_last_location_survives_restart_and_becomes_stale(self):
        self.family.sharing(self.alice['id'], True)
        self.family.location(self.alice['id'], self.location())
        original = self.member()['location']
        self.family.close()
        self.family = Family(self.folder.name, TOKEN, CONFIG, clock=lambda: self.now)
        self.now += 7200
        self.assertEqual(self.member()['status'], 'stale')
        self.assertEqual(self.member()['location'], original)
        self.assertEqual(self.member()['age_seconds'], 7200)
        self.assertNotIn('offline', self.member()['status'])

    def test_old_or_invalid_samples_do_not_overwrite_last(self):
        self.family.sharing(self.alice['id'], True)
        self.family.location(self.alice['id'], self.location())
        self.assertEqual(self.family.location(self.alice['id'], self.location(self.now-60))['state'], 'ignored_older_sample')
        with self.assertRaises(ValueError):
            self.family.location(self.alice['id'], self.location(self.now-500))
        with self.assertRaises(ValueError):
            self.family.location(self.alice['id'], {**self.location(), 'latitude': float('nan')})
        self.assertEqual(self.member()['location']['timestamp'], self.now)

    def test_erase_stops_sharing_and_removes_only_members_data(self):
        for who in (self.alice, self.bob):
            self.family.sharing(who['id'], True)
            self.family.location(who['id'], self.location())
            self.family.note(who['id'], self.note())
        self.family.erase(self.alice['id'])
        self.assertFalse(self.member()['sharing'])
        self.assertIsNone(self.member()['location'])
        self.assertEqual(self.member()['notes'], [])
        self.assertEqual(len(self.member(self.bob)['notes']), 1)
        self.assertIsNotNone(self.member(self.bob)['location'])

    def test_notes_require_consent_are_idempotent_and_expire(self):
        note = self.note()
        with self.assertRaises(ValueError):
            self.family.note(self.alice['id'], {**note, 'consent': False})
        self.family.note(self.alice['id'], note)
        self.family.note(self.alice['id'], note)
        self.assertEqual(len(self.member()['notes']), 1)
        with self.assertRaises(ValueError):
            self.family.note(self.alice['id'], {**note, 'text': 'Changed'})
        self.now += 31*86400
        self.assertEqual(self.member()['notes'], [])

    def test_revocation_removes_access_and_shared_data(self):
        self.family.sharing(self.alice['id'], True)
        self.family.location(self.alice['id'], self.location())
        self.family.note(self.alice['id'], self.note())
        self.family.revoke(self.alice['id'])
        self.assertIsNone(self.family.identity(self.alice['token']))
        self.assertIsNone(self.member()['location'])
        self.assertEqual(self.member()['notes'], [])
        with self.assertRaises(PermissionError):
            self.family.sharing(self.alice['id'], True)

    def test_safety_records_are_separate_per_member(self):
        a, b = self.family.services[self.alice['id']], self.family.services[self.bob['id']]
        key = str(uuid.uuid4())
        a.command('/v1/checkins', {'minutes': 1}, key)
        self.assertIsNone(b.status()['timer'])
        b.command('/v1/checkins', {'minutes': 1}, key)
        self.now += 61
        self.family.tick()
        self.assertEqual(len(a.status()['alerts']), 1)
        self.assertEqual(len(b.status()['alerts']), 1)

    def test_http_roles_and_member_id_cannot_be_spoofed(self):
        server = ThreadingHTTPServer(('127.0.0.1', 0), handler(self.family))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        def call(path, token, body=None):
            req = Request(f'http://127.0.0.1:{server.server_port}'+path,
                          data=json.dumps(body).encode() if body is not None else None,
                          headers={'Authorization': 'Bearer '+token, 'Content-Type': 'application/json'})
            with urlopen(req) as response:
                return json.load(response)
        try:
            for path, token, body, code in [
                ('/v1/controller/members', self.alice['token'], None, 403),
                ('/v1/controller/invites', self.bob['token'], {}, 403),
                ('/v1/family/sharing', TOKEN, {'enabled': True}, 403),
                ('/v1/controller/members', 'invalid', None, 401)]:
                with self.assertRaises(HTTPError) as error:
                    call(path, token, body)
                self.assertEqual(error.exception.code, code)
            call('/v1/family/sharing', self.alice['token'], {'enabled': True, 'id': self.bob['id']})
            call('/v1/family/location', self.alice['token'], {'location': self.location(), 'id': self.bob['id']})
            self.assertIsNotNone(self.member()['location'])
            self.assertIsNone(self.member(self.bob)['location'])
            self.assertFalse(self.member(self.bob)['sharing'])
            self.assertEqual(call('/v1/family/me', self.alice['token'])['id'], self.alice['id'])
            self.assertEqual(len(call('/v1/controller/members', TOKEN)['members']), 2)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_public_tunnel_blocks_controller_even_with_valid_token(self):
        server = ThreadingHTTPServer(('127.0.0.1', 0), handler(self.family, allow_controller=False))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            req = Request(f'http://127.0.0.1:{server.server_port}/v1/controller/members', headers={'Authorization': 'Bearer '+TOKEN})
            with self.assertRaises(HTTPError) as error:
                urlopen(req)
            self.assertEqual(error.exception.code, 403)
            req = Request(f'http://127.0.0.1:{server.server_port}/v1/family/me', headers={'Authorization': 'Bearer '+self.alice['token']})
            with urlopen(req) as response:
                self.assertEqual(json.load(response)['id'], self.alice['id'])
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_call_logs_sync_and_24h_window(self):
        calls_data = [
            {'id': 'call1', 'number': '+15551234567', 'name': 'Mom', 'type': 'incoming', 'timestamp': self.now - 3600, 'duration': 120},
            {'id': 'call2', 'number': '+15559876543', 'name': 'Unknown', 'type': 'outgoing', 'timestamp': self.now - 600, 'duration': 45},
            {'id': 'call_old', 'number': '+15550000000', 'name': 'Old', 'type': 'missed', 'timestamp': self.now - 90000, 'duration': 0}
        ]
        res = self.family.calls(self.alice['id'], calls_data)
        self.assertEqual(res['state'], 'saved')

        m = self.member(self.alice)
        self.assertIn('calls', m)
        calls = m['calls']
        # The call from 90000 seconds ago (>24h) should not be returned
        self.assertEqual(len(calls), 2)
        self.assertEqual(calls[0]['call_id'], 'call2')
        self.assertEqual(calls[1]['call_id'], 'call1')
        self.assertEqual(calls[1]['number'], '+15551234567')
        self.assertEqual(calls[1]['name'], 'Mom')
        self.assertEqual(calls[1]['call_type'], 'incoming')

        # Test erase clears calls
        self.family.erase(self.alice['id'])
        self.assertEqual(len(self.member(self.alice)['calls']), 0)


if __name__ == '__main__':
    unittest.main()
