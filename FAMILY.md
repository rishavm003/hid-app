# Haven family controller

**Updated installation route:** see [WINDOWS-IPHONE.md](WINDOWS-IPHONE.md). The latest iOS source adds optional Always permission, significant-change recovery and scheduled best-effort refresh; the original foreground-only recovery description below is superseded by that guide. The one-window Windows launcher automatically starts a demo server and temporary HTTPS tunnel. A GitHub Actions compile succeeded in October 2026; physical-device behavior remains unverified.

This update adds a **PC controller** and a Family tab in the iOS app. It is a development pilot: background behavior and real SMS delivery still need validation on physical iPhones. The controller is a native Python/Tk desktop app, not a hosted website or phone controller app.

## What it does

- The controller creates a private, one-use invitation valid for ten minutes.
- Each phone owner previews the controller name, accepts joining, and separately enables location sharing. Joining alone sends no coordinates.
- Each phone receives its own token. Members cannot view other members, and controller credentials cannot activate sharing or impersonate a member.
- The app starts visible Core Location background updates from the foreground and uploads at most once per hour after a successful upload. First successful sharing can happen immediately. Failed uploads are retried on fresh location callbacks, no more often than once per minute.
- The server stores the **latest successful location only**, including latitude, longitude, accuracy, capture time and server receipt time. No route history is collected in the family location table.
- The controller refreshes every minute and shows `recent`, `stale` (older than 90 minutes), `no sample`, `paused` or `revoked`. A stale sample is never labelled “phone switched off”: the reason is unknown.
- Stopping sharing halts collection locally immediately. The server's pause flag is updated when reachable. Last shared data stays visible until the owner erases it or the controller revokes the device.
- Manual call notes are shared individually after confirmation, with a call time chosen by the owner. They are retained for 30 days and can be erased earlier. These are **not automatically collected call records or audio recordings**.
- SOS/check-in records are isolated per family member and keep the existing server-backed timers and optional Twilio integration.

## iOS limitations

There is no supported public API used by this app to read the Phone app's complete cellular call history. CallKit does not provide a general call-log export. Haven therefore does not offer automatic daily call-history collection. The manual note form is an explicit alternative, not an equivalent substitute.

iOS does not guarantee hourly background execution. Core Location may pause when stationary, and callbacks depend on permission, operating-system decisions, battery and device state. This implementation uses a visible background location session with When In Use permission; it does not ask for hidden persistence or automatic relaunch. Force quit, reboot, permission changes and phone shutdown can interrupt updates. Reopen Haven in the foreground to reconcile and resume an opted-in session. The app cannot upload from a powered-off phone. The server retains the previous sample.

Location services can consume battery even though uploads are throttled hourly. `pausesLocationUpdatesAutomatically` is enabled to reduce battery use, which can produce longer gaps. A guaranteed hourly feed is **not** delivered by this prototype. The Family tab explains this before consent.

References: [Apple background location](https://developer.apple.com/documentation/corelocation/handling-location-updates-in-the-background), [Apple background scheduling limits](https://developer.apple.com/documentation/backgroundtasks/bgtaskrequest/earliestbegindate), [CallKit](https://developer.apple.com/documentation/callkit).

## Start the family server

Use this entry point **instead of** the legacy single-person `server.py` process on the same port. Old single-person records are not migrated. Complete old active check-ins before switching accounts, or keep the old server running separately until they are resolved.

```powershell
cd server
Copy-Item config.example.json config.json
# Edit config.json: controller_name and consenting SMS contacts.
$env:HAVEN_CONTROLLER_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"
$env:HAVEN_MODE = 'demo'
python family.py
```

If you already have `config.json`, add `controller_name` without overwriting your contacts. Securely record the generated controller token; use the same token after a server restart. The server stores invitation and member-token hashes, not their raw secrets. The controller token is supplied via environment variable and hashed in memory. Rotate it by changing the environment variable and restarting; revoke a member in the controller to disable their token.

Configure HTTPS on a trusted server with a valid certificate and proxy to `127.0.0.1:8080`. Both clients require HTTPS. Do not expose the development HTTP server directly to the internet. Persist `server/family-data/`, which contains the family database and a separate SOS database for each member. Run only one family server process against this directory. Set `HAVEN_FAMILY_DATA` to change its location.

Keep demo mode for initial tests. Existing `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM` and `HAVEN_MODE=live` settings enable SMS for member SOS/check-ins, as described in the main README. **Demo only disables SMS; family locations and manually shared notes still upload to your server.** Use fabricated test coordinates and consenting test users during development. Each member sees the configured SMS recipients in Circle before enabling alert consent.

## Open the PC controller

On a machine with Python 3.11+ and Tkinter (included in standard Windows Python installs):

```powershell
python controller/controller.py
```

Enter the HTTPS server origin and controller token. Click **Connect / refresh**, then **Create invitation**. Give the code privately to the phone owner. The controller token remains in memory only; **Disconnect** clears its field and loaded member data. The map button opens Apple Maps for the selected coordinates, sharing those coordinates with that map service through the browser.

The controller can view latest locations and manually shared notes, and revoke devices. It cannot remotely enable collection, fetch the phone's call database, or determine why a sample is stale. A refresh failure leaves the last displayed data labelled as cached. Member times are shown in the PC's local timezone.

## Set up each iPhone

1. Regenerate the project (`cd ios && xcodegen generate`), choose your signing team in Xcode, and build/install on the owner's phone. The project now declares the location background mode.
2. Open **Family**, enter the server origin, invitation and your own display name. Review the controller identity and accept joining.
3. Choose **Enable hourly location sharing**, read the confirmation, and allow location if you wish. Background usage is visibly indicated by iOS.
4. For SOS, tap **Connect Home and Circle to my family account**, review the contacts in Circle, and enable alert consent in Setup. This requires resolving any pending SOS request or existing active timer first.
5. Optionally type a call note and explicitly share it. Stop collection anytime with **Stop location sharing**. Use **Erase shared family location and notes** to remove previously shared family data as well.

The family credential is stored in the device-only Keychain with access after the first unlock, allowing approved uploads while the device is locked. Family background sharing and Home's one-shot location toggle are independent. Turning off the Home toggle does not stop Family sharing; use Family's stop control or revoke iOS location permission.

## Data and operational boundaries

Family erase and controller revoke remove the family location and manual notes, not already sent SMS or separate safety alert/timer records. Revoking a device does not cancel its registered check-in: the timer can still alert contacts on expiry. Finish active check-ins before revocation when appropriate. Screenshots or copies already made by recipients cannot be recalled.

The latest family location is retained until erased/revoked; notes expire after 30 days while the server is running. SQLite and backups need restricted access and encrypted storage. No remote or covert recording is implemented. Public deployment still requires rate limiting, robust login/recovery, transport and host hardening, service monitoring, backup/deletion procedures and a security review. An invitation response lost during joining may consume the invitation; issue a new one and revoke the unused entry. No location is shared by that unused entry.

## Verification

```powershell
python -m unittest discover -s server -v
```

Backend tests cover controller/member authorization, cross-member isolation, invitation expiry and single use, consent gates, last-location persistence across restart, stale status, out-of-order samples, note expiry, erase and revocation, plus existing SOS/check-in behavior. No live messages are sent by tests. Physical iPhone tests must still cover locked/foreground/force-quit states, stationary behavior, denied permission, airplane mode, shutdown, resume, battery usage and offline pause/erase.
