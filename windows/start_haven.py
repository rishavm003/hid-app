"""One-window Windows pilot. Demo SMS; Cloudflare temporary HTTPS tunnel."""
import ctypes
from ctypes import wintypes
import json
from pathlib import Path
import queue
import re
import secrets
import subprocess
import sys
import threading
import time
import tkinter as tk
from tkinter import ttk, messagebox
from http.server import ThreadingHTTPServer

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'server'))
sys.path.insert(0, str(ROOT / 'controller'))
from family import Family, handler
from controller import Controller


class Blob(ctypes.Structure):
    _fields_ = [('size', wintypes.DWORD), ('data', ctypes.POINTER(ctypes.c_ubyte))]


def protect(data, decrypt=False):
    buffer = ctypes.create_string_buffer(data)
    source = Blob(len(data), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_ubyte)))
    output = Blob()
    crypt = ctypes.windll.crypt32
    function = crypt.CryptUnprotectData if decrypt else crypt.CryptProtectData
    if not function(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(output)):
        raise ctypes.WinError()
    try:
        return ctypes.string_at(output.data, output.size)
    finally:
        ctypes.windll.kernel32.LocalFree(output.data)


def load_token(directory):
    path = directory / 'controller-token.dpapi'
    if path.exists():
        return protect(path.read_bytes(), decrypt=True).decode()
    token = secrets.token_urlsafe(32)
    path.write_bytes(protect(token.encode()))
    return token


def main():
    root = tk.Tk()
    root.title('Starting Notes')
    root.geometry('550x250')
    status = tk.StringVar(value='Starting your family controller…')
    panel = ttk.Frame(root, padding=24)
    panel.pack(fill='both', expand=True)
    ttk.Label(panel, text='Notes', font=('Segoe UI', 24, 'bold')).pack(anchor='w')
    ttk.Label(panel, textvariable=status, wraplength=490).pack(anchor='w', pady=16)
    ttk.Label(panel, text='Free test connection via Cloudflare. Your PC must stay awake and online. The address changes when the tunnel restarts.', wraplength=490).pack(anchor='w')
    resources, events = {}, queue.Queue()
    stop = threading.Event()

    def setup():
        try:
            binary = ROOT / 'windows' / 'tools' / 'cloudflared.exe'
            if not binary.exists():
                raise RuntimeError('First run Install connection.ps1 in the windows folder to download the official connection helper.')
            private = ROOT / 'windows' / 'private'
            private.mkdir(parents=True, exist_ok=True)
            token = load_token(private)
            family = Family(private / 'family-data', token, {'controller_name': 'Notes Cloud', 'contacts': []})
            resources['family'] = family
            internal = ThreadingHTTPServer(('127.0.0.1', 0), handler(family))
            public = ThreadingHTTPServer(('127.0.0.1', 0), handler(family, allow_controller=False))
            resources['servers'] = [internal, public]
            for server in resources['servers']:
                threading.Thread(target=server.serve_forever, daemon=True).start()
            def worker():
                while not stop.wait(1):
                    try: family.tick()
                    except Exception: events.put(('warning', 'Server worker error. Do not rely on check-in alerts.'))
            threading.Thread(target=worker, daemon=True).start()
            tunnel = subprocess.Popen([str(binary), 'tunnel', '--no-autoupdate', '--url', f'http://127.0.0.1:{public.server_port}'],
                                      stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                                      creationflags=subprocess.CREATE_NO_WINDOW)
            resources['tunnel'] = tunnel
            def reader():
                for line in tunnel.stdout:
                    match = re.search(r'https://[a-z0-9-]+\.trycloudflare\.com', line)
                    if match: events.put(('connected', (f'http://127.0.0.1:{internal.server_port}', token, match.group())))
                if not stop.is_set(): events.put(('error', 'The phone connection stopped. Restart Notes; phones will need the new server address.'))
            threading.Thread(target=reader, daemon=True).start()
            events.put(('waiting', 'Connecting your PC to a free temporary HTTPS address…'))
        except Exception as exc:
            events.put(('error', str(exc)))

    controller = None
    started = time.monotonic()
    def pump():
        nonlocal controller
        try:
            while True:
                kind, value = events.get_nowait()
                if kind == 'connected' and controller is None:
                    panel.destroy()
                    local, token, origin = value
                    controller = Controller(root, (local, token), origin)
                    root.title('Notes • Family controller • Free test mode')
                    (ROOT / 'windows' / 'private' / 'connection.json').write_text(json.dumps({'origin': origin}), encoding='utf-8')
                    (ROOT / 'release' / 'connection.json').write_text(json.dumps({'origin': origin}), encoding='utf-8')
                elif kind in ('error', 'warning'):
                    if controller: controller.status.set(value)
                    else: status.set(value)
                elif kind == 'waiting': status.set(value)
        except queue.Empty:
            pass
        if controller is None and time.monotonic()-started > 90:
            status.set('Connection is taking longer than expected. Check your internet connection and close/reopen Notes if it does not finish.')
        root.after(300, pump)

    def close():
        if controller and not messagebox.askyesno('Close Notes?', 'Closing stops the family server and incoming updates. Stored locations stay on this PC. Close?'):
            return
        stop.set()
        tunnel = resources.get('tunnel')
        if tunnel and tunnel.poll() is None: tunnel.terminate()
        # Process exit closes sockets/SQLite; committed records remain on disk.
        root.destroy()

    root.protocol('WM_DELETE_WINDOW', close)
    threading.Thread(target=setup, daemon=True).start()
    root.after(300, pump)
    root.mainloop()


if __name__ == '__main__':
    main()
