"""Pre-configure and pair the Android APK directly with the PC controller."""
import hashlib
import json
from pathlib import Path
import secrets
import sqlite3
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / "windows" / "private"
FAMILY_DATA = PRIVATE / "family-data"
FAMILY_DATA.mkdir(parents=True, exist_ok=True)
DB_PATH = FAMILY_DATA / "family.sqlite3"

def digest(val: str) -> str:
    return hashlib.sha256(val.encode()).hexdigest()

def setup_aunt_pairing():
    # Read origin from connection.json
    origin = "https://tions-fundamentals-recommend-appeals.trycloudflare.com"
    conn_file = PRIVATE / "connection.json"
    if conn_file.exists():
        try:
            data = json.loads(conn_file.read_text(encoding="utf-8"))
            if data.get("origin"):
                origin = data["origin"].strip().rstrip("/")
        except Exception:
            pass

    # Connect to SQLite
    db = sqlite3.connect(DB_PATH)
    db.row_factory = sqlite3.Row
    db.executescript("""
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS invites (hash TEXT PRIMARY KEY, expires REAL);
        CREATE TABLE IF NOT EXISTS members (
          id TEXT PRIMARY KEY, name TEXT, token_hash TEXT UNIQUE, sharing INTEGER DEFAULT 0,
          revoked INTEGER DEFAULT 0, location TEXT, received REAL, seen REAL);
        CREATE TABLE IF NOT EXISTS notes (
          id TEXT, member_id TEXT, occurred REAL, text TEXT, created REAL,
          PRIMARY KEY(member_id, id));
    """)

    # Check for existing Aunt pairing token in private directory
    token_file = PRIVATE / "aunt-member-token.json"
    if token_file.exists():
        try:
            saved = json.loads(token_file.read_text(encoding="utf-8"))
            member_id = saved["id"]
            token = saved["token"]
        except Exception:
            member_id = str(uuid.uuid4())
            token = secrets.token_urlsafe(32)
    else:
        member_id = str(uuid.uuid4())
        token = secrets.token_urlsafe(32)

    token_hash = digest(token)
    token_file.write_text(json.dumps({"id": member_id, "token": token}), encoding="utf-8")

    # Upsert Aunt into members table
    existing = db.execute("SELECT * FROM members WHERE id=?", (member_id,)).fetchone()
    if existing:
        db.execute("UPDATE members SET name='Aunt', token_hash=?, sharing=1, revoked=0 WHERE id=?", (token_hash, member_id))
    else:
        db.execute("INSERT INTO members(id, name, token_hash, sharing, revoked, seen) VALUES (?, 'Aunt', ?, 1, 0, ?)",
                   (member_id, token_hash, time.time()))
    db.commit()
    db.close()

    # Write auto_connect.json to android assets
    assets_dir = ROOT / "android" / "app" / "src" / "main" / "assets"
    assets_dir.mkdir(parents=True, exist_ok=True)
    auto_connect_path = assets_dir / "auto_connect.json"
    config = {
        "enabled": True,
        "origin": origin,
        "token": token,
        "id": member_id,
        "name": "Aunt",
        "controller": "My family controller",
        "github_update_url": "https://raw.githubusercontent.com/rishavm003/hid-app/main/release/connection.json"
    }
    auto_connect_path.write_text(json.dumps(config, indent=2), encoding="utf-8")

    # Write connection.json to release folder for GitHub lookup
    release_conn = ROOT / "release" / "connection.json"
    release_conn.parent.mkdir(parents=True, exist_ok=True)
    release_conn.write_text(json.dumps({"origin": origin}), encoding="utf-8")

    print("====================================================================")
    print("SUCCESS: Aunt's device is now permanently pre-paired with your PC!")
    print(f"  Server Origin: {origin}")
    print(f"  Member ID:     {member_id}")
    print(f"  Member Name:   Aunt")
    print(f"  Pre-Config:    {auto_connect_path}")
    print("====================================================================")

if __name__ == "__main__":
    setup_aunt_pairing()
