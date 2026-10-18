"""Single-person Notes pilot server. Python 3.11+, no third-party dependencies."""
import base64
import hashlib
import hmac
import json
import math
import os
import re
import sqlite3
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlencode
from urllib.request import Request, urlopen


class Service:
    def __init__(self, db_path, config, sender=None, clock=time.time):
        self.config, self.sender, self.clock = config, sender, clock
        self.lock = threading.RLock()
        self.db = sqlite3.connect(db_path, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.executescript('''
            PRAGMA journal_mode=WAL;
            CREATE TABLE IF NOT EXISTS timers (
              id TEXT PRIMARY KEY, deadline REAL, state TEXT, location TEXT);
            CREATE TABLE IF NOT EXISTS alerts (
              id TEXT PRIMARY KEY, kind TEXT, created REAL, location TEXT);
            CREATE TABLE IF NOT EXISTS deliveries (
              alert_id TEXT, phone TEXT, state TEXT, attempts INTEGER DEFAULT 0,
              next_attempt REAL DEFAULT 0, provider_id TEXT,
              PRIMARY KEY(alert_id, phone));
            CREATE TABLE IF NOT EXISTS requests (
              id TEXT PRIMARY KEY, fingerprint TEXT, response TEXT);
        ''')
        # A previous process could have exited after provider acceptance. Retrying
        # is at-least-once: recipients may get a duplicate, never claim exactly-once.
        self.db.execute("UPDATE deliveries SET state='retry' WHERE state='sending'")
        self.db.commit()

    def location(self, value):
        if value is None:
            return None
        if not isinstance(value, dict):
            raise ValueError('Invalid location')
        clean = {}
        for key, low, high in [('latitude', -90, 90), ('longitude', -180, 180),
                               ('accuracy', 0, 100000), ('timestamp', 0, self.clock()+60)]:
            n = value.get(key)
            if isinstance(n, bool) or not isinstance(n, (int, float)) or not math.isfinite(n) or not low <= n <= high:
                raise ValueError('Invalid location')
            clean[key] = n
        if self.clock() - clean['timestamp'] > 120:
            raise ValueError('Location is stale; retry without it')
        return json.dumps(clean)

    def _alert(self, alert_id, kind, location):
        self.db.execute('INSERT OR IGNORE INTO alerts VALUES (?, ?, ?, ?)',
                        (alert_id, kind, self.clock(), location))
        for contact in self.config['contacts']:
            self.db.execute('INSERT OR IGNORE INTO deliveries(alert_id, phone, state) VALUES (?, ?, ?)',
                            (alert_id, contact['phone'], 'pending' if self.sender else 'demo'))

    def _expire(self):
        for row in self.db.execute("SELECT * FROM timers WHERE state='active' AND deadline<=?", (self.clock(),)).fetchall():
            self._alert(row['id'], 'missed_checkin', row['location'])
            self.db.execute("UPDATE timers SET state='expired' WHERE id=?", (row['id'],))

    def command(self, path, body, request_id):
        try:
            uuid.UUID(request_id)
        except (ValueError, TypeError, AttributeError):
            raise ValueError('A UUID Idempotency-Key is required')
        if not isinstance(body, dict):
            raise ValueError('Expected a JSON object')
        fingerprint = hashlib.sha256((path + json.dumps(body, sort_keys=True)).encode()).hexdigest()
        with self.lock, self.db:
            previous = self.db.execute('SELECT * FROM requests WHERE id=?', (request_id,)).fetchone()
            if previous:
                if previous['fingerprint'] != fingerprint:
                    raise ValueError('Idempotency key already used for a different request')
                return json.loads(previous['response'])
            self._expire()
            if path == '/v1/sos':
                self._alert(request_id, 'sos', self.location(body.get('location')))
                result = {'id': request_id, 'state': 'queued' if self.sender else 'demo'}
            elif path == '/v1/checkins':
                minutes = body.get('minutes')
                if isinstance(minutes, bool) or not isinstance(minutes, int) or not 1 <= minutes <= 240:
                    raise ValueError('Choose 1–240 minutes')
                if self.db.execute("SELECT 1 FROM timers WHERE state='active'").fetchone():
                    raise ValueError('An active check-in already exists')
                deadline = self.clock() + minutes * 60
                self.db.execute('INSERT INTO timers VALUES (?, ?, ?, ?)',
                                (request_id, deadline, 'active', self.location(body.get('location'))))
                result = {'id': request_id, 'state': 'active', 'deadline': deadline}
            elif path == '/v1/checkins/complete':
                row = self.db.execute('SELECT * FROM timers WHERE id=?', (str(body.get('id')),)).fetchone()
                if not row:
                    raise ValueError('Check-in not found')
                if row['state'] == 'expired':
                    result = {'id': row['id'], 'state': 'expired'}
                else:
                    self.db.execute("UPDATE timers SET state='complete' WHERE id=?", (row['id'],))
                    result = {'id': row['id'], 'state': 'complete'}
            else:
                raise ValueError('Unknown endpoint')
            self.db.execute('INSERT INTO requests VALUES (?, ?, ?)',
                            (request_id, fingerprint, json.dumps(result)))
            return result

    def status(self):
        with self.lock, self.db:
            self._expire()
            timer = self.db.execute("SELECT id,deadline,state FROM timers ORDER BY deadline DESC LIMIT 1").fetchone()
            alerts = [dict(r) for r in self.db.execute('SELECT id,kind,created FROM alerts ORDER BY created DESC LIMIT 10')]
            for alert in alerts:
                alert['deliveries'] = [dict(r) for r in self.db.execute(
                    'SELECT phone,state,attempts FROM deliveries WHERE alert_id=?', (alert['id'],))]
            return {'mode': 'live' if self.sender else 'demo', 'contacts': self.config['contacts'],
                    'timer': dict(timer) if timer else None, 'alerts': alerts}

    def tick(self):
        with self.lock, self.db:
            self._expire()
            jobs = self.db.execute("""SELECT d.*, a.kind, a.location FROM deliveries d JOIN alerts a
                ON a.id=d.alert_id WHERE d.state IN ('pending','retry') AND d.next_attempt<=?""",
                (self.clock(),)).fetchall()
            for job in jobs:
                self.db.execute("UPDATE deliveries SET state='sending',attempts=attempts+1 WHERE alert_id=? AND phone=?",
                                (job['alert_id'], job['phone']))
        for job in jobs:
            message = f"Notes: {self.config['display_name']} " + (
                'requested help.' if job['kind'] == 'sos' else 'missed a scheduled check-in.')
            message += ' Please try to reach them. Alert ' + job['alert_id'][:8] + '.'
            if job['location']:
                loc = json.loads(job['location'])
                message += (f" Last shared location (not live; captured {time.strftime('%Y-%m-%d %H:%M UTC', time.gmtime(loc['timestamp']))},"
                            f" accuracy ~{round(loc['accuracy'])}m): https://maps.apple.com/?ll={loc['latitude']},{loc['longitude']}")
            try:
                provider_id = self.sender(job['phone'], message)
                state = 'provider_accepted'
            except Exception:
                provider_id = None
                state = 'failed' if job['attempts'] >= 4 else 'retry'
            with self.lock, self.db:
                self.db.execute('UPDATE deliveries SET state=?,provider_id=?,next_attempt=? WHERE alert_id=? AND phone=?',
                                (state, provider_id, self.clock()+min(300, 5*2**job['attempts']), job['alert_id'], job['phone']))


def twilio_sender():
    sid, token, source = (os.environ.get(k) for k in ('TWILIO_ACCOUNT_SID', 'TWILIO_AUTH_TOKEN', 'TWILIO_FROM'))
    if not all((sid, token, source)):
        raise ValueError('Live mode requires all three TWILIO environment variables')
    if not re.fullmatch(r'AC[0-9a-fA-F]{32}', sid):
        raise ValueError('Invalid Twilio account SID')
    def send(phone, message):
        req = Request(f'https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json',
                      data=urlencode({'To': phone, 'From': source, 'Body': message}).encode(),
                      headers={'Authorization': 'Basic ' + base64.b64encode(f'{sid}:{token}'.encode()).decode()})
        with urlopen(req, timeout=15) as response:
            payload = json.load(response)
            if payload.get('status') in ('failed', 'undelivered') or not payload.get('sid'):
                raise ValueError('Provider did not accept message')
            return payload['sid']
    return send


def make_handler(service, api_token):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass  # Do not log tokens, location or phone numbers.

        def reply(self, code, value):
            data = json.dumps(value).encode()
            self.send_response(code)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
            self.wfile.write(data)

        def authorized(self):
            if not hmac.compare_digest(self.headers.get('Authorization', '').encode(), ('Bearer '+api_token).encode()):
                self.reply(401, {'error': 'Unauthorized'})
                return False
            return True

        def do_GET(self):
            if not self.authorized():
                return
            if self.path != '/v1/status':
                self.reply(404, {'error': 'Not found'})
                return
            self.reply(200, service.status())

        def do_POST(self):
            if not self.authorized():
                return
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if not 0 < length <= 8192:
                    raise ValueError('Body must be 1–8192 bytes')
                body = json.loads(self.rfile.read(length))
                self.reply(200, service.command(self.path, body, self.headers.get('Idempotency-Key')))
            except (ValueError, UnicodeDecodeError) as exc:
                self.reply(400, {'error': str(exc)})
            except Exception:
                self.reply(500, {'error': 'Server error; retry with the same request ID'})

        def setup(self):
            super().setup()
            self.connection.settimeout(10)
    return Handler


def main():
    token = os.environ.get('NOTES_API_TOKEN', os.environ.get('HAVEN_API_TOKEN', ''))
    if len(token) < 32:
        raise SystemExit('Set NOTES_API_TOKEN to a random secret of at least 32 characters')
    with open(os.environ.get('NOTES_CONFIG', os.environ.get('HAVEN_CONFIG', 'config.json')), encoding='utf-8') as f:
        config = json.load(f)
    if not isinstance(config.get('display_name'), str) or not 1 <= len(config['display_name']) <= 80:
        raise SystemExit('Configure a display_name of 1–80 characters')
    contacts = config.get('contacts', [])
    if not 1 <= len(contacts) <= 5 or any(not re.fullmatch(r'\+[1-9]\d{7,14}', c.get('phone', '')) or not c.get('name') for c in contacts):
        raise SystemExit('Configure 1–5 consenting contacts with E.164 phone numbers and names')
    mode = os.environ.get('NOTES_MODE', os.environ.get('HAVEN_MODE', 'demo'))
    if mode not in ('demo', 'live'):
        raise SystemExit('NOTES_MODE must be demo or live')
    service = Service(os.environ.get('NOTES_DB', os.environ.get('HAVEN_DB', 'notes.sqlite3')), config, twilio_sender() if mode == 'live' else None)
    def worker():
        while True:
            try:
                service.tick()
            except Exception:
                print('Worker error: inspect server health; pending work will be retried', flush=True)
            time.sleep(1)
    threading.Thread(target=worker, daemon=True).start()
    server = ThreadingHTTPServer((os.environ.get('NOTES_BIND', os.environ.get('HAVEN_BIND', '127.0.0.1')), int(os.environ.get('PORT', '8080'))), make_handler(service, token))
    print(f'Notes {mode} mode listening on {server.server_address}; TLS reverse proxy required for device access', flush=True)
    server.serve_forever()


if __name__ == '__main__':
    main()
