"""PC controller. Run with Python + Tk; no third-party packages required."""
import json
import base64
import threading
import tkinter as tk
from tkinter import ttk, messagebox
from datetime import datetime, timezone
from urllib.request import Request, urlopen
from urllib.parse import urlsplit
from urllib.error import HTTPError
import webbrowser


def stamp(value):
    return datetime.fromtimestamp(value, timezone.utc).astimezone().strftime('%d %b %Y %H:%M:%S %Z') if value is not None else 'Never'


class Controller:
    def __init__(self, root, local_connection=None, public_origin=None):
        self.root, self.members, self.busy = root, {}, False
        self.local_connection = local_connection
        self.public_origin = public_origin
        root.title('Notes • Family controller')
        root.geometry('1020x740')
        root.minsize(700, 600)
        style = ttk.Style()
        style.theme_use('clam')
        style.configure('TFrame', background='#f0f4ee')
        style.configure('TLabel', background='#f0f4ee', foreground='#163d3e')
        style.configure('Treeview', rowheight=34)
        panel = ttk.Frame(root, padding=24)
        panel.pack(fill='both', expand=True)
        ttk.Label(panel, text='NOTES / FAMILY', font=('Segoe UI', 22, 'bold')).pack(anchor='w')
        ttk.Label(panel, text='Last shared locations • owner-controlled sharing • no remote activation').pack(anchor='w', pady=(4, 16))
        form = ttk.Frame(panel)
        form.pack(fill='x')
        self.origin, self.token = tk.StringVar(), tk.StringVar()
        ttk.Label(form, text='HTTPS server').grid(row=0, column=0, sticky='w')
        ttk.Entry(form, textvariable=self.origin, width=48).grid(row=0, column=1, padx=12, sticky='ew')
        ttk.Label(form, text='Controller token').grid(row=1, column=0, sticky='w')
        ttk.Entry(form, textvariable=self.token, show='•', width=48).grid(row=1, column=1, padx=12, pady=8, sticky='ew')
        form.columnconfigure(1, weight=1)
        ttk.Button(form, text='Connect / refresh', command=self.refresh).grid(row=0, column=2)
        ttk.Button(form, text='Disconnect', command=self.disconnect).grid(row=1, column=2)
        self.status = tk.StringVar(value='Not connected. No member data loaded.')
        ttk.Label(panel, textvariable=self.status, wraplength=900).pack(anchor='w', pady=12)
        buttons = ttk.Frame(panel)
        buttons.pack(fill='x', pady=(0, 12))
        ttk.Button(buttons, text='Create invitation', command=self.invite).pack(side='left')
        ttk.Button(buttons, text='Open last location in Maps', command=self.open_map).pack(side='left', padx=12)
        ttk.Button(buttons, text='Revoke selected device', command=self.revoke).pack(side='left')
        self.tree = ttk.Treeview(panel, columns=('name', 'status', 'captured', 'coordinates'), show='headings', height=7)
        for key, title, width in [('name', 'Family member', 150), ('status', 'Sharing / freshness', 140),
                                  ('captured', 'Location captured (local time)', 230), ('coordinates', 'Latitude, longitude', 220)]:
            self.tree.heading(key, text=title)
            self.tree.column(key, width=width)
        self.tree.pack(fill='both', expand=True)
        self.tree.bind('<<TreeviewSelect>>', lambda _: self.details())
        ttk.Label(panel, text='Selected member • manually shared call notes (not call history)', font=('Segoe UI', 11, 'bold')).pack(anchor='w', pady=(16, 6))
        self.detail = tk.Text(panel, height=10, wrap='word', state='disabled', font=('Segoe UI', 10))
        self.detail.pack(fill='both', expand=True)
        ttk.Label(panel, text='Stale ≠ switched off. No new sample can also mean no network, permissions, iOS suspension or a closed app.', wraplength=900).pack(anchor='w', pady=(12, 0))
        self.generation = 0
        self.root.after(60000, self.poll)
        if local_connection:
            form.pack_forget()
            self.origin.set(local_connection[0])
            self.token.set(local_connection[1])
            ttk.Label(panel, text='Connected automatically to this PC. SMS is in demo mode. Keep this window open and the PC awake.', wraplength=900).pack(anchor='w', pady=8)
            def copy_server():
                root.clipboard_clear()
                root.clipboard_append(self.public_origin)
                self.status.set('Phone server address copied. Existing members can use Reconnect after PC restart in Family.')
            ttk.Button(panel, text='Copy phone server address', command=copy_server).pack(anchor='w')
            self.root.after(200, self.refresh)

    def call(self, path, body, done):
        if self.busy:
            return
        origin, token = self.origin.get().strip().rstrip('/'), self.token.get().strip()
        url = urlsplit(origin)
        trusted_local = self.local_connection is not None and origin == self.local_connection[0] and url.hostname == '127.0.0.1'
        if (url.scheme != 'https' and not trusted_local) or not url.hostname or url.path or url.query or url.fragment or url.username:
            messagebox.showerror('Server address', 'Use an HTTPS origin such as https://family.example.com')
            return
        if len(token) < 32:
            messagebox.showerror('Token required', 'Enter your controller token.')
            return
        generation = self.generation
        self.busy = True
        self.status.set('Contacting family server…')
        def work():
            try:
                request = Request(origin+path, data=json.dumps(body).encode() if body is not None else None,
                                  headers={'Authorization': 'Bearer '+token, 'Content-Type': 'application/json'})
                with urlopen(request, timeout=20) as response:
                    result = json.load(response)
                self.root.after(0, lambda: finish(result, None))
            except Exception as exc:
                error = 'Connection failed. Displayed data is cached and may be stale.'
                if isinstance(exc, HTTPError):
                    error += f' Server returned HTTP {exc.code}.'
                self.root.after(0, lambda: finish(None, error))
        def finish(result, error):
            self.busy = False
            if generation != self.generation:
                return
            if error:
                self.status.set(error)
            else:
                done(result)
        threading.Thread(target=work, daemon=True).start()

    def refresh(self):
        self.call('/v1/controller/members', None, self.render)

    def render(self, payload):
        selected = self.tree.selection()
        self.members = {member['id']: member for member in payload['members']}
        self.tree.delete(*self.tree.get_children())
        for key, member in self.members.items():
            loc = member['location']
            coords = f"{loc['latitude']:.5f}, {loc['longitude']:.5f}" if loc else 'Not shared'
            self.tree.insert('', 'end', iid=key, values=(member['name'], member['status'].replace('_', ' '), stamp(loc['timestamp']) if loc else 'Never', coords))
        if selected and selected[0] in self.members:
            self.tree.selection_set(selected)
        self.details()
        self.status.set(f"Connected as {payload['controller_name']} • refreshed {stamp(payload['server_time'])} • updates requested every 60 seconds")

    def selected(self):
        selection = self.tree.selection()
        return self.members.get(selection[0]) if selection else None

    def details(self):
        member = self.selected()
        text = 'Select a family member to see their last report and shared notes.'
        if member:
            loc = member['location']
            text = f"{member['name']} — {member['status']}\nLast received by server: {stamp(member['received'])}\n"
            if loc:
                text += f"Location captured: {stamp(loc['timestamp'])}; accuracy approximately {loc['accuracy']:.0f} m.\n"
            text += '\nManual call notes (latest 100, retained 30 days):\n'
            text += '\n\n'.join(f"{stamp(note['occurred'])}\n{note['text']}" for note in member['notes']) or 'No notes shared.'
        self.detail.configure(state='normal')
        self.detail.delete('1.0', 'end')
        self.detail.insert('1.0', text)
        self.detail.configure(state='disabled')

    def invite(self):
        def show(payload):
            self.status.set('Invitation created. Share privately with the phone owner; expires in 10 minutes.')
            dialog = tk.Toplevel(self.root)
            dialog.title('One-use family invitation')
            ttk.Label(dialog, text='On the iPhone: Notes → Family → Paste invitation. Review the controller, join, then enable sharing.', wraplength=500).pack(padx=20, pady=12)
            invite_origin = self.public_origin or self.origin.get().strip().rstrip('/')
            setup_code = 'NOTES1.' + base64.b64encode(json.dumps({'origin': invite_origin, 'code': payload['code']}).encode()).decode()
            entry = ttk.Entry(dialog, width=55)
            entry.insert(0, setup_code)
            entry.configure(state='readonly')
            entry.pack(padx=20, pady=12)
            def copy():
                dialog.clipboard_clear()
                dialog.clipboard_append(setup_code)
                self.status.set('Invitation copied. Share privately; it expires in ten minutes.')
            ttk.Button(dialog, text='Copy invitation', command=copy).pack(pady=6)
            ttk.Label(dialog, text='Expires: '+stamp(payload['expires'])).pack(pady=12)
        self.call('/v1/controller/invites', {}, show)

    def revoke(self):
        member = self.selected()
        if member and messagebox.askyesno('Revoke device?', 'This removes access and erases shared location and manual notes. Existing SOS/check-in records remain; active check-ins still expire. Continue?'):
            self.call('/v1/controller/revoke', {'id': member['id']}, lambda _: self.refresh())

    def open_map(self):
        member = self.selected()
        if member and member['location']:
            loc = member['location']
            webbrowser.open(f"https://maps.apple.com/?ll={loc['latitude']},{loc['longitude']}")

    def disconnect(self):
        self.generation += 1
        self.token.set('')
        self.members = {}
        self.tree.delete(*self.tree.get_children())
        self.details()
        self.status.set('Disconnected. Controller token and displayed data cleared.')

    def poll(self):
        if self.token.get() and not self.busy:
            self.refresh()
        self.root.after(60000, self.poll)


if __name__ == '__main__':
    root = tk.Tk()
    Controller(root)
    root.mainloop()
