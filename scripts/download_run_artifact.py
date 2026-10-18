import json
import time
import urllib.request
import zipfile
import shutil
from pathlib import Path

import os

TOKEN = os.environ.get("GITHUB_TOKEN", "")
REPO = os.environ.get("GITHUB_REPOSITORY", "rishavm003/hid-app")
RUN_ID = 39142769132
ROOT = Path(__file__).resolve().parents[1]
RELEASE = ROOT / "release"

class NoAuthRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        m = req.get_method()
        if (code in (301, 302, 303, 307) and m in ("GET", "HEAD")
                or code in (301, 302, 303) and m == "POST"):
            newheaders = {k: v for k, v in req.headers.items()
                          if k.lower() not in ("content-length", "content-type", "authorization")}
            return urllib.request.Request(newurl, headers=newheaders,
                                          origin_req_host=req.origin_req_host,
                                          unverifiable=True)
        return super().redirect_request(req, fp, code, msg, headers, newurl)

opener = urllib.request.build_opener(NoAuthRedirectHandler())

def check_status():
    req = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/actions/runs/{RUN_ID}",
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "User-Agent": "python",
            "Accept": "application/vnd.github+json"
        }
    )
    with opener.open(req) as resp:
        data = json.loads(resp.read().decode())
        return data["status"], data["conclusion"]

def download_artifact():
    req = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/actions/runs/{RUN_ID}/artifacts",
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "User-Agent": "python",
            "Accept": "application/vnd.github+json"
        }
    )
    with opener.open(req) as resp:
        artifacts = json.loads(resp.read().decode())["artifacts"]
        if not artifacts:
            raise RuntimeError("No artifacts found")
        artifact = artifacts[0]
        download_url = artifact["archive_download_url"]
        print(f"Downloading {artifact['name']} ({artifact['size_in_bytes']} bytes)...")

    req = urllib.request.Request(
        download_url,
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "User-Agent": "python",
            "Accept": "application/vnd.github+json"
        }
    )
    zip_path = RELEASE / f"{artifact['name']}.zip"
    with opener.open(req) as resp, open(zip_path, "wb") as f:
        f.write(resp.read())
    print(f"Downloaded to {zip_path}")
    
    with zipfile.ZipFile(zip_path) as z:
        z.extractall(RELEASE)
        for name in z.namelist():
            print(f"Extracted {name} to {RELEASE / name}")
            if name.endswith(".ipa"):
                ipa_file = RELEASE / name
                notes_ipa = RELEASE / "Notes-unsigned.ipa"
                if ipa_file != notes_ipa:
                    shutil.copy2(ipa_file, notes_ipa)
                    print(f"Copied to {notes_ipa}")

print(f"Monitoring build {RUN_ID}...")
while True:
    status, conclusion = check_status()
    print(f"Status: {status}, conclusion: {conclusion}")
    if status == "completed":
        if conclusion == "success":
            print("Build succeeded! Downloading artifact...")
            download_artifact()
            break
        else:
            raise RuntimeError(f"Build failed with conclusion: {conclusion}")
    time.sleep(15)
