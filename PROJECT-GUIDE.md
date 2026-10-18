# Haven project guide

Updated 18 October 2026. This folder contains the current iPhone app, Windows PC controller, Python server, tests, and GitHub build workflow. It is a **development pilot**, not an emergency service.

## What is included

| Path | Purpose |
| --- | --- |
| `ios/project.yml`, `ios/Haven/` | XcodeGen specification and SwiftUI iPhone source |
| `.github/workflows/ios-build.yml` | GitHub Actions unsigned iPhone IPA build |
| `windows/Start Haven.vbs`, `windows/start_haven.py` | One-window Windows controller, local family server and temporary HTTPS tunnel |
| `windows/Install connection.ps1` | Downloads the official Cloudflare tunnel helper and checks its published SHA-256 digest |
| `controller/controller.py` | PC family dashboard and invitation creator |
| `server/family.py`, `server/server.py` | Family API, latest location, manual notes, SOS and check-in logic |
| `server/test_*.py` | Backend tests; no real SMS is sent |
| `README.md`, `FAMILY.md`, `WINDOWS-IPHONE.md` | Detailed design and operating instructions |
| `ANDROID-FEASIBILITY.md` | Android requirements and possible future design; **no Android app exists yet** |

## What the current app does

Each phone owner accepts a one-use invitation, then separately enables family location sharing. The PC shows the **latest received** coordinates, accuracy and timestamps, marks a sample stale after 90 minutes, opens that coordinate in Apple Maps, and retains the last sample if the phone goes offline. The owner can send a fresh sample, pause sharing, or erase family location and manually entered notes. The controller can revoke a phone. The server stores one family location sample per member, not a route history.

The iPhone app also has SOS and timed check-ins. The free Windows launcher starts in **demo SMS mode** with no SMS contacts, so those alerts do not contact anyone. The Phone app's call history is not collected; family members may explicitly share a typed call note. Hiding the app icon depends on iOS support for that specific sideloaded installation and must be checked on a device.

## Restore on a Windows PC

1. Keep this folder in a private location. Install Python 3.11 or newer with Tkinter.
2. From this folder, run `windows/Install connection.ps1` in PowerShell once. It downloads `cloudflared.exe` to `windows/tools/` after digest verification. If PowerShell blocks the script, use a temporary process-scoped execution-policy override after reviewing the script; do not disable protection system-wide.
3. Double-click `windows/Start Haven.vbs`. The PC controller and family server open together. Keep that window, PC and internet connection running during tests.
4. In the controller, click **Create invitation**, copy the code, and give it privately to the consenting iPhone owner. An invitation expires after ten minutes.

The controller token and family database are generated locally under `windows/private/` on first launch. They are intentionally **not** in this export. Restoring the code on another PC creates a new family account and does not restore previous locations or phone connections. Back up private data separately only if you need continuity, and protect that backup.

The free Cloudflare address may change after the launcher restarts. On an already connected iPhone, stop sharing, copy the new phone server address from the controller, use **Reconnect** in Haven's Family tab, then enable and test sharing again. A different PC's fresh account requires a new invitation instead.

## Build the iPhone app without a Mac

1. Put the **contents** of this folder at the root of your GitHub repository's `main` branch. At minimum, confirm these root paths: `.github/workflows/ios-build.yml`, `ios/project.yml`, and the five files in `ios/Haven/`. Do not upload this folder as an enclosing directory or upload a ZIP as the repository source.
2. In GitHub **Actions**, select **Build iPhone test package** from `ios-build.yml` and click **Run workflow**. A successful build for this source was shown in October 2026; rebuild after any source change.
3. Download the run's `Haven-iPhone-test-package` artifact and extract `Haven-unsigned.ipa` from it. The artifact expires according to the workflow retention period, so rebuild when needed.
4. Follow the [AltStore Classic Windows guide](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows) to install AltStore on the owner's iPhone. The owner uses their own Apple ID. Import the IPA in AltStore, trust the developer profile and enable Developer Mode if iOS requests them.
5. Free signing expires after seven days. Refresh through AltStore and AltServer before expiry, then reopen Haven and verify a current location. [AltStore explains refresh and limits](https://faq.altstore.io/altstore-classic/your-altstore).

Do not put Apple credentials, controller tokens, SMS credentials, or location databases in GitHub. The successful GitHub build only proved compilation; physical-device installation and background behavior remain unverified.

## Connect an iPhone

Open Haven → **Family** → paste the invitation → review the controller → **Accept and join**. Then enable location sharing, grant location access, and choose **Always** in iOS Settings if available and desired. Tap **Share my location now** and confirm a new timestamp and sensible coordinates appear in the PC controller. Test locked, stationary, moving and offline cases with the owner. Sharing aims for about one upload per hour, but iOS can delay or stop callbacks; an exact hourly feed is not guaranteed.

Haven's **Home/Circle** SOS connection is separate from Family sharing. The owner can connect it in Family, review recipients in Circle, and enable alert consent in Setup. In the free launcher SMS stays in demo mode, so do not treat an SOS button press as a message to others or an emergency-services call.

## Run local backend tests

From this folder in PowerShell:

```powershell
python -m unittest discover -s server -v
```

The tests use temporary databases and a fake message sender. They do not validate the iPhone background schedule, signing, AltStore installation, tunnel reliability, real SMS delivery or battery use.

## Limits before relying on it

- The PC must be awake, online and running the controller; closing it stops new uploads and timers until restarted.
- A powered-off phone, force quit, expired free signing, removed permission, lost network or iOS scheduling can interrupt sharing. A stale location does not explain the cause.
- The test tunnel is temporary, and the server is not hardened or monitored for public production use.
- iOS does not provide general call-history access to this app, and hiding the icon does not prevent discovery in Settings or prevent uninstalling.
- Practice with consenting participants, and use built-in emergency calling for real emergencies.

For deeper implementation and API details, read `README.md` and `FAMILY.md`. For step-by-step Windows signing, read `WINDOWS-IPHONE.md`.
