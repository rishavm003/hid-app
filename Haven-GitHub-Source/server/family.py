"""Family pilot: separate member credentials and owner-controlled sharing."""
import hashlib
import hmac
import json
import math
import os
import re
from pathlib import Path
import secrets
import sqlite3
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from server import Service, twilio_sender


def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()


class Family:
    def __init__(self, directory, controller_token, config, sender=None, clock=time.time):
        if len(controller_token) < 32:
            raise ValueError('Controller token must be at least 32 characters')
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True)
        self.controller_hash = digest(controller_token)
        self.config, self.clock, self.sender = config, clock, sender
        self.lock = threading.RLock()
        self.db = sqlite3.connect(self.directory / 'family.sqlite3', check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.executescript('''
            PRAGMA journal_mode=WAL;
            CREATE TABLE IF NOT EXISTS invites (hash TEXT PRIMARY KEY, expires REAL);
            CREATE TABLE IF NOT EXISTS members (
              id TEXT PRIMARY KEY, name TEXT, token_hash TEXT UNIQUE, sharing INTEGER DEFAULT 0,
              revoked INTEGER DEFAULT 0, location TEXT, received REAL, seen REAL);
            CREATE TABLE IF NOT EXISTS notes (
              id TEXT, member_id TEXT, occurred REAL, text TEXT, created REAL,
              PRIMARY KEY(member_id, id));
        ''')
        self.services = {}
        for member in self.db.execute('SELECT * FROM members').fetchall():
            self.services[member['id']] = self._service(member['id'], member['name'])

    def _service(self, member_id, name):
        return Service(str(self.directory / (member_id + '.sqlite3')),
                       {**self.config, 'display_name': name}, self.sender, self.clock)

    def identity(self, token):
        token_hash = digest(token)
        if hmac.compare_digest(token_hash, self.controller_hash):
            return 'controller'
        with self.lock:
            member = self.db.execute('SELECT id FROM members WHERE token_hash=? AND revoked=0', (token_hash,)).fetchone()
            return member['id'] if member else None

    def invite(self):
        code = secrets.token_urlsafe(24)
        with self.lock, self.db:
            self.db.execute('DELETE FROM invites WHERE expires<=?', (self.clock(),))
            self.db.execute('INSERT INTO invites VALUES (?, ?)', (digest(code), self.clock()+600))
        return {'code': code, 'expires': self.clock()+600, 'controller_name': self.config['controller_name']}

    def join(self, body):
        if body.get('consent') is not True:
            raise ValueError('The phone owner must accept joining this family')
        name, code = body.get('name'), body.get('code')
        if not isinstance(name, str) or not 1 <= len(name.strip()) <= 80 or not isinstance(code, str):
            raise ValueError('Enter your name and invitation code')
        with self.lock, self.db:
            invite = self.db.execute('SELECT * FROM invites WHERE hash=? AND expires>?', (digest(code), self.clock())).fetchone()
            if not invite:
                raise ValueError('Invitation is invalid, expired or already used')
            member_id, token = str(uuid.uuid4()), secrets.token_urlsafe(32)
            service = self._service(member_id, name.strip())
            self.db.execute('INSERT INTO members(id,name,token_hash) VALUES (?, ?, ?)', (member_id, name.strip(), digest(token)))
            self.db.execute('DELETE FROM invites WHERE hash=?', (digest(code),))
            self.services[member_id] = service
        return {'id': member_id, 'token': token, 'controller_name': self.config['controller_name']}

    def preview(self, code):
        if not isinstance(code, str):
            raise ValueError('Invitation code required')
        with self.lock:
            row = self.db.execute('SELECT expires FROM invites WHERE hash=? AND expires>?', (digest(code), self.clock())).fetchone()
            if not row:
                raise ValueError('Invitation is invalid, expired or already used')
            return {'controller_name': self.config['controller_name'], 'expires': row['expires']}

    def _member(self, member_id):
        member = self.db.execute('SELECT * FROM members WHERE id=? AND revoked=0', (member_id,)).fetchone()
        if not member:
            raise PermissionError('Member access revoked')
        return member

    def me(self, member_id):
        with self.lock:
            member = self._member(member_id)
            return {'id': member_id, 'name': member['name'], 'sharing': bool(member['sharing']),
                    'controller_name': self.config['controller_name'],
                    'last_location': json.loads(member['location']) if member['location'] else None}

    def sharing(self, member_id, enabled):
        if type(enabled) is not bool:
            raise ValueError('enabled must be true or false')
        with self.lock, self.db:
            self._member(member_id)
            self.db.execute('UPDATE members SET sharing=?, seen=? WHERE id=?', (enabled, self.clock(), member_id))
        return {'sharing': enabled}

    def location(self, member_id, value):
        with self.lock, self.db:
            member = self._member(member_id)
            if not member['sharing']:
                raise ValueError('Sharing is paused by the phone owner')
            location = self.services[member_id].location(value)
            if location is None:
                raise ValueError('A location sample is required')
            sample = json.loads(location)
            old = json.loads(member['location']) if member['location'] else None
            if old and sample['timestamp'] <= old['timestamp']:
                return {'state': 'ignored_older_sample'}
            self.db.execute('UPDATE members SET location=?,received=?,seen=? WHERE id=?',
                            (location, self.clock(), self.clock(), member_id))
        return {'state': 'saved'}

    def note(self, member_id, body):
        note_id, note, occurred = body.get('id'), body.get('text'), body.get('occurred')
        try:
            uuid.UUID(note_id)
        except (ValueError, TypeError, AttributeError):
            raise ValueError('Note requires a UUID')
        if body.get('consent') is not True or not isinstance(note, str) or not 1 <= len(note.strip()) <= 1000:
            raise ValueError('Confirm sharing a note of 1–1000 characters')
        if isinstance(occurred, bool) or not isinstance(occurred, (int, float)) or not math.isfinite(occurred) or not self.clock()-86400*30 <= occurred <= self.clock()+60:
            raise ValueError('Call date must be within the last 30 days')
        with self.lock, self.db:
            self._member(member_id)
            previous = self.db.execute('SELECT * FROM notes WHERE member_id=? AND id=?', (member_id, note_id)).fetchone()
            if previous and (previous['text'] != note.strip() or previous['occurred'] != occurred):
                raise ValueError('Note ID already used')
            self.db.execute('INSERT OR IGNORE INTO notes VALUES (?, ?, ?, ?, ?)', (note_id, member_id, occurred, note.strip(), self.clock()))
        return {'state': 'shared'}

    def erase(self, member_id):
        with self.lock, self.db:
            self._member(member_id)
            self.db.execute('UPDATE members SET sharing=0,location=NULL,received=NULL,seen=NULL WHERE id=?', (member_id,))
            self.db.execute('DELETE FROM notes WHERE member_id=?', (member_id,))
        return {'state': 'erased'}

    def revoke(self, member_id):
        with self.lock, self.db:
            self._member(member_id)
            self.db.execute('UPDATE members SET sharing=0,revoked=1,location=NULL,received=NULL,seen=NULL WHERE id=?', (member_id,))
            self.db.execute('DELETE FROM notes WHERE member_id=?', (member_id,))
        return {'state': 'revoked'}

    def dashboard(self):
        with self.lock, self.db:
            self.db.execute('DELETE FROM notes WHERE created<?', (self.clock()-30*86400,))
            members = []
            for row in self.db.execute('SELECT * FROM members ORDER BY name').fetchall():
                loc = json.loads(row['location']) if row['location'] else None
                age = max(0, self.clock()-loc['timestamp']) if loc else None
                members.append({'id': row['id'], 'name': row['name'], 'sharing': bool(row['sharing']),
                    'revoked': bool(row['revoked']), 'location': loc, 'received': row['received'],
                    'last_contact': row['seen'], 'age_seconds': age,
                    'status': 'revoked' if row['revoked'] else 'paused' if not row['sharing'] else 'no_sample' if not loc else 'stale' if age > 5400 else 'recent',
                    'notes': [dict(n) for n in self.db.execute('SELECT id,occurred,text,created FROM notes WHERE member_id=? ORDER BY occurred DESC LIMIT 100', (row['id'],))]})
            return {'controller_name': self.config['controller_name'], 'members': members, 'server_time': self.clock()}

    def tick(self):
        with self.lock, self.db:
            self.db.execute('DELETE FROM notes WHERE created<?', (self.clock()-30*86400,))
            services = list(self.services.values())
        for service in services:
            service.tick()

    def close(self):
        for service in self.services.values():
            service.db.close()
        self.db.close()


def handler(family, allow_controller=True):
    class Handler(BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(10)

        def log_message(self, *_):
            pass

        def reply(self, code, body):
            data = json.dumps(body).encode()
            self.send_response(code)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
            self.wfile.write(data)

        def dispatch(self, post=False):
            try:
                if not allow_controller and self.path.startswith('/v1/controller/'):
                    self.reply(403, {'error': 'Controller is available only on the PC'})
                    return
                body = {}
                if post:
                    length = int(self.headers.get('Content-Length', '0'))
                    if not 0 < length <= 8192:
                        raise ValueError('Invalid body size')
                    body = json.loads(self.rfile.read(length))
                    if not isinstance(body, dict):
                        raise ValueError('Expected a JSON object')
                if post and self.path == '/v1/family/preview':
                    result = family.preview(body.get('code'))
                elif post and self.path == '/v1/family/join':
                    result = family.join(body)
                else:
                    auth = self.headers.get('Authorization', '')
                    identity = family.identity(auth[7:]) if auth.startswith('Bearer ') else None
                    if identity is None:
                        self.reply(401, {'error': 'Unauthorized'})
                        return
                    if self.path.startswith('/v1/controller/'):
                        if identity != 'controller':
                            raise PermissionError('Controller access required')
                        if not post and self.path == '/v1/controller/members':
                            result = family.dashboard()
                        elif post and self.path == '/v1/controller/invites':
                            result = family.invite()
                        elif post and self.path == '/v1/controller/revoke':
                            result = family.revoke(str(body.get('id')))
                        else:
                            self.reply(404, {'error': 'Not found'})
                            return
                    else:
                        if identity == 'controller':
                            raise PermissionError('Member credentials required; controller cannot enable sharing')
                        # Member identity comes only from the token, never from a request's ID field.
                        if not post and self.path == '/v1/family/me':
                            result = family.me(identity)
                        elif post and self.path == '/v1/family/sharing':
                            result = family.sharing(identity, body.get('enabled'))
                        elif post and self.path == '/v1/family/location':
                            result = family.location(identity, body.get('location'))
                        elif post and self.path == '/v1/family/notes':
                            result = family.note(identity, body)
                        elif post and self.path == '/v1/family/erase':
                            result = family.erase(identity)
                        elif not post and self.path == '/v1/status':
                            result = family.services[identity].status()
                        elif post and self.path in ('/v1/sos', '/v1/checkins', '/v1/checkins/complete'):
                            result = family.services[identity].command(self.path, body, self.headers.get('Idempotency-Key'))
                        else:
                            self.reply(404, {'error': 'Not found'})
                            return
                self.reply(200, result)
            except PermissionError as exc:
                self.reply(403, {'error': str(exc)})
            except (ValueError, UnicodeDecodeError) as exc:
                self.reply(400, {'error': str(exc)})
            except Exception:
                self.reply(500, {'error': 'Server error'})

        def do_GET(self):
            self.dispatch()

        def do_POST(self):
            self.dispatch(True)
    return Handler


def main():
    with open(os.environ.get('HAVEN_CONFIG', 'config.json'), encoding='utf-8') as f:
        config = json.load(f)
    if not isinstance(config.get('controller_name'), str) or not config['controller_name'].strip():
        raise SystemExit('Set controller_name in config.json so members know who receives their data')
    contacts = config.get('contacts', [])
    if not 1 <= len(contacts) <= 5 or any(not isinstance(c, dict) or not re.fullmatch(r'\+[1-9]\d{7,14}', c.get('phone', '')) or not c.get('name') for c in contacts):
        raise SystemExit('Configure 1–5 consenting SMS contacts with names and E.164 numbers')
    mode = os.environ.get('HAVEN_MODE', 'demo')
    if mode not in ('demo', 'live'):
        raise SystemExit('HAVEN_MODE must be demo or live')
    family = Family(os.environ.get('HAVEN_FAMILY_DATA', 'family-data'), os.environ.get('HAVEN_CONTROLLER_TOKEN', ''),
                    config, twilio_sender() if mode == 'live' else None)
    def work():
        while True:
            try:
                family.tick()
            except Exception:
                print('Family worker failed; inspect server health', flush=True)
            time.sleep(1)
    threading.Thread(target=work, daemon=True).start()
    server = ThreadingHTTPServer((os.environ.get('HAVEN_BIND', '127.0.0.1'), int(os.environ.get('PORT', '8080'))), handler(family))
    print(f'Family server in {mode} mode; HTTPS reverse proxy required', flush=True)
    server.serve_forever()


if __name__ == '__main__':
    main()
