import json
from pathlib import Path
import tempfile
import threading
import unittest
import uuid
from http.server import ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from server import Service, make_handler


CONFIG = {'display_name': 'Test user', 'contacts': [{'name': 'Test contact', 'phone': '+15555550123'}]}


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory(prefix='haven-test-', dir=Path(__file__).resolve().parent)
        self.now = 1000.0
        self.sent = []
        self.path = self.folder.name + '/test.sqlite3'
        self.service = Service(self.path, CONFIG, self.send, lambda: self.now)

    def tearDown(self):
        self.service.db.close()
        self.folder.cleanup()

    def send(self, phone, message):
        self.sent.append((phone, message))
        return 'provider-id'

    def command(self, path, body=None, key=None):
        return self.service.command(path, body or {}, key or str(uuid.uuid4()))

    def test_sos_retries_are_idempotent(self):
        key = str(uuid.uuid4())
        a = self.command('/v1/sos', key=key)
        self.assertEqual(a, self.command('/v1/sos', key=key))
        self.service.tick()
        self.service.tick()
        self.assertEqual(len(self.sent), 1)
        self.assertEqual(self.service.status()['alerts'][0]['deliveries'][0]['state'], 'provider_accepted')

    def test_reused_key_cannot_change_operation(self):
        key = str(uuid.uuid4())
        self.command('/v1/sos', key=key)
        with self.assertRaises(ValueError):
            self.command('/v1/checkins', {'minutes': 1}, key)

    def test_timer_survives_restart_and_expires_once(self):
        self.command('/v1/checkins', {'minutes': 1})
        self.service.db.close()
        self.service = Service(self.path, CONFIG, self.send, lambda: self.now)
        self.now += 61
        self.service.tick()
        self.service.tick()
        self.assertEqual(len(self.sent), 1)
        self.assertEqual(self.service.status()['timer']['state'], 'expired')

    def test_successful_checkin_prevents_alert(self):
        timer = self.command('/v1/checkins', {'minutes': 1})
        self.command('/v1/checkins/complete', {'id': timer['id']})
        self.now += 61
        self.service.tick()
        self.assertEqual(self.sent, [])

    def test_late_checkin_cannot_silently_cancel_alert(self):
        timer = self.command('/v1/checkins', {'minutes': 1})
        self.now += 60
        result = self.command('/v1/checkins/complete', {'id': timer['id']})
        self.assertEqual(result['state'], 'expired')
        self.service.tick()
        self.assertEqual(len(self.sent), 1)

    def test_second_active_timer_rejected(self):
        self.command('/v1/checkins', {'minutes': 1})
        with self.assertRaises(ValueError):
            self.command('/v1/checkins', {'minutes': 5})

    def test_location_validation_and_optional_location(self):
        for latitude in [float('nan'), float('inf'), 91, True]:
            with self.assertRaises(ValueError):
                self.command('/v1/sos', {'location': {'latitude': latitude, 'longitude': 1, 'accuracy': 10, 'timestamp': 1000}})
        with self.assertRaises(ValueError):
            self.command('/v1/sos', {'location': {'latitude': 1, 'longitude': 1, 'accuracy': 10, 'timestamp': 1}})
        self.command('/v1/sos')
        self.service.tick()
        self.assertNotIn('maps.apple', self.sent[0][1])

    def test_location_is_labelled_as_last_shared(self):
        self.command('/v1/sos', {'location': {'latitude': 1, 'longitude': 2, 'accuracy': 10, 'timestamp': 1000}})
        self.service.tick()
        self.assertIn('not live', self.sent[0][1])
        self.assertIn('https://maps.apple.com/?ll=1,2', self.sent[0][1])

    def test_demo_never_sends(self):
        self.service.sender = None
        self.assertEqual(self.command('/v1/sos')['state'], 'demo')
        self.service.tick()
        self.assertEqual(self.sent, [])
        self.assertEqual(self.service.status()['alerts'][0]['deliveries'][0]['state'], 'demo')

    def test_failure_retries_then_reports_failure(self):
        def fail(*_):
            raise OSError('network down')
        self.service.sender = fail
        self.command('/v1/sos')
        for _ in range(5):
            self.service.tick()
            self.now += 301
        delivery = self.service.status()['alerts'][0]['deliveries'][0]
        self.assertEqual(delivery['state'], 'failed')
        self.assertEqual(delivery['attempts'], 5)

    def test_concurrent_expiry_and_completion_have_one_outcome(self):
        timer = self.command('/v1/checkins', {'minutes': 1})
        self.now += 61
        threads = [threading.Thread(target=self.service.tick),
                   threading.Thread(target=lambda: self.command('/v1/checkins/complete', {'id': timer['id']}))]
        for thread in threads: thread.start()
        for thread in threads: thread.join()
        self.service.tick()
        self.assertEqual(len(self.sent), 1)

    def test_http_auth_validation_and_status(self):
        server = ThreadingHTTPServer(('127.0.0.1', 0), make_handler(self.service, 'x'*32))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f'http://127.0.0.1:{server.server_port}'
        try:
            with self.assertRaises(HTTPError) as error:
                urlopen(base+'/v1/status')
            self.assertEqual(error.exception.code, 401)
            req = Request(base+'/v1/status', headers={'Authorization': 'Bearer '+'x'*32})
            with urlopen(req) as response:
                self.assertEqual(json.load(response)['mode'], 'live')
                self.assertEqual(response.headers['Cache-Control'], 'no-store')
            req = Request(base+'/v1/sos', data=b'[]', headers={'Authorization': 'Bearer '+'x'*32, 'Idempotency-Key': str(uuid.uuid4())})
            with self.assertRaises(HTTPError) as error:
                urlopen(req)
            self.assertEqual(error.exception.code, 400)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == '__main__':
    unittest.main()
