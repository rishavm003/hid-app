"""Download and extract the compiled Android APK from GitHub Actions."""
import json
import os
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

TOKEN = os.environ.get("GITHUB_TOKEN", "")
REPO = os.environ.get("GITHUB_REPOSITORY", "rishavm003/hid-app")
ROOT = Path(__file__).resolve().parents[1]
RELEASE = ROOT / "release"
RELEASE.mkdir(parents=True, exist_ok=True)

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

def get_latest_android_run():
    req = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/actions/workflows/android-build.yml/runs?per_page=1",
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "User-Agent": "python",
            "Accept": "application/vnd.github+json"
        }
    )
    with opener.open(req) as resp:
        data = json.loads(resp.read().decode())
        runs = data.get("workflow_runs", [])
        if not runs:
            # Fallback to general runs
            req2 = urllib.request.Request(
                f"https://api.github.com/repos/{REPO}/actions/runs?per_page=5",
                headers={
                    "Authorization": f"Bearer {TOKEN}",
                    "User-Agent": "python",
                    "Accept": "application/vnd.github+json"
                }
            )
            with opener.open(req2) as resp2:
                data2 = json.loads(resp2.read().decode())
                runs2 = [r for r in data2.get("workflow_runs", []) if "android" in r.get("name", "").lower() or "android" in r.get("path", "").lower()]
                if runs2:
                    return runs2[0]["id"]
            raise RuntimeError("No Android workflow runs found yet on GitHub Actions.")
        return runs[0]["id"]

def check_status(run_id):
    req = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/actions/runs/{run_id}",
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "User-Agent": "python",
            "Accept": "application/vnd.github+json"
        }
    )
    with opener.open(req) as resp:
        data = json.loads(resp.read().decode())
        return data["status"], data.get("conclusion")

def download_artifact(run_id):
    req = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/actions/runs/{run_id}/artifacts",
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "User-Agent": "python",
            "Accept": "application/vnd.github+json"
        }
    )
    with opener.open(req) as resp:
        artifacts = json.loads(resp.read().decode())["artifacts"]
        if not artifacts:
            raise RuntimeError(f"No artifacts found in run {run_id}")
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
    print(f"Downloaded artifact ZIP to {zip_path}")

    with zipfile.ZipFile(zip_path) as z:
        z.extractall(RELEASE)
        for name in z.namelist():
            print(f"Extracted: {RELEASE / name}")
            if name.endswith(".apk"):
                dest = RELEASE / "Notes.apk"
                if (RELEASE / name) != dest:
                    import shutil
                    shutil.copy2(RELEASE / name, dest)
                print(f"SUCCESS! Ready-to-install Android APK is at: {dest}")

if __name__ == "__main__":
    run_id = sys.argv[1] if len(sys.argv) > 1 else None
    if not run_id:
        print("Finding latest Android workflow run...")
        try:
            run_id = get_latest_android_run()
            print(f"Found Run ID: {run_id}")
        except Exception as e:
            print(f"Error: {e}")
            print("Tip: Run the workflow on GitHub, then provide RUN_ID: python scripts/download_android_artifact.py <RUN_ID>")
            sys.exit(1)

    print(f"Monitoring build run {run_id}...")
    while True:
        status, conclusion = check_status(run_id)
        print(f"Run {run_id} -> Status: {status}, Conclusion: {conclusion}")
        if status == "completed":
            if conclusion == "success":
                print("Build succeeded! Downloading APK artifact...")
                download_artifact(run_id)
                break
            else:
                raise RuntimeError(f"Build failed with conclusion: {conclusion}")
        time.sleep(15)
