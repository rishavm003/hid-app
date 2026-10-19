"""24/7 Cloud Server with Mobile Web Dashboard for Notes / Lost Phone Locator."""
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

# Ensure server package path is available
ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
from family import Family, digest

PORT = int(os.environ.get('PORT', '8080'))
DATA_DIR = Path(os.environ.get('DATA_DIR', ROOT / 'cloud_data'))
DATA_DIR.mkdir(parents=True, exist_ok=True)

# Master Dashboard Access PIN / Password
DASHBOARD_PIN = os.environ.get('DASHBOARD_PIN', '1998')

# Persistent Controller Token
TOKEN_FILE = DATA_DIR / 'controller_token.txt'
if TOKEN_FILE.exists():
    CONTROLLER_TOKEN = TOKEN_FILE.read_text().strip()
else:
    CONTROLLER_TOKEN = secrets.token_urlsafe(32)
    TOKEN_FILE.write_text(CONTROLLER_TOKEN)

family = Family(
    directory=str(DATA_DIR),
    controller_token=CONTROLLER_TOKEN,
    config={'controller_name': 'Notes Cloud 24/7', 'contacts': []}
)

# Start background maintenance tick
def background_worker():
    while True:
        try:
            family.tick()
        except Exception:
            pass
        time.sleep(1)

threading.Thread(target=background_worker, daemon=True).start()

DASHBOARD_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Notes 🌸 • Device Locator</title>
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <style>
        :root {
            --bg: #0f141c;
            --card: #192130;
            --card-border: #28354c;
            --primary: #f28b82;
            --accent: #81c995;
            --text: #e8eaed;
            --muted: #9aa0a6;
            --danger: #f28b82;
            --btn: #2d3b55;
            --btn-hover: #3b4d6e;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: var(--bg); color: var(--text); min-height: 100vh; padding-bottom: 30px; }
        
        header {
            background: var(--card);
            border-bottom: 1px solid var(--card-border);
            padding: 16px 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            position: sticky;
            top: 0;
            z-index: 1000;
        }
        .logo { font-size: 1.25rem; font-weight: 700; color: #ffb4a2; display: flex; align-items: center; gap: 8px; }
        .logo span { font-size: 0.8rem; background: rgba(255, 180, 162, 0.15); color: #ffb4a2; padding: 2px 8px; border-radius: 20px; }
        .refresh-btn {
            background: var(--btn);
            color: var(--text);
            border: 1px solid var(--card-border);
            padding: 8px 14px;
            border-radius: 12px;
            font-size: 0.85rem;
            cursor: pointer;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .refresh-btn:hover { background: var(--btn-hover); }

        .container { max-width: 900px; margin: 0 auto; padding: 16px; }

        #map {
            width: 100%;
            height: 320px;
            border-radius: 16px;
            border: 1px solid var(--card-border);
            margin-bottom: 20px;
            z-index: 10;
        }

        .auth-box {
            background: var(--card);
            border: 1px solid var(--card-border);
            border-radius: 20px;
            padding: 30px 24px;
            max-width: 380px;
            margin: 60px auto;
            text-align: center;
        }
        .auth-box h2 { margin-bottom: 12px; color: #ffb4a2; font-size: 1.4rem; }
        .auth-box p { color: var(--muted); font-size: 0.9rem; margin-bottom: 20px; line-height: 1.4; }
        .auth-input {
            width: 100%;
            background: #0f141c;
            border: 1px solid var(--card-border);
            padding: 12px 16px;
            border-radius: 12px;
            color: var(--text);
            font-size: 1.1rem;
            text-align: center;
            letter-spacing: 4px;
            margin-bottom: 16px;
            outline: none;
        }
        .auth-input:focus { border-color: var(--primary); }
        .auth-btn {
            width: 100%;
            background: #ffb4a2;
            color: #2b130e;
            border: none;
            padding: 12px;
            border-radius: 12px;
            font-weight: 700;
            font-size: 1rem;
            cursor: pointer;
        }

        .section-title {
            font-size: 1.05rem;
            font-weight: 600;
            color: var(--muted);
            margin-bottom: 12px;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        .device-card {
            background: var(--card);
            border: 1px solid var(--card-border);
            border-radius: 16px;
            padding: 16px 20px;
            margin-bottom: 16px;
            transition: transform 0.15s ease, border-color 0.15s ease;
        }
        .device-card:hover { border-color: #3d4f70; }
        .device-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 10px;
        }
        .device-name {
            font-size: 1.2rem;
            font-weight: 700;
            color: #fff;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .badge {
            font-size: 0.75rem;
            font-weight: 600;
            padding: 4px 10px;
            border-radius: 20px;
        }
        .badge-battery { background: rgba(129, 201, 149, 0.15); color: #81c995; border: 1px solid rgba(129, 201, 149, 0.3); }
        .badge-battery.low { background: rgba(242, 139, 130, 0.15); color: #f28b82; border-color: rgba(242, 139, 130, 0.3); }
        .badge-battery.charging { background: rgba(253, 214, 99, 0.15); color: #fdd663; border-color: rgba(253, 214, 99, 0.3); }
        
        .device-info {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 10px;
            font-size: 0.85rem;
            color: var(--muted);
            margin-bottom: 14px;
        }
        .info-item span { display: block; color: var(--text); font-weight: 500; font-size: 0.95rem; margin-top: 2px; }

        .device-actions {
            display: flex;
            gap: 10px;
        }
        .action-btn {
            flex: 1;
            padding: 10px 14px;
            border-radius: 10px;
            font-size: 0.85rem;
            font-weight: 600;
            border: 1px solid var(--card-border);
            cursor: pointer;
            text-align: center;
            text-decoration: none;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
        }
        .btn-locate { background: rgba(255, 180, 162, 0.15); color: #ffb4a2; border-color: rgba(255, 180, 162, 0.3); }
        .btn-locate:hover { background: rgba(255, 180, 162, 0.25); }
        .btn-maps { background: var(--btn); color: var(--text); }
        .btn-maps:hover { background: var(--btn-hover); }

        .empty-state {
            text-align: center;
            padding: 40px 20px;
            color: var(--muted);
            background: var(--card);
            border-radius: 16px;
            border: 1px solid var(--card-border);
        }
        .status-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            display: inline-block;
        }
        .dot-green { background: #81c995; box-shadow: 0 0 8px #81c995; }
        .dot-yellow { background: #fdd663; box-shadow: 0 0 8px #fdd663; }
        .dot-gray { background: #5f6368; }
    </style>
</head>
<body>

<div id="authView" class="auth-box" style="display: none;">
    <h2>Notes 🌸 Locator</h2>
    <p>Enter your master security PIN to access live family device tracking.</p>
    <input type="password" id="pinInput" class="auth-input" placeholder="PIN" maxlength="10" />
    <button class="auth-btn" onclick="submitPin()">Unlock Dashboard</button>
</div>

<div id="mainView" style="display: none;">
    <header>
        <div class="logo">
            Notes 🌸
            <span>24/7 Cloud</span>
        </div>
        <button class="refresh-btn" onclick="loadDevices(true)">
            🔄 Refresh
        </button>
    </header>

    <div class="container">
        <div id="map"></div>

        <div class="section-title">
            <span>TRACKED DEVICES</span>
            <span id="lastUpdated" style="font-size: 0.8rem; font-weight: 400;">Auto-refreshes every 15s</span>
        </div>

        <div id="devicesList"></div>
    </div>
</div>

<script>
    let map = null;
    let markers = {};
    let pin = localStorage.getItem('notes_pin') || '';

    function checkAuth() {
        if (!pin) {
            document.getElementById('authView').style.display = 'block';
            document.getElementById('mainView').style.display = 'none';
        } else {
            document.getElementById('authView').style.display = 'none';
            document.getElementById('mainView').style.display = 'block';
            initMap();
            loadDevices();
            setInterval(loadDevices, 15000);
        }
    }

    function submitPin() {
        const val = document.getElementById('pinInput').value.trim();
        if (val) {
            pin = val;
            localStorage.setItem('notes_pin', pin);
            checkAuth();
        }
    }

    function initMap() {
        if (map) return;
        map = L.map('map').setView([20.5937, 78.9629], 5);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '© OpenStreetMap contributors'
        }).addTo(map);
    }

    function timeAgo(epochSec) {
        if (!epochSec) return 'Never';
        const sec = Math.floor((Date.now() / 1000) - epochSec);
        if (sec < 60) return `${sec}s ago`;
        if (sec < 3600) return `${Math.floor(sec / 60)}m ago`;
        if (sec < 86400) return `${Math.floor(sec / 3600)}h ago`;
        return `${Math.floor(sec / 86400)}d ago`;
    }

    async function loadDevices(manual = false) {
        try {
            const resp = await fetch('/api/dashboard?pin=' + encodeURIComponent(pin));
            if (resp.status === 401) {
                localStorage.removeItem('notes_pin');
                pin = '';
                checkAuth();
                return;
            }
            const data = await resp.json();
            renderDevices(data.members || []);
            document.getElementById('lastUpdated').innerText = 'Refreshed ' + new Date().toLocaleTimeString();
        } catch (e) {
            console.error('Error fetching devices', e);
        }
    }

    function renderDevices(members) {
        const list = document.getElementById('devicesList');
        if (!members.length) {
            list.innerHTML = '<div class="empty-state">No devices registered yet.<br>Install Notes.apk on a device to see it here.</div>';
            return;
        }

        let bounds = [];
        let html = '';

        members.forEach(m => {
            const loc = m.location;
            const bat = m.battery;
            const charging = m.charging;
            let batClass = 'badge-battery';
            let batText = '—';
            if (bat !== null && bat !== undefined) {
                if (charging) { batClass += ' charging'; batText = '⚡ ' + bat + '%'; }
                else if (bat <= 15) { batClass += ' low'; batText = '⚠️ ' + bat + '%'; }
                else { batText = '🔋 ' + bat + '%'; }
            }

            const isLocating = m.locate_requested;
            const statusDot = m.status === 'recent' ? '<span class="status-dot dot-green"></span>' : '<span class="status-dot dot-yellow"></span>';
            const statusLabel = isLocating ? '📍 LOCATING...' : (m.status === 'recent' ? 'Active' : 'Stale');

            let coordsText = 'No location';
            let accuracyText = '—';
            let timeText = timeAgo(m.last_contact);
            let mapsUrl = '#';

            if (loc) {
                coordsText = `${loc.latitude.toFixed(5)}, ${loc.longitude.toFixed(5)}`;
                accuracyText = `±${Math.round(loc.accuracy || 0)} m`;
                timeText = timeAgo(loc.timestamp);
                mapsUrl = `https://www.google.com/maps?q=${loc.latitude},${loc.longitude}`;
                bounds.push([loc.latitude, loc.longitude]);

                // Map Marker
                if (!markers[m.id]) {
                    markers[m.id] = L.marker([loc.latitude, loc.longitude]).addTo(map);
                } else {
                    markers[m.id].setLatLng([loc.latitude, loc.longitude]);
                }
                markers[m.id].bindPopup(`<b>${m.name}</b><br>${batText}<br>${timeText}`).openPopup();
            }

            html += `
                <div class="device-card">
                    <div class="device-header">
                        <div class="device-name">
                            ${statusDot}
                            ${m.name}
                        </div>
                        <div class="badge ${batClass}">
                            ${batText}
                        </div>
                    </div>
                    <div class="device-info">
                        <div class="info-item">Status: <span>${statusLabel}</span></div>
                        <div class="info-item">Last Seen: <span>${timeText}</span></div>
                        <div class="info-item">Coordinates: <span>${coordsText}</span></div>
                        <div class="info-item">Accuracy: <span>${accuracyText}</span></div>
                    </div>
                    <div class="device-actions">
                        <button class="action-btn btn-locate" onclick="locateDevice('${m.id}')">
                            📍 Locate Device Now
                        </button>
                        ${loc ? `<a href="${mapsUrl}" target="_blank" class="action-btn btn-maps">🗺️ Open in Google Maps</a>` : ''}
                    </div>
                </div>
            `;
        });

        list.innerHTML = html;

        if (bounds.length && map) {
            map.fitBounds(bounds, { padding: [50, 50], maxZoom: 16 });
        }
    }

    async function locateDevice(memberId) {
        try {
            const resp = await fetch('/api/locate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: memberId, pin: pin })
            });
            if (resp.ok) {
                alert('📍 Locate request sent! The phone will capture and upload fresh GPS coordinates shortly.');
                loadDevices();
                setTimeout(loadDevices, 4000);
                setTimeout(loadDevices, 10000);
            } else {
                alert('Could not send locate request. Please verify your PIN.');
            }
        } catch (e) {
            alert('Failed to connect to cloud server.');
        }
    }

    checkAuth();
</script>

</body>
</html>
"""

class CloudHandler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(15)

    def log_message(self, format, *args):
        # Clean logging
        pass

    def reply_json(self, code, body):
        data = json.dumps(body).encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        self.wfile.write(data)

    def reply_html(self, code, html):
        data = html.encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        self.wfile.write(data)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Authorization')
        self.end_headers()

    def do_GET(self):
        url = urlparse(self.path)
        path = url.path

        # 1. Web Dashboard View
        if path in ('/', '/dashboard', '/index.html'):
            self.reply_html(200, DASHBOARD_HTML)
            return

        # 2. Web Dashboard API: get devices
        if path == '/api/dashboard':
            params = parse_qs(url.query)
            provided_pin = params.get('pin', [''])[0]
            if provided_pin != DASHBOARD_PIN:
                self.reply_json(401, {'error': 'Invalid PIN'})
                return
            dashboard = family.dashboard()
            self.reply_json(200, dashboard)
            return

        # 3. Android API: /v1/family/ping
        if path == '/v1/family/ping':
            auth = self.headers.get('Authorization', '')
            token = auth[7:] if auth.startswith('Bearer ') else None
            identity = family.identity(token) if token else None
            if not identity:
                self.reply_json(401, {'error': 'Unauthorized'})
                return
            result = family.ping(identity)
            self.reply_json(200, result)
            return

        # 4. Android API: /v1/family/me
        if path == '/v1/family/me':
            auth = self.headers.get('Authorization', '')
            token = auth[7:] if auth.startswith('Bearer ') else None
            identity = family.identity(token) if token else None
            if not identity:
                self.reply_json(401, {'error': 'Unauthorized'})
                return
            result = family.me(identity)
            self.reply_json(200, result)
            return

        # 5. Desktop Controller API: /v1/controller/members
        if path == '/v1/controller/members':
            auth = self.headers.get('Authorization', '')
            token = auth[7:] if auth.startswith('Bearer ') else None
            if token and family.identity(token) == 'controller':
                self.reply_json(200, family.dashboard())
            else:
                self.reply_json(401, {'error': 'Controller access required'})
            return

        self.reply_json(404, {'error': 'Not found'})

    def do_POST(self):
        url = urlparse(self.path)
        path = url.path
        length = int(self.headers.get('Content-Length', '0'))
        body = {}
        if length > 0:
            try:
                body = json.loads(self.rfile.read(length).decode('utf-8'))
            except Exception:
                self.reply_json(400, {'error': 'Invalid JSON body'})
                return

        # Web Dashboard locate trigger
        if path == '/api/locate':
            if body.get('pin') != DASHBOARD_PIN:
                self.reply_json(401, {'error': 'Invalid PIN'})
                return
            member_id = body.get('id')
            if not member_id:
                self.reply_json(400, {'error': 'Missing member id'})
                return
            try:
                result = family.locate(member_id)
                self.reply_json(200, result)
            except Exception as e:
                self.reply_json(400, {'error': str(e)})
            return

        # Android API: /v1/family/register
        if path == '/v1/family/register':
            try:
                result = family.register(body)
                self.reply_json(200, result)
            except Exception as e:
                self.reply_json(400, {'error': str(e)})
            return

        # Auth check for member/controller requests
        auth = self.headers.get('Authorization', '')
        token = auth[7:] if auth.startswith('Bearer ') else None
        identity = family.identity(token) if token else None
        if not identity:
            self.reply_json(401, {'error': 'Unauthorized'})
            return

        if path.startswith('/v1/controller/'):
            if identity != 'controller':
                self.reply_json(403, {'error': 'Controller access required'})
                return
            if path == '/v1/controller/locate':
                res = family.locate(str(body.get('id')))
                self.reply_json(200, res)
            elif path == '/v1/controller/invites':
                res = family.invite()
                self.reply_json(200, res)
            elif path == '/v1/controller/revoke':
                res = family.revoke(str(body.get('id')))
                self.reply_json(200, res)
            else:
                self.reply_json(404, {'error': 'Not found'})
            return

        # Member requests
        if path == '/v1/family/location':
            try:
                res = family.location(identity, body.get('location'))
                self.reply_json(200, res)
            except Exception as e:
                self.reply_json(400, {'error': str(e)})
            return

        if path == '/v1/family/sharing':
            try:
                res = family.sharing(identity, body.get('enabled'))
                self.reply_json(200, res)
            except Exception as e:
                self.reply_json(400, {'error': str(e)})
            return

        self.reply_json(404, {'error': 'Not found'})

def main():
    server = ThreadingHTTPServer(('0.0.0.0', PORT), CloudHandler)
    print(f'Notes Cloud 24/7 Server listening on port {PORT}', flush=True)
    server.serve_forever()

if __name__ == '__main__':
    main()
